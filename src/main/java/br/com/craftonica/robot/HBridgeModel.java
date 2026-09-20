package br.com.craftonica.robot;

/** Pure educational dual-input H-bridge channel model. */
public strictfp final class HBridgeModel {
    public static final int PWM_DEAD_ZONE = 32;
    private HBridgeModel() { }

    public static Output evaluate(boolean powered, boolean wiringValid, boolean in1, boolean in2, int pwm) {
        if (!powered) return new Output(0.0, false, 0.0, "NO_POWER");
        if (!wiringValid || pwm < 0 || pwm > 255) return new Output(0.0, false, 0.0, "INVALID_WIRING");
        if (in1 == in2) return new Output(0.0, in1, in1 ? 0.12 : 0.02, "");
        if (pwm < PWM_DEAD_ZONE) return new Output(0.0, false, 0.04, "PWM_DEAD_ZONE");
        double duty = (pwm - PWM_DEAD_ZONE) / (double) (255 - PWM_DEAD_ZONE);
        return new Output((in1 ? 1.0 : -1.0) * duty, false, 0.06 + duty * 0.34, "");
    }

    public static final class Output {
        public final double effort;
        public final boolean braking;
        public final double currentAmps;
        public final String diagnostic;
        Output(double effort, boolean braking, double currentAmps, String diagnostic) {
            this.effort = effort; this.braking = braking; this.currentAmps = currentAmps;
            this.diagnostic = diagnostic;
        }
    }
}
