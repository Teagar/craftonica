package br.com.craftonica.robot;

/** Deterministic differential-drive kinematics in Minecraft metres and seconds. */
public strictfp final class DifferentialDriveModel {
    public static final double MAX_WHEEL_SPEED = 1.5;
    public static final double WHEEL_TRACK = 1.0;
    public static final double MAX_ACCELERATION = 3.0;
    public static final double COAST_DECELERATION = 2.0;
    public static final double BRAKE_DECELERATION = 6.0;
    private DifferentialDriveModel() { }

    public static Step step(double leftSpeed, double rightSpeed, HBridgeModel.Output left,
                            HBridgeModel.Output right, double yawDegrees, double dt) {
        if (!finite(leftSpeed) || !finite(rightSpeed) || left == null || right == null
                || !finite(yawDegrees) || !finite(dt) || dt <= 0.0 || dt > 0.05)
            throw new IllegalArgumentException("invalid drive step");
        double nextLeft = approach(leftSpeed, left.effort * MAX_WHEEL_SPEED,
                rate(left, leftSpeed) * dt);
        double nextRight = approach(rightSpeed, right.effort * MAX_WHEEL_SPEED,
                rate(right, rightSpeed) * dt);
        double linear = (nextRight + nextLeft) * 0.5;
        double angular = clamp((nextRight - nextLeft) / WHEEL_TRACK, -3.0, 3.0);
        double deltaYaw = StrictMath.toDegrees(angular * dt);
        double middleYaw = StrictMath.toRadians(yawDegrees + deltaYaw * 0.5);
        double distance = linear * dt;
        return new Step(nextLeft, nextRight, -StrictMath.sin(middleYaw) * distance,
                StrictMath.cos(middleYaw) * distance, deltaYaw,
                left.currentAmps + right.currentAmps);
    }

    private static double rate(HBridgeModel.Output output, double speed) {
        if (output.braking) return BRAKE_DECELERATION;
        if (StrictMath.abs(output.effort) < 1.0e-12) return COAST_DECELERATION;
        return MAX_ACCELERATION;
    }
    private static double approach(double value, double target, double amount) {
        return value < target ? Math.min(target, value + amount) : Math.max(target, value - amount);
    }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static final class Step {
        public final double leftSpeed, rightSpeed, deltaX, deltaZ, deltaYawDegrees, currentAmps;
        Step(double leftSpeed, double rightSpeed, double deltaX, double deltaZ,
             double deltaYawDegrees, double currentAmps) {
            this.leftSpeed = leftSpeed; this.rightSpeed = rightSpeed; this.deltaX = deltaX;
            this.deltaZ = deltaZ; this.deltaYawDegrees = deltaYawDegrees; this.currentAmps = currentAmps;
        }
    }
}
