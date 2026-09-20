package br.com.craftonica.showcase;

import br.com.craftonica.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import java.util.List;

/** Idempotent, loaded-chunk-only builder for the bounded mobile robot arena. */
public final class RobotArenaGenerator {
    public static final int WALL_HEIGHT = RobotArenaBlueprint.WALL_HEIGHT;
    public static final int CLEAR_HEIGHT = RobotArenaBlueprint.CLEAR_HEIGHT;

    private RobotArenaGenerator() { }

    public static Result generate(WorldServer world, EntityPlayerMP player, int ox, int oy, int oz) {
        if (world == null || player == null || player.worldObj != world) throw new IllegalArgumentException("world/player");
        if (oy < 2 || oy + CLEAR_HEIGHT >= world.getHeight()) return Result.failure("HEIGHT_OUT_OF_RANGE");
        if (!allChunksLoaded(world, ox, oz)) return Result.failure("UNLOADED_CHUNK");
        if (!entitiesCompatible(world, player, ox, oy, oz)) return Result.failure("ENTITY_IN_FUTURE_WALL");

        RobotArenaBlueprint blueprint = new RobotArenaBlueprint();
        RobotArenaLayout layout = blueprint.layout();
        for (int x = 0; x < RobotArenaLayout.WIDTH; x++) for (int z = 0; z < RobotArenaLayout.DEPTH; z++)
            for (int y = -1; y <= CLEAR_HEIGHT; y++) {
                RobotArenaBlueprint.Voxel voxel = blueprint.voxel(x, y, z);
                set(world, ox + x, oy + y, oz + z, block(voxel), metadata(voxel));
            }
        int mdf = 0, plastic = 0, absorbent = 0;
        for (int lx = 0; lx < RobotArenaLayout.LOGICAL_SIZE; lx++)
            for (int lz = 0; lz < RobotArenaLayout.LOGICAL_SIZE; lz++) {
                RobotArenaLayout.Cell cell = layout.cell(lx, lz);
                if (cell == RobotArenaLayout.Cell.WALL) {
                    RobotArenaLayout.WallMaterial material = layout.wallMaterial(lx, lz);
                    if (material == RobotArenaLayout.WallMaterial.MDF) mdf++;
                    else if (material == RobotArenaLayout.WallMaterial.RIGID_PLASTIC) plastic++;
                    else if (material == RobotArenaLayout.WallMaterial.ABSORBENT) absorbent++;
                }
            }

        double startX = ox + layout.startBlockX() + 0.5D;
        double startZ = oz + layout.startBlockZ() + 0.5D;
        player.setPositionAndUpdate(startX, oy, startZ);
        player.rotationYaw = 0.0F; player.rotationPitch = 0.0F;
        return Result.success(ox, oy, oz, mdf, plastic, absorbent);
    }

    static boolean allChunksLoaded(WorldServer world, int ox, int oz) {
        int minChunkX = ox >> 4, maxChunkX = (ox + RobotArenaLayout.WIDTH - 1) >> 4;
        int minChunkZ = oz >> 4, maxChunkZ = (oz + RobotArenaLayout.DEPTH - 1) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            if (!world.getChunkProvider().chunkExists(cx, cz)) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean entitiesCompatible(WorldServer world, EntityPlayerMP player, int ox, int oy, int oz) {
        AxisAlignedBB volume = AxisAlignedBB.getBoundingBox(ox, oy, oz,
                ox + RobotArenaLayout.WIDTH, oy + CLEAR_HEIGHT + 1, oz + RobotArenaLayout.DEPTH);
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(player, volume);
        RobotArenaLayout layout = new RobotArenaLayout();
        for (Entity entity : entities) {
            int minX = (int) StrictMath.floor(entity.boundingBox.minX - ox);
            int maxX = (int) StrictMath.floor(entity.boundingBox.maxX - ox);
            int minZ = (int) StrictMath.floor(entity.boundingBox.minZ - oz);
            int maxZ = (int) StrictMath.floor(entity.boundingBox.maxZ - oz);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                RobotArenaLayout.Cell cell = x < 0 || z < 0 || x >= RobotArenaLayout.WIDTH
                        || z >= RobotArenaLayout.DEPTH ? RobotArenaLayout.Cell.WALL
                        : layout.cell(x / RobotArenaLayout.SCALE, z / RobotArenaLayout.SCALE);
                if (x < 0 || z < 0 || x >= RobotArenaLayout.WIDTH || z >= RobotArenaLayout.DEPTH
                        || cell == RobotArenaLayout.Cell.WALL || cell == RobotArenaLayout.Cell.SMALL_MDF
                        || cell == RobotArenaLayout.Cell.SMALL_FOAM) return false;
            }
        }
        return true;
    }

    private static Block block(RobotArenaBlueprint.Voxel voxel) {
        switch (voxel) {
            case AIR: return Blocks.air;
            case START_FLOOR: case RECOVERY_FLOOR: case EXIT_FLOOR: return Blocks.stained_hardened_clay;
            case MDF_WALL: return Blocks.planks;
            case PLASTIC_WALL: return Blocks.stained_glass;
            case ABSORBENT_WALL: return Blocks.wool;
            case SMALL_MDF: return ModBlocks.TARGET_MDF;
            case SMALL_FOAM: return ModBlocks.TARGET_FOAM;
            default: return Blocks.quartz_block;
        }
    }
    private static int metadata(RobotArenaBlueprint.Voxel voxel) {
        if (voxel == RobotArenaBlueprint.Voxel.START_FLOOR) return 5;
        if (voxel == RobotArenaBlueprint.Voxel.RECOVERY_FLOOR) return 4;
        if (voxel == RobotArenaBlueprint.Voxel.EXIT_FLOOR) return 1;
        if (voxel == RobotArenaBlueprint.Voxel.ABSORBENT_WALL) return 10;
        return 0;
    }
    private static void set(WorldServer world, int x, int y, int z, Block block, int metadata) {
        world.setBlock(x, y, z, block, metadata, 2);
    }

    public static final class Result {
        public final boolean success; public final String failure;
        public final int x, y, z, mdfWalls, plasticWalls, absorbentWalls;
        private Result(boolean success, String failure, int x, int y, int z,
                       int mdfWalls, int plasticWalls, int absorbentWalls) {
            this.success = success; this.failure = failure; this.x = x; this.y = y; this.z = z;
            this.mdfWalls = mdfWalls; this.plasticWalls = plasticWalls; this.absorbentWalls = absorbentWalls;
        }
        static Result success(int x, int y, int z, int mdf, int plastic, int absorbent) {
            return new Result(true, "", x, y, z, mdf, plastic, absorbent);
        }
        static Result failure(String failure) { return new Result(false, failure, 0, 0, 0, 0, 0, 0); }
    }
}
