package br.com.craftonica.robot.modular.joint;

public strictfp final class JointState {
    public final double position, velocity;
    public JointState(double position, double velocity) {
        if (!finite(position) || !finite(velocity)) throw new IllegalArgumentException("joint state");
        this.position = position; this.velocity = velocity;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
