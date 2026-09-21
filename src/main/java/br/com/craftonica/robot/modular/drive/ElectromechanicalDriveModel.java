package br.com.craftonica.robot.modular.drive;

/**
 * Deterministic educational DC model. PWM is averaged over the step; commutation ripple,
 * brush inductance and spatial magnetic effects are intentionally omitted.
 */
public strictfp final class ElectromechanicalDriveModel {
    public static final double MAX_STEP_SECONDS = 0.05;
    private ElectromechanicalDriveModel() { }

    public static DriveStep step(DcMotorParameters motor, HBridgeParameters bridge,
            DriveState previous, DriveInput input, double seconds) {
        if (motor == null || bridge == null || previous == null || input == null
                || !finite(seconds) || seconds <= 0.0 || seconds > MAX_STEP_SECONDS)
            throw new IllegalArgumentException("drive step");
        DriveStep.Diagnostic invalid = validate(motor, bridge, previous, input);
        if (invalid != DriveStep.Diagnostic.NONE)
            return safe(motor, bridge, previous, input, seconds, invalid);

        double duty = input.pwm / 255.0;
        boolean closed = input.mode != DriveInput.Mode.COAST;
        double terminalVoltage = 0.0;
        double circuitResistance = motor.resistanceOhms;
        if (input.mode == DriveInput.Mode.FORWARD || input.mode == DriveInput.Mode.REVERSE) {
            double available = StrictMath.max(0.0, input.supplyVolts - bridge.voltageDropVolts);
            terminalVoltage = (input.mode == DriveInput.Mode.FORWARD ? 1.0 : -1.0) * available * duty;
            circuitResistance += bridge.onResistanceOhms;
        } else if (input.mode == DriveInput.Mode.BRAKE) circuitResistance += bridge.brakeResistanceOhms;

        double backEmf = motor.backEmfVoltSecondsPerRad * previous.angularVelocityRadPerSecond;
        double current = closed ? (terminalVoltage - backEmf) / circuitResistance : 0.0;
        double currentLimit = StrictMath.min(motor.maximumCurrentAmps, bridge.maximumCurrentAmps);
        current = clamp(current, -currentLimit, currentLimit);
        double electromagneticTorque = motor.torqueConstantNmPerAmp * current;
        double viscousTorque = motor.viscousFrictionNmSecondsPerRad * previous.angularVelocityRadPerSecond;
        double shaftTorque = electromagneticTorque - viscousTorque;
        double acceleration = (shaftTorque - input.loadTorqueNm) / motor.rotorInertiaKgM2;
        double velocity = clamp(previous.angularVelocityRadPerSecond + acceleration * seconds,
                -motor.maximumAngularVelocityRadPerSecond, motor.maximumAngularVelocityRadPerSecond);
        if ((input.mode == DriveInput.Mode.COAST || input.mode == DriveInput.Mode.BRAKE)
                && previous.angularVelocityRadPerSecond * velocity < 0.0) velocity = 0.0;

        double motorLoss = current * current * motor.resistanceOhms;
        boolean driving = input.mode == DriveInput.Mode.FORWARD || input.mode == DriveInput.Mode.REVERSE;
        double bridgeLoss = current * current * (input.mode == DriveInput.Mode.BRAKE
                ? bridge.brakeResistanceOhms : bridge.onResistanceOhms)
                + (driving ? StrictMath.abs(current) * bridge.voltageDropVolts : 0.0);
        double motorTemp = thermal(previous.motorTemperatureCelsius, motor.ambientTemperatureCelsius,
                motorLoss, motor.thermalCapacityJoulesPerKelvin, motor.thermalResistanceKelvinPerWatt, seconds);
        double bridgeTemp = thermal(previous.bridgeTemperatureCelsius, bridge.ambientTemperatureCelsius,
                bridgeLoss, bridge.thermalCapacityJoulesPerKelvin, bridge.thermalResistanceKelvinPerWatt, seconds);
        boolean shutdown = motorTemp >= motor.maximumTemperatureCelsius
                || bridgeTemp >= bridge.maximumTemperatureCelsius;
        DriveState state = new DriveState(velocity, motorTemp, bridgeTemp, shutdown);
        double sourcePower = driving ? StrictMath.abs(input.supplyVolts * duty * current) : 0.0;
        return new DriveStep(state, input.mode, terminalVoltage, current, electromagneticTorque,
                shaftTorque, sourcePower, motorLoss, bridgeLoss, shaftTorque * velocity,
                shutdown ? DriveStep.Diagnostic.OVER_TEMPERATURE : DriveStep.Diagnostic.NONE);
    }

    private static DriveStep.Diagnostic validate(DcMotorParameters motor, HBridgeParameters bridge,
                                                  DriveState state, DriveInput input) {
        if (!input.wiringValid) return DriveStep.Diagnostic.OPEN_CIRCUIT;
        if (!finite(input.supplyVolts) || input.supplyVolts < 0.0 || input.supplyVolts > bridge.maximumSupplyVolts)
            return DriveStep.Diagnostic.INVALID_SUPPLY;
        if ((input.mode == DriveInput.Mode.FORWARD || input.mode == DriveInput.Mode.REVERSE)
                && input.supplyVolts <= 0.0) return DriveStep.Diagnostic.INVALID_SUPPLY;
        if (input.pwm < 0 || input.pwm > 255) return DriveStep.Diagnostic.INVALID_PWM;
        if (!finite(input.loadTorqueNm)) return DriveStep.Diagnostic.OPEN_CIRCUIT;
        if (state.thermalShutdown || state.motorTemperatureCelsius >= motor.maximumTemperatureCelsius
                || state.bridgeTemperatureCelsius >= bridge.maximumTemperatureCelsius)
            return DriveStep.Diagnostic.OVER_TEMPERATURE;
        return DriveStep.Diagnostic.NONE;
    }

    private static DriveStep safe(DcMotorParameters motor, HBridgeParameters bridge, DriveState previous,
            DriveInput input, double seconds, DriveStep.Diagnostic diagnostic) {
        double velocity = previous.angularVelocityRadPerSecond;
        double drag = motor.viscousFrictionNmSecondsPerRad * velocity / motor.rotorInertiaKgM2;
        double next = velocity - drag * seconds;
        if (velocity * next < 0.0) next = 0.0;
        double motorTemp = thermal(previous.motorTemperatureCelsius, motor.ambientTemperatureCelsius,
                0.0, motor.thermalCapacityJoulesPerKelvin, motor.thermalResistanceKelvinPerWatt, seconds);
        double bridgeTemp = thermal(previous.bridgeTemperatureCelsius, bridge.ambientTemperatureCelsius,
                0.0, bridge.thermalCapacityJoulesPerKelvin, bridge.thermalResistanceKelvinPerWatt, seconds);
        DriveState state = new DriveState(next, motorTemp, bridgeTemp,
                diagnostic == DriveStep.Diagnostic.OVER_TEMPERATURE);
        return new DriveStep(state, DriveInput.Mode.COAST, 0.0, 0.0, 0.0,
                -motor.viscousFrictionNmSecondsPerRad * velocity, 0.0, 0.0, 0.0, 0.0, diagnostic);
    }

    private static double thermal(double temperature, double ambient, double lossWatts,
            double capacity, double resistance, double seconds) {
        double coolingWatts = (temperature - ambient) / resistance;
        return temperature + (lossWatts - coolingWatts) * seconds / capacity;
    }
    private static double clamp(double value, double min, double max) { return StrictMath.max(min, StrictMath.min(max, value)); }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
