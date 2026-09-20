package br.com.craftonica.sensor;

import br.com.craftonica.block.BlockCalibrationTarget;
import br.com.craftonica.tile.TileEntityCalibrationTarget;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Loaded-world-only acoustic cone query. It never requests or generates a chunk. */
public final class UltrasonicRaycaster {
    private static final double MAX_BLOCKS = UltrasonicMeasurementModel.MAX_CM / 100.0;
    private static final double CHUNK_CLIP_EPSILON = 1.0e-7;
    private static final double HIT_EPSILON = 1.0e-8;
    private static final int MAX_ENTITY_CANDIDATES = 128;

    private UltrasonicRaycaster() { }

    /** Fixed-block adapter that preserves the adjacent sub-block calibration target. */
    public static Hit trace(World world, int x, int y, int z, int front) {
        if (world == null || front < 2 || front > 5) return null;
        int dx = dx(front), dz = dz(front);
        int adjacentX = x + dx, adjacentZ = z + dz;
        if (chunkLoaded(world, adjacentX >> 4, adjacentZ >> 4)) {
            Block adjacent = world.getBlock(adjacentX, y, adjacentZ);
            TileEntity tile = world.getTileEntity(adjacentX, y, adjacentZ);
            if (adjacent instanceof BlockCalibrationTarget && tile instanceof TileEntityCalibrationTarget) {
                TileEntityCalibrationTarget target = (TileEntityCalibrationTarget) tile;
                return new Hit(target.getDistanceCentimeters(), target.getAngleDegrees(), 1.0,
                        ((BlockCalibrationTarget) adjacent).getProfile(), "calibration");
            }
        }

        UltrasonicSensorPose pose = new UltrasonicSensorPose(
                x + 0.5 + dx * 0.501, y + 0.5, z + 0.5 + dz * 0.501,
                yawForSide(front), 0.0);
        return trace(world, pose, null);
    }

    public static Hit trace(World world, UltrasonicSensorPose pose) {
        return trace(world, pose, null);
    }

    /** The excluded entity is normally the future mobile robot that owns this sensor. */
    public static Hit trace(World world, UltrasonicSensorPose pose, Entity excludedEntity) {
        if (world == null || pose == null || world.isRemote) return null;
        UltrasonicBeamModel.Ray[] rays = UltrasonicBeamModel.rays(pose);
        Vec3 start = vector(pose.x, pose.y, pose.z);
        List<Entity> entities = entitiesInConeBounds(world, start, rays, excludedEntity);
        List<UltrasonicBeamModel.RayReturn> returns =
                new ArrayList<UltrasonicBeamModel.RayReturn>(rays.length);

        for (UltrasonicBeamModel.Ray ray : rays) {
            Vec3 requestedEnd = vector(pose.x + ray.direction.x * MAX_BLOCKS,
                    pose.y + ray.direction.y * MAX_BLOCKS,
                    pose.z + ray.direction.z * MAX_BLOCKS);
            Vec3 loadedEnd = clipToLoadedChunks(world, start, requestedEnd);
            if (loadedEnd == null || distanceSquared(start, loadedEnd) < 1.0e-12) continue;

            RayHit blockHit = traceBlock(world, start, loadedEnd, ray.direction);
            RayHit entityHit = traceEntities(start, loadedEnd, ray.direction, entities, excludedEntity);
            RayHit selected = first(blockHit, entityHit);
            if (selected != null) {
                returns.add(new UltrasonicBeamModel.RayReturn(ray.index, selected.targetKey,
                        selected.centimeters, selected.incidenceDegrees, selected.material, selected.mode));
            }
        }

        UltrasonicBeamModel.Echo echo = UltrasonicBeamModel.resolve(returns);
        return echo == null ? null : new Hit(echo.centimeters, echo.incidenceDegrees,
                echo.apparentCoverage, echo.material, echo.mode);
    }

    private static RayHit traceBlock(World world, Vec3 start, Vec3 end,
                                     UltrasonicSensorPose.Vector direction) {
        MovingObjectPosition hit = world.rayTraceBlocks(start, end, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !chunkLoaded(world, hit.blockX >> 4, hit.blockZ >> 4)) return null;
        double centimeters = distance(start, hit.hitVec) * 100.0;
        Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        AcousticMaterialProfile material = AcousticMaterialProfile.forBlock(block);
        return new RayHit(surfaceKey(hit, material), centimeters,
                incidence(direction, hit.sideHit), material, "world");
    }

