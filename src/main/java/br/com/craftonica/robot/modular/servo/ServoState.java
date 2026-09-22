package br.com.craftonica.robot.modular.servo;

public strictfp final class ServoState {
    public final double positionRadians, velocityRadPerSecond, targetRadians;
    public final double temperatureCelsius, secondsSinceValidSignal;
    public final boolean hasTarget, thermalShutdown;

    public ServoState(double position, double velocity, double target, double temperature,
            double signalAge, boolean hasTarget, boolean thermalShutdown) {
        double[] values = { position, velocity, target, temperature, signalAge };
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException("servo state");
        if (signalAge < 0.0) throw new IllegalArgumentException("signal age");
        this.positionRadians = position; this.velocityRadPerSecond = velocity;
        this.targetRadians = target; this.temperatureCelsius = temperature;
        this.secondsSinceValidSignal = signalAge; this.hasTarget = hasTarget;
        this.thermalShutdown = thermalShutdown;
    }

    public static ServoState ambient(ServoParameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("parameters");
        return new ServoState(parameters.safeAngleRadians, 0.0, parameters.safeAngleRadians,
                parameters.ambientTemperatureCelsius, parameters.signalTimeoutSeconds, false, false);
    }
}
