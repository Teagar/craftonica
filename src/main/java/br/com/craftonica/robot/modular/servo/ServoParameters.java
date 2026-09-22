package br.com.craftonica.robot.modular.servo;

/** SI parameters for the educational positional servo. */
public strictfp final class ServoParameters {
    public final double nominalVoltage, minimumPulseMicros, maximumPulseMicros;
    public final double minimumAngleRadians, maximumAngleRadians, safeAngleRadians;
    public final double proportionalGainNmPerRad, derivativeGainNmSecondsPerRad;
    public final double deadbandRadians, maximumTorqueNm, maximumVelocityRadPerSecond;
    public final double maximumCurrentAmps, torqueConstantNmPerAmp, windingResistanceOhms;
    public final double outputInertiaKgM2, viscousFrictionNmSecondsPerRad;
    public final double ambientTemperatureCelsius, maximumTemperatureCelsius;
    public final double thermalCapacityJoulesPerKelvin, thermalResistanceKelvinPerWatt;
    public final double signalTimeoutSeconds;

    public ServoParameters(double nominalVoltage, double minimumPulseMicros, double maximumPulseMicros,
            double minimumAngleRadians, double maximumAngleRadians, double safeAngleRadians,
            double proportionalGain, double derivativeGain, double deadbandRadians,
            double maximumTorque, double maximumVelocity, double maximumCurrent,
            double torqueConstant, double windingResistance, double outputInertia,
            double viscousFriction, double ambientTemperature, double maximumTemperature,
            double thermalCapacity, double thermalResistance, double signalTimeout) {
        double[] positive = { nominalVoltage, minimumPulseMicros, maximumPulseMicros,
                proportionalGain, derivativeGain, deadbandRadians, maximumTorque, maximumVelocity,
                maximumCurrent, torqueConstant, windingResistance, outputInertia, viscousFriction,
                thermalCapacity, thermalResistance, signalTimeout };
        for (double value : positive) if (!finite(value) || value <= 0.0)
            throw new IllegalArgumentException("servo parameters");
        if (maximumPulseMicros <= minimumPulseMicros || !finite(minimumAngleRadians)
                || !finite(maximumAngleRadians) || maximumAngleRadians <= minimumAngleRadians
                || !finite(safeAngleRadians) || safeAngleRadians < minimumAngleRadians
                || safeAngleRadians > maximumAngleRadians || !finite(ambientTemperature)
                || !finite(maximumTemperature) || maximumTemperature <= ambientTemperature)
            throw new IllegalArgumentException("servo ranges");
        this.nominalVoltage = nominalVoltage; this.minimumPulseMicros = minimumPulseMicros;
        this.maximumPulseMicros = maximumPulseMicros; this.minimumAngleRadians = minimumAngleRadians;
        this.maximumAngleRadians = maximumAngleRadians; this.safeAngleRadians = safeAngleRadians;
        this.proportionalGainNmPerRad = proportionalGain;
        this.derivativeGainNmSecondsPerRad = derivativeGain; this.deadbandRadians = deadbandRadians;
        this.maximumTorqueNm = maximumTorque; this.maximumVelocityRadPerSecond = maximumVelocity;
        this.maximumCurrentAmps = maximumCurrent; this.torqueConstantNmPerAmp = torqueConstant;
        this.windingResistanceOhms = windingResistance; this.outputInertiaKgM2 = outputInertia;
        this.viscousFrictionNmSecondsPerRad = viscousFriction;
        this.ambientTemperatureCelsius = ambientTemperature;
        this.maximumTemperatureCelsius = maximumTemperature;
        this.thermalCapacityJoulesPerKelvin = thermalCapacity;
        this.thermalResistanceKelvinPerWatt = thermalResistance;
        this.signalTimeoutSeconds = signalTimeout;
    }

    public static ServoParameters educational() {
        return new ServoParameters(5.0, 1000.0, 2000.0, 0.0, StrictMath.PI, StrictMath.PI / 2.0,
                0.9, 0.08, StrictMath.toRadians(1.0), 0.22, StrictMath.toRadians(300.0),
                1.2, 0.2, 3.0, 0.0008, 0.003, 25.0, 65.0, 8.0, 12.0, 0.1);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
