package br.com.craftonica.robot.modular;

/** Versioned one-degree-of-freedom joint and integrated hard-stop contract. */
public strictfp final class JointProfile {
    public enum Kind { REVOLUTE, PRISMATIC }
    public final Kind kind;
    public final Direction axis;
    public final double minimumPosition, maximumPosition;
    public final double maximumVelocity, maximumEffort, viscousFriction;
    public final double movingInertiaOrMass, maximumStopReaction;

    public JointProfile(Kind kind, Direction axis, double minimumPosition, double maximumPosition,
            double maximumVelocity, double maximumEffort, double viscousFriction,
            double movingInertiaOrMass, double maximumStopReaction) {
        if (kind == null || axis == null || !finite(minimumPosition) || !finite(maximumPosition)
                || minimumPosition >= maximumPosition || !positive(maximumVelocity)
                || !positive(maximumEffort) || !positive(viscousFriction)
                || !positive(movingInertiaOrMass) || !positive(maximumStopReaction)
                || maximumStopReaction < maximumEffort)
            throw new IllegalArgumentException("joint profile");
        if (kind == Kind.REVOLUTE && maximumPosition - minimumPosition > StrictMath.PI * 2.0 + 1.0e-12)
            throw new IllegalArgumentException("revolute range");
        if (kind == Kind.PRISMATIC && maximumPosition - minimumPosition > 4.0 + 1.0e-12)
            throw new IllegalArgumentException("prismatic range");
        this.kind = kind; this.axis = axis; this.minimumPosition = minimumPosition;
        this.maximumPosition = maximumPosition; this.maximumVelocity = maximumVelocity;
        this.maximumEffort = maximumEffort; this.viscousFriction = viscousFriction;
        this.movingInertiaOrMass = movingInertiaOrMass; this.maximumStopReaction = maximumStopReaction;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static boolean positive(double value) { return finite(value) && value > 0.0; }
}
