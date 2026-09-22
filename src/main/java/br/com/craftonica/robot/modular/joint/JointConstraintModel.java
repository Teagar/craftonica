package br.com.craftonica.robot.modular.joint;

import br.com.craftonica.robot.modular.JointProfile;

/** Semi-implicit one-DOF integration with unilateral finite hard-stop reactions. */
public strictfp final class JointConstraintModel {
    public static final double MAX_STEP_SECONDS = 0.025;
    private JointConstraintModel() { }

    public static JointStep step(JointProfile profile, JointState before,
            double requestedEffort, double externalLoad, double seconds) {
        if (profile == null || before == null || !finite(requestedEffort) || !finite(externalLoad)
                || !finite(seconds) || seconds <= 0.0 || seconds > MAX_STEP_SECONDS)
            throw new IllegalArgumentException("joint step");
        if (before.position < profile.minimumPosition - 1.0e-9
                || before.position > profile.maximumPosition + 1.0e-9)
            throw new IllegalArgumentException("joint starts outside limits");
        double effort = clamp(requestedEffort, -profile.maximumEffort, profile.maximumEffort);
        JointStep.Diagnostic diagnostic = effort == requestedEffort
                ? JointStep.Diagnostic.NONE : JointStep.Diagnostic.EFFORT_SATURATED;
        double net = effort - externalLoad - profile.viscousFriction * before.velocity;
        double velocity = before.velocity + net / profile.movingInertiaOrMass * seconds;
        velocity = clamp(velocity, -profile.maximumVelocity, profile.maximumVelocity);
        double position = before.position + velocity * seconds;
        double reaction = 0.0;
        if (position < profile.minimumPosition) {
            reaction = stopReaction(profile, net, velocity, seconds);
            position = profile.minimumPosition; if (velocity < 0.0) velocity = 0.0;
            diagnostic = JointStep.Diagnostic.LOWER_HARD_STOP;
        } else if (position > profile.maximumPosition) {
            reaction = -stopReaction(profile, net, velocity, seconds);
            position = profile.maximumPosition; if (velocity > 0.0) velocity = 0.0;
            diagnostic = JointStep.Diagnostic.UPPER_HARD_STOP;
        } else if (position <= profile.minimumPosition && net < 0.0) {
            reaction = stopReaction(profile, net, velocity, seconds);
            position = profile.minimumPosition; velocity = StrictMath.max(0.0, velocity);
            diagnostic = JointStep.Diagnostic.LOWER_HARD_STOP;
        } else if (position >= profile.maximumPosition && net > 0.0) {
            reaction = -stopReaction(profile, net, velocity, seconds);
            position = profile.maximumPosition; velocity = StrictMath.min(0.0, velocity);
            diagnostic = JointStep.Diagnostic.UPPER_HARD_STOP;
        }
        return new JointStep(new JointState(position, velocity), effort, reaction, diagnostic);
    }

    private static double stopReaction(JointProfile profile, double net, double velocity, double seconds) {
        double required = StrictMath.abs(net) + profile.movingInertiaOrMass * StrictMath.abs(velocity) / seconds;
        return StrictMath.min(profile.maximumStopReaction, required);
    }
    private static double clamp(double value, double minimum, double maximum) {
        return StrictMath.max(minimum, StrictMath.min(maximum, value));
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
