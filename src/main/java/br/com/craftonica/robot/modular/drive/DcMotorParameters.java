package br.com.craftonica.robot.modular.drive;

/** SI parameters for the educational modular motor. */
public final class DcMotorParameters {
    public final double resistanceOhms, torqueConstantNmPerAmp, backEmfVoltSecondsPerRad;
    public final double rotorInertiaKgM2, viscousFrictionNmSecondsPerRad;
    public final double maximumCurrentAmps, maximumAngularVelocityRadPerSecond;
    public final double ambientTemperatureCelsius, maximumTemperatureCelsius;
    public final double thermalCapacityJoulesPerKelvin, thermalResistanceKelvinPerWatt;

    public DcMotorParameters(double resistanceOhms, double torqueConstantNmPerAmp,
            double backEmfVoltSecondsPerRad, double rotorInertiaKgM2,
            double viscousFrictionNmSecondsPerRad, double maximumCurrentAmps,
            double maximumAngularVelocityRadPerSecond, double ambientTemperatureCelsius,
            double maximumTemperatureCelsius, double thermalCapacityJoulesPerKelvin,
            double thermalResistanceKelvinPerWatt) {
        double[] values = { resistanceOhms, torqueConstantNmPerAmp, backEmfVoltSecondsPerRad,
                rotorInertiaKgM2, viscousFrictionNmSecondsPerRad, maximumCurrentAmps,
                maximumAngularVelocityRadPerSecond, thermalCapacityJoulesPerKelvin,
                thermalResistanceKelvinPerWatt };
        for (double value : values) if (!finite(value) || value <= 0.0) throw new IllegalArgumentException("motor parameters");
        if (!finite(ambientTemperatureCelsius) || !finite(maximumTemperatureCelsius)
                || maximumTemperatureCelsius <= ambientTemperatureCelsius)
            throw new IllegalArgumentException("motor temperatures");
        this.resistanceOhms = resistanceOhms; this.torqueConstantNmPerAmp = torqueConstantNmPerAmp;
        this.backEmfVoltSecondsPerRad = backEmfVoltSecondsPerRad; this.rotorInertiaKgM2 = rotorInertiaKgM2;
        this.viscousFrictionNmSecondsPerRad = viscousFrictionNmSecondsPerRad;
        this.maximumCurrentAmps = maximumCurrentAmps;
        this.maximumAngularVelocityRadPerSecond = maximumAngularVelocityRadPerSecond;
        this.ambientTemperatureCelsius = ambientTemperatureCelsius;
        this.maximumTemperatureCelsius = maximumTemperatureCelsius;
        this.thermalCapacityJoulesPerKelvin = thermalCapacityJoulesPerKelvin;
        this.thermalResistanceKelvinPerWatt = thermalResistanceKelvinPerWatt;
    }

    public static DcMotorParameters educational() {
        return new DcMotorParameters(4.0, 0.04, 0.04, 0.0002, 0.0001,
                1.0, 300.0, 25.0, 120.0, 20.0, 8.0);
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
