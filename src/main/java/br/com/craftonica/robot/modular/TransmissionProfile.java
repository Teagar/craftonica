package br.com.craftonica.robot.modular;

/** Immutable rotary transmission data used by the Forge-free mechanical graph. */
public final class TransmissionProfile {
    public enum Kind { SHAFT, BEARING, SPUR_GEAR }

    public final Kind kind;
    public final double efficiency;
    public final double rotationalInertiaKgM2;
    public final double maximumAngularVelocityRadPerSecond;
    public final int toothCount;
    public final double gearModuleMetres;

    private TransmissionProfile(Kind kind, double efficiency, double inertia,
            double maximumAngularVelocity, int toothCount, double gearModuleMetres) {
        if (kind == null || !finite(efficiency) || efficiency <= 0.0 || efficiency > 1.0
                || !finite(inertia) || inertia < 0.0 || !finite(maximumAngularVelocity)
                || maximumAngularVelocity <= 0.0)
            throw new IllegalArgumentException("transmission profile");
        if (kind == Kind.SPUR_GEAR) {
            if (toothCount < 6 || toothCount > 256 || !finite(gearModuleMetres)
                    || gearModuleMetres <= 0.0) throw new IllegalArgumentException("spur gear profile");
        } else if (toothCount != 0 || gearModuleMetres != 0.0) {
            throw new IllegalArgumentException("non-gear teeth");
        }
        this.kind = kind; this.efficiency = efficiency; this.rotationalInertiaKgM2 = inertia;
        this.maximumAngularVelocityRadPerSecond = maximumAngularVelocity;
        this.toothCount = toothCount; this.gearModuleMetres = gearModuleMetres;
    }

    public static TransmissionProfile shaft(double efficiency, double inertia, double maximumAngularVelocity) {
        return new TransmissionProfile(Kind.SHAFT, efficiency, inertia, maximumAngularVelocity, 0, 0.0);
    }

    public static TransmissionProfile bearing(double efficiency, double inertia, double maximumAngularVelocity) {
        return new TransmissionProfile(Kind.BEARING, efficiency, inertia, maximumAngularVelocity, 0, 0.0);
    }

    public static TransmissionProfile spurGear(int teeth, double moduleMetres, double efficiency,
            double inertia, double maximumAngularVelocity) {
        return new TransmissionProfile(Kind.SPUR_GEAR, efficiency, inertia,
                maximumAngularVelocity, teeth, moduleMetres);
    }

    public boolean meshesWith(TransmissionProfile other) {
        return other != null && kind == Kind.SPUR_GEAR && other.kind == Kind.SPUR_GEAR
                && StrictMath.abs(gearModuleMetres - other.gearModuleMetres) <= 1.0e-12;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
