package br.com.craftonica.sensor;

import br.com.craftonica.block.BlockCalibrationTarget;
import br.com.craftonica.tile.TileEntityCalibrationTarget;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Loaded-world-only acoustic query. It never requests or generates a chunk. */
public final class UltrasonicRaycaster {
    private static final double MAX_BLOCKS = UltrasonicMeasurementModel.MAX_CM / 100.0;

    private UltrasonicRaycaster() { }

    public static Hit trace(World world, int x, int y, int z, int front) {
        if (world == null || front < 2 || front > 5) return null;
        int dx = dx(front), dz = dz(front);
        int adjacentX = x + dx, adjacentZ = z + dz;
        if (world.getChunkProvider().chunkExists(adjacentX >> 4, adjacentZ >> 4)) {
            Block adjacent = world.getBlock(adjacentX, y, adjacentZ);
            TileEntity tile = world.getTileEntity(adjacentX, y, adjacentZ);
            if (adjacent instanceof BlockCalibrationTarget && tile instanceof TileEntityCalibrationTarget) {
                TileEntityCalibrationTarget target = (TileEntityCalibrationTarget) tile;
                return new Hit(target.getDistanceCentimeters(), target.getAngleDegrees(),
                        ((BlockCalibrationTarget) adjacent).getProfile(), "calibration");
            }
        }

        Vec3 start = Vec3.createVectorHelper(x + 0.5 + dx * 0.501, y + 0.5, z + 0.5 + dz * 0.501);
        Vec3 end = Vec3.createVectorHelper(start.xCoord + dx * MAX_BLOCKS,
                start.yCoord, start.zCoord + dz * MAX_BLOCKS);
        MovingObjectPosition hit = world.rayTraceBlocks(start, end, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !world.getChunkProvider().chunkExists(hit.blockX >> 4, hit.blockZ >> 4)) return null;
        double bx = hit.hitVec.xCoord - start.xCoord;
        double by = hit.hitVec.yCoord - start.yCoord;
        double bz = hit.hitVec.zCoord - start.zCoord;
        double centimeters = StrictMath.sqrt(bx * bx + by * by + bz * bz) * 100.0;
        Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        double incidence = incidence(front, hit.sideHit);
        return new Hit(centimeters, incidence, AcousticMaterialProfile.forBlock(block), "world");
    }

    private static double incidence(int front, int hitSide) {
        return hitSide == opposite(front) ? 0.0 : hitSide == 0 || hitSide == 1 ? 75.0 : 60.0;
    }

    private static int opposite(int side) { return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
    private static int dx(int side) { return side == 4 ? -1 : side == 5 ? 1 : 0; }
    private static int dz(int side) { return side == 2 ? -1 : side == 3 ? 1 : 0; }

    public static final class Hit {
        public final double centimeters;
        public final double incidenceDegrees;
        public final AcousticMaterialProfile material;
        public final String mode;

        Hit(double centimeters, double incidenceDegrees, AcousticMaterialProfile material, String mode) {
            this.centimeters = centimeters;
            this.incidenceDegrees = incidenceDegrees;
            this.material = material;
            this.mode = mode;
        }
    }
}
