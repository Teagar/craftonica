package br.com.craftonica.robot.modular.joint;

public final class JointStep {
    public enum Diagnostic { NONE, LOWER_HARD_STOP, UPPER_HARD_STOP, EFFORT_SATURATED }
    public final JointState state;
    public final double appliedEffort, stopReaction;
    public final Diagnostic diagnostic;
    JointStep(JointState state, double effort, double reaction, Diagnostic diagnostic) {
        this.state = state; this.appliedEffort = effort; this.stopReaction = reaction; this.diagnostic = diagnostic;
    }
}