    private static RayHit traceEntities(Vec3 start, Vec3 end, UltrasonicSensorPose.Vector direction,
                                        List<Entity> entities, Entity excludedEntity) {
        RayHit best = null;
        int bestEntityId = Integer.MAX_VALUE;
        for (Entity entity : entities) {
            if (entity == null || entity == excludedEntity || entity.isDead || entity.boundingBox == null
                    || !entity.canBeCollidedWith()) continue;
            MovingObjectPosition intersection = entity.boundingBox.calculateIntercept(start, end);
            if (intersection == null || intersection.hitVec == null) continue;
            double centimeters = distance(start, intersection.hitVec) * 100.0;
            int entityId = entity.getEntityId();
            if (best == null || centimeters < best.centimeters - HIT_EPSILON
                    || (StrictMath.abs(centimeters - best.centimeters) <= HIT_EPSILON
                    && entityId < bestEntityId)) {
                AcousticMaterialProfile material = AcousticMaterialProfile.forEntity(entity);
                best = new RayHit("entity:" + entityId, centimeters,
                        incidence(direction, intersection.sideHit), material, "entity");
                bestEntityId = entityId;
            }
        }
        return best;
    }

    private static List<Entity> entitiesInConeBounds(World world, Vec3 start,
                                                      UltrasonicBeamModel.Ray[] rays,
                                                      Entity excludedEntity) {
        double minX = start.xCoord, minY = start.yCoord, minZ = start.zCoord;
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (UltrasonicBeamModel.Ray ray : rays) {
            double x = start.xCoord + ray.direction.x * MAX_BLOCKS;
            double y = start.yCoord + ray.direction.y * MAX_BLOCKS;
            double z = start.zCoord + ray.direction.z * MAX_BLOCKS;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
                .expand(0.001, 0.001, 0.001);
        List<?> found = world.getEntitiesWithinAABBExcludingEntity(excludedEntity, bounds);
        List<Entity> candidates = new ArrayList<Entity>(Math.min(found.size(), MAX_ENTITY_CANDIDATES));
        for (Object value : found) {
            if (value instanceof Entity) {
                Entity entity = (Entity) value;
                if (entity != excludedEntity && !entity.isDead && entity.boundingBox != null
                        && entity.canBeCollidedWith()) candidates.add(entity);
            }
        }
        Collections.sort(candidates, new EntityDistanceComparator(start));
        if (candidates.size() > MAX_ENTITY_CANDIDATES) {
            return new ArrayList<Entity>(candidates.subList(0, MAX_ENTITY_CANDIDATES));
        }
        return candidates;
    }

    /** Clips before the first unloaded X/Z chunk using a two-dimensional voxel traversal. */
    private static Vec3 clipToLoadedChunks(World world, Vec3 start, Vec3 end) {
        double deltaX = end.xCoord - start.xCoord;
        double deltaY = end.yCoord - start.yCoord;
        double deltaZ = end.zCoord - start.zCoord;
        int chunkX = MathHelper.floor_double(start.xCoord) >> 4;
        int chunkZ = MathHelper.floor_double(start.zCoord) >> 4;
        if (!chunkLoaded(world, chunkX, chunkZ)) return null;

        int stepX = deltaX > 0.0 ? 1 : deltaX < 0.0 ? -1 : 0;
        int stepZ = deltaZ > 0.0 ? 1 : deltaZ < 0.0 ? -1 : 0;
        double tMaxX = firstBoundaryT(start.xCoord, deltaX, chunkX, stepX);
        double tMaxZ = firstBoundaryT(start.zCoord, deltaZ, chunkZ, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 16.0 / StrictMath.abs(deltaX);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16.0 / StrictMath.abs(deltaZ);

        while (Math.min(tMaxX, tMaxZ) <= 1.0) {
            double crossing = Math.min(tMaxX, tMaxZ);
            if (tMaxX <= crossing + 1.0e-12) { chunkX += stepX; tMaxX += tDeltaX; }
            if (tMaxZ <= crossing + 1.0e-12) { chunkZ += stepZ; tMaxZ += tDeltaZ; }
            if (!chunkLoaded(world, chunkX, chunkZ)) {
                double safeT = Math.max(0.0, crossing - CHUNK_CLIP_EPSILON);
                return vector(start.xCoord + deltaX * safeT, start.yCoord + deltaY * safeT,
                        start.zCoord + deltaZ * safeT);
            }
        }
        return end;
    }

    private static double firstBoundaryT(double coordinate, double delta, int chunk, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? (chunk + 1) * 16.0 : chunk * 16.0;
        return (boundary - coordinate) / delta;
    }

    private static RayHit first(RayHit block, RayHit entity) {
        if (block == null) return entity;
        if (entity == null) return block;
        return entity.centimeters < block.centimeters - HIT_EPSILON ? entity : block;
    }

    private static double incidence(UltrasonicSensorPose.Vector direction, int side) {
        double normalX = side == 4 ? -1.0 : side == 5 ? 1.0 : 0.0;
        double normalY = side == 0 ? -1.0 : side == 1 ? 1.0 : 0.0;
        double normalZ = side == 2 ? -1.0 : side == 3 ? 1.0 : 0.0;
        double cosine = StrictMath.abs(direction.x * normalX + direction.y * normalY
                + direction.z * normalZ);
        return StrictMath.toDegrees(StrictMath.acos(clamp(cosine, 0.0, 1.0)));
    }

    private static String surfaceKey(MovingObjectPosition hit, AcousticMaterialProfile material) {
        int plane = hit.sideHit == 0 ? hit.blockY : hit.sideHit == 1 ? hit.blockY + 1
                : hit.sideHit == 2 ? hit.blockZ : hit.sideHit == 3 ? hit.blockZ + 1
                : hit.sideHit == 4 ? hit.blockX : hit.blockX + 1;
        return "surface:" + material.getId() + ':' + hit.sideHit + ':' + plane;
    }

    private static boolean chunkLoaded(World world, int chunkX, int chunkZ) {
        return world.getChunkProvider().chunkExists(chunkX, chunkZ);
    }

    private static Vec3 vector(double x, double y, double z) {
        return Vec3.createVectorHelper(x, y, z);
    }

    private static double distance(Vec3 first, Vec3 second) {
        return StrictMath.sqrt(distanceSquared(first, second));
    }

    private static double distanceSquared(Vec3 first, Vec3 second) {
        double x = second.xCoord - first.xCoord;
        double y = second.yCoord - first.yCoord;
        double z = second.zCoord - first.zCoord;
        return x * x + y * y + z * z;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double yawForSide(int side) {
        return side == 2 ? -180.0 : side == 3 ? 0.0 : side == 4 ? 90.0 : -90.0;
    }

    private static int dx(int side) { return side == 4 ? -1 : side == 5 ? 1 : 0; }
    private static int dz(int side) { return side == 2 ? -1 : side == 3 ? 1 : 0; }

    private static final class RayHit {
        final String targetKey;
        final double centimeters;
        final double incidenceDegrees;
        final AcousticMaterialProfile material;
        final String mode;

        RayHit(String targetKey, double centimeters, double incidenceDegrees,
               AcousticMaterialProfile material, String mode) {
            this.targetKey = targetKey;
            this.centimeters = centimeters;
            this.incidenceDegrees = incidenceDegrees;
            this.material = material;
            this.mode = mode;
        }
    }

    private static final class EntityDistanceComparator implements Comparator<Entity> {
        private final Vec3 origin;

        EntityDistanceComparator(Vec3 origin) { this.origin = origin; }

        @Override public int compare(Entity first, Entity second) {
            int distance = Double.compare(distanceToBoxSquared(origin, first.boundingBox),
                    distanceToBoxSquared(origin, second.boundingBox));
            return distance != 0 ? distance : first.getEntityId() - second.getEntityId();
        }

        private static double distanceToBoxSquared(Vec3 point, AxisAlignedBB box) {
            double x = point.xCoord < box.minX ? box.minX - point.xCoord
                    : point.xCoord > box.maxX ? point.xCoord - box.maxX : 0.0;
            double y = point.yCoord < box.minY ? box.minY - point.yCoord
                    : point.yCoord > box.maxY ? point.yCoord - box.maxY : 0.0;
            double z = point.zCoord < box.minZ ? box.minZ - point.zCoord
                    : point.zCoord > box.maxZ ? point.zCoord - box.maxZ : 0.0;
            return x * x + y * y + z * z;
        }
    }

    public static final class Hit {
        public final double centimeters;
        public final double incidenceDegrees;
        public final double apparentCoverage;
        public final AcousticMaterialProfile material;
        public final String mode;

        Hit(double centimeters, double incidenceDegrees, double apparentCoverage,
            AcousticMaterialProfile material, String mode) {
            this.centimeters = centimeters;
            this.incidenceDegrees = incidenceDegrees;
            this.apparentCoverage = apparentCoverage;
            this.material = material;
            this.mode = mode;
        }
    }
}
