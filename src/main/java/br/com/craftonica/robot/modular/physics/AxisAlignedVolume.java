package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.Vector3;

/** Immutable axis-aligned volume in metres. */
public strictfp final class AxisAlignedVolume {
    public final Vector3 minimum;
    public final Vector3 maximum;

    public AxisAlignedVolume(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ) {
        minimum = new Vector3(minX, minY, minZ);
        maximum = new Vector3(maxX, maxY, maxZ);
        if (maxX <= minX || maxY <= minY || maxZ <= minZ)
            throw new IllegalArgumentException("volume");
    }

    public AxisAlignedVolume offset(double x, double y, double z) {
        return new AxisAlignedVolume(minimum.x + x, minimum.y + y, minimum.z + z,
                maximum.x + x, maximum.y + y, maximum.z + z);
    }
}
