package br.com.craftonica.robot.modular.physics;

/** Conservative compound collision using one rotated AABB per manifest volume. */
public strictfp final class CompoundCollisionProbe {
    public enum Result { CLEAR, BLOCKED, UNLOADED }

    public interface WorldView {
        boolean isLoaded(AxisAlignedVolume worldVolume);
        boolean collides(AxisAlignedVolume worldVolume);
    }

    private CompoundCollisionProbe() { }

    public static Result test(RigidBodyProperties body, WorldView world,
                              double x, double y, double z, double yawRadians) {
        if (body == null || world == null || Double.isNaN(yawRadians) || Double.isInfinite(yawRadians))
            throw new IllegalArgumentException("collision probe");
        for (AxisAlignedVolume local : body.getCollisionVolumes()) {
            AxisAlignedVolume transformed = transform(local, x, y, z, yawRadians);
            if (!world.isLoaded(transformed)) return Result.UNLOADED;
            if (world.collides(transformed)) return Result.BLOCKED;
        }
        return Result.CLEAR;
    }

    /** Conservative swept test prevents tunnelling between two integrated poses. */
    public static Result sweep(RigidBodyProperties body, WorldView world,
            TerrestrialRigidBodyModel.State from, TerrestrialRigidBodyModel.State to) {
        if (body == null || world == null || from == null || to == null)
            throw new IllegalArgumentException("collision sweep");
        for (AxisAlignedVolume local : body.getCollisionVolumes()) {
            AxisAlignedVolume start = transform(local, from.x, from.y, from.z, from.yawRadians);
            AxisAlignedVolume end = transform(local, to.x, to.y, to.z, to.yawRadians);
            AxisAlignedVolume swept = union(start, end);
            if (!world.isLoaded(swept)) return Result.UNLOADED;
            if (world.collides(swept)) return Result.BLOCKED;
        }
        return Result.CLEAR;
    }

    /** Entity x/z is the centre of the anchor block; local y starts at the anchor floor. */
    static AxisAlignedVolume transform(AxisAlignedVolume local, double x, double y, double z, double yaw) {
        double cosine = StrictMath.cos(yaw), sine = StrictMath.sin(yaw);
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int ix = 0; ix < 2; ix++) for (int iz = 0; iz < 2; iz++) {
            double lx = (ix == 0 ? local.minimum.x : local.maximum.x) - 0.5;
            double lz = (iz == 0 ? local.minimum.z : local.maximum.z) - 0.5;
            double wx = x + cosine * lx + sine * lz;
            double wz = z - sine * lx + cosine * lz;
            minX = StrictMath.min(minX, wx); maxX = StrictMath.max(maxX, wx);
            minZ = StrictMath.min(minZ, wz); maxZ = StrictMath.max(maxZ, wz);
        }
        return new AxisAlignedVolume(minX, y + local.minimum.y, minZ,
                maxX, y + local.maximum.y, maxZ);
    }

    private static AxisAlignedVolume union(AxisAlignedVolume a, AxisAlignedVolume b) {
        return new AxisAlignedVolume(StrictMath.min(a.minimum.x, b.minimum.x),
                StrictMath.min(a.minimum.y, b.minimum.y), StrictMath.min(a.minimum.z, b.minimum.z),
                StrictMath.max(a.maximum.x, b.maximum.x), StrictMath.max(a.maximum.y, b.maximum.y),
                StrictMath.max(a.maximum.z, b.maximum.z));
    }
}
