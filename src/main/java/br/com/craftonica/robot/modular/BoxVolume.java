package br.com.craftonica.robot.modular;

/** Axis-aligned local volume in metres. */
public strictfp final class BoxVolume {
    public static final BoxVolume FULL_BLOCK = new BoxVolume(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public final Vector3 minimum;
    public final Vector3 maximum;

    public BoxVolume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        minimum = new Vector3(minX, minY, minZ);
        maximum = new Vector3(maxX, maxY, maxZ);
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) throw new IllegalArgumentException("volume");
    }
}
