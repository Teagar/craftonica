package br.com.craftonica.robot.modular.servo;

/** Deterministic torque-driven servo; target commands never replace physical position. */
public strictfp final class ServoModel {
    public static final double MAX_STEP_SECONDS = 0.025;
    private ServoModel() { }

    public static ServoStep step(ServoParameters p, ServoState before, ServoInput input, double seconds) {
        if (p == null || before == null || input == null || !finite(seconds)
                || seconds <= 0.0 || seconds > MAX_STEP_SECONDS) throw new IllegalArgumentException("servo step");
        boolean validPulse = input.pulseWidthMicros != null
                && input.pulseWidthMicros.intValue() >= (int) p.minimumPulseMicros
                && input.pulseWidthMicros.intValue() <= (int) p.maximumPulseMicros;
        double target = before.targetRadians;
        boolean hasTarget = before.hasTarget;
        double signalAge = before.secondsSinceValidSignal + seconds;
        ServoStep.Diagnostic diagnostic = ServoStep.Diagnostic.NONE;
        if (input.pulseWidthMicros != null && !validPulse) diagnostic = ServoStep.Diagnostic.INVALID_PULSE;
        if (validPulse) {
            double fraction = (input.pulseWidthMicros.doubleValue() - p.minimumPulseMicros)
                    / (p.maximumPulseMicros - p.minimumPulseMicros);
            target = p.minimumAngleRadians + fraction * (p.maximumAngleRadians - p.minimumAngleRadians);
            hasTarget = true; signalAge = 0.0;
        }

        boolean signalLost = signalAge > p.signalTimeoutSeconds;
        boolean controller = hasTarget && (!signalLost || input.signalLossPolicy == ServoInput.SignalLossPolicy.HOLD);
        if (signalLost && input.signalLossPolicy == ServoInput.SignalLossPolicy.SAFE_POSITION) {
            target = p.safeAngleRadians; hasTarget = true; controller = true;
        } else if (signalLost && input.signalLossPolicy == ServoInput.SignalLossPolicy.COAST) {
            controller = false;
        }
        if (signalLost && diagnostic == ServoStep.Diagnostic.NONE) diagnostic = ServoStep.Diagnostic.SIGNAL_LOST;
        if (!input.wiringValid) { controller = false; diagnostic = ServoStep.Diagnostic.OPEN_WIRING; }
        else if (!input.powered) { controller = false; diagnostic = ServoStep.Diagnostic.UNPOWERED; }
        else if (input.supplyVolts <= 0.0 || input.supplyVolts > p.nominalVoltage * 1.1) {
            controller = false; diagnostic = ServoStep.Diagnostic.INVALID_SUPPLY;
        }

        boolean shutdown = before.thermalShutdown;
        if (shutdown && before.temperatureCelsius <= p.maximumTemperatureCelsius - 10.0) shutdown = false;
        if (before.temperatureCelsius >= p.maximumTemperatureCelsius) shutdown = true;
        if (shutdown) { controller = false; diagnostic = ServoStep.Diagnostic.OVER_TEMPERATURE; }

        double error = target - before.positionRadians;
        double torque = 0.0;
        if (controller) {
            double controlledError = StrictMath.abs(error) <= p.deadbandRadians ? 0.0 : error;
            torque = p.proportionalGainNmPerRad * controlledError
                    - p.derivativeGainNmSecondsPerRad * before.velocityRadPerSecond;
            double supplyLimit = p.maximumTorqueNm * input.supplyVolts / p.nominalVoltage;
            double currentLimit = p.maximumCurrentAmps * p.torqueConstantNmPerAmp;
            torque = clamp(torque, -StrictMath.min(supplyLimit, currentLimit), StrictMath.min(supplyLimit, currentLimit));
        }
        double current = controller ? StrictMath.min(p.maximumCurrentAmps,
                StrictMath.abs(torque) / p.torqueConstantNmPerAmp) : 0.0;
        double net = torque - input.loadTorqueNm
                - p.viscousFrictionNmSecondsPerRad * before.velocityRadPerSecond;
        double velocity = before.velocityRadPerSecond + net / p.outputInertiaKgM2 * seconds;
        velocity = clamp(velocity, -p.maximumVelocityRadPerSecond, p.maximumVelocityRadPerSecond);
        double position = before.positionRadians + velocity * seconds;
        if (position < p.minimumAngleRadians) {
            position = p.minimumAngleRadians; if (velocity < 0.0) velocity = 0.0;
        } else if (position > p.maximumAngleRadians) {
            position = p.maximumAngleRadians; if (velocity > 0.0) velocity = 0.0;
        }
        double heatWatts = current * current * p.windingResistanceOhms;
        double coolingWatts = (before.temperatureCelsius - p.ambientTemperatureCelsius)
                / p.thermalResistanceKelvinPerWatt;
        double temperature = before.temperatureCelsius
                + (heatWatts - coolingWatts) / p.thermalCapacityJoulesPerKelvin * seconds;
        if (temperature < p.ambientTemperatureCelsius) temperature = p.ambientTemperatureCelsius;
        if (temperature >= p.maximumTemperatureCelsius) shutdown = true;
        ServoState state = new ServoState(position, velocity, target, temperature, signalAge, hasTarget, shutdown);
        return new ServoStep(state, torque, current, error,
                shutdown ? ServoStep.Diagnostic.OVER_TEMPERATURE : diagnostic);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return StrictMath.max(minimum, StrictMath.min(maximum, value));
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
