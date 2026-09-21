package br.com.craftonica.robot.modular.drive;

/** Dynamic motor/bridge state. Temperatures are Celsius; angular velocity is rad/s. */
public final class DriveState {
    public final double angularVelocityRadPerSecond;
    public final double motorTemperatureCelsius;
    public final double bridgeTemperatureCelsius;
    public final boolean thermalShutdown;

    public DriveState(double angularVelocityRadPerSecond, double motorTemperatureCelsius,
                      double bridgeTemperatureCelsius, boolean thermalShutdown) {
        if (!finite(angularVelocityRadPerSecond) || !finite(motorTemperatureCelsius)
                || !finite(bridgeTemperatureCelsius)) throw new IllegalArgumentException("drive state");
        this.angularVelocityRadPerSecond = angularVelocityRadPerSecond;
        this.motorTemperatureCelsius = motorTemperatureCelsius;
        this.bridgeTemperatureCelsius = bridgeTemperatureCelsius;
        this.thermalShutdown = thermalShutdown;
    }
    public static DriveState ambient(DcMotorParameters motor, HBridgeParameters bridge) {
        return new DriveState(0.0, motor.ambientTemperatureCelsius, bridge.ambientTemperatureCelsius, false);
    }
    public double getRevolutionsPerMinute() {
        return angularVelocityRadPerSecond * 60.0 / (2.0 * StrictMath.PI);
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
