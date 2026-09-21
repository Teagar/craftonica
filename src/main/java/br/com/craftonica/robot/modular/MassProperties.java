package br.com.craftonica.robot.modular;

/** Component mass, local centre of mass and principal inertia in SI units. */
public strictfp final class MassProperties {
    public final double massKg;
    public final Vector3 centerMetres;
    public final Vector3 principalInertiaKgMetresSquared;

    public MassProperties(double massKg, Vector3 centerMetres, Vector3 principalInertia) {
        this.massKg = ContractValues.positive(massKg, "massKg");
        if (centerMetres == null || principalInertia == null) throw new IllegalArgumentException("mass vectors");
        ContractValues.positive(principalInertia.x, "inertiaX");
        ContractValues.positive(principalInertia.y, "inertiaY");
        ContractValues.positive(principalInertia.z, "inertiaZ");
        this.centerMetres = centerMetres;
        this.principalInertiaKgMetresSquared = principalInertia;
    }

    public static MassProperties box(double massKg, BoxVolume volume) {
        if (volume == null) throw new IllegalArgumentException("volume");
        double x = volume.maximum.x - volume.minimum.x;
        double y = volume.maximum.y - volume.minimum.y;
        double z = volume.maximum.z - volume.minimum.z;
        double factor = ContractValues.positive(massKg, "massKg") / 12.0;
        return new MassProperties(massKg, new Vector3(
                (volume.minimum.x + volume.maximum.x) * 0.5,
                (volume.minimum.y + volume.maximum.y) * 0.5,
                (volume.minimum.z + volume.maximum.z) * 0.5),
                new Vector3(factor * (y * y + z * z), factor * (x * x + z * z),
                        factor * (x * x + y * y)));
    }
}
