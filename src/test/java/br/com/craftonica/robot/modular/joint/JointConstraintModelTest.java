package br.com.craftonica.robot.modular.joint;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.JointProfile;
import org.junit.Test;

import static org.junit.Assert.*;

public final class JointConstraintModelTest {
    private final JointProfile profile = new JointProfile(JointProfile.Kind.REVOLUTE, Direction.EAST,
            -1.0, 1.0, 4.0, 2.0, 0.1, 0.5, 8.0);

    @Test public void torqueAcceleratesInsteadOfTeleportingCoordinate() {
        JointStep step = JointConstraintModel.step(profile, new JointState(0.0, 0.0),
                1.0, 0.0, 0.025);
        assertEquals(0.05, step.state.velocity, 1.0e-12);
        assertEquals(0.00125, step.state.position, 1.0e-12);
        assertEquals(JointStep.Diagnostic.NONE, step.diagnostic);
    }

    @Test public void hardStopsDoNotPenetrateAndProduceBoundedFiniteReaction() {
        JointStep upper = JointConstraintModel.step(profile, new JointState(0.999, 1.0),
                2.0, 0.0, 0.025);
        assertEquals(1.0, upper.state.position, 0.0);
        assertEquals(0.0, upper.state.velocity, 0.0);
        assertEquals(JointStep.Diagnostic.UPPER_HARD_STOP, upper.diagnostic);
        assertTrue(Double.isFinite(upper.stopReaction));
        assertTrue(StrictMath.abs(upper.stopReaction) <= profile.maximumStopReaction);

        JointStep lower = JointConstraintModel.step(profile, new JointState(-1.0, 0.0),
                -2.0, 0.0, 0.025);
        assertEquals(-1.0, lower.state.position, 0.0);
        assertEquals(0.0, lower.state.velocity, 0.0);
        assertEquals(JointStep.Diagnostic.LOWER_HARD_STOP, lower.diagnostic);
    }

    @Test public void effortVelocityAndStepBudgetsAreEnforced() {
        JointStep saturated = JointConstraintModel.step(profile, new JointState(0.0, 0.0),
                20.0, 0.0, 0.025);
        assertEquals(profile.maximumEffort, saturated.appliedEffort, 0.0);
        assertEquals(JointStep.Diagnostic.EFFORT_SATURATED, saturated.diagnostic);
        try {
            JointConstraintModel.step(profile, new JointState(0.0, 0.0), 0.0, 0.0, 0.026);
            fail("oversized step must fail");
        } catch (IllegalArgumentException expected) { }
    }
}
