package br.com.craftonica.network;

import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FeedbackThrottleTest {
    @After
    public void resetThrottle() {
        FeedbackThrottle.clearForTests();
    }

    @Test
    public void suppressesRepeatedEventsWithinCooldown() {
        assertTrue(FeedbackThrottle.allow("wire", 100L, 8));
        assertFalse(FeedbackThrottle.allow("wire", 107L, 8));
        assertTrue(FeedbackThrottle.allow("wire", 108L, 8));
    }

    @Test
    public void keepsIndependentNetworksIndependent() {
        assertTrue(FeedbackThrottle.allow("a", 10L, 8));
        assertTrue(FeedbackThrottle.allow("b", 10L, 8));
    }

    @Test
    public void nullAndRepeatedResultsDoNotEmitTransitions() {
        CircuitResult closed = new CircuitResult(CircuitStatus.CLOSED, 5.0, 0.01, 500.0, "test");
        CircuitResult open = new CircuitResult(CircuitStatus.OPEN_CIRCUIT, 5.0, 0.0,
                Double.POSITIVE_INFINITY, "test");
        assertFalse(ElectricalFeedback.isTransition(null, null));
        assertFalse(ElectricalFeedback.isTransition(closed, null));
        assertFalse(ElectricalFeedback.isTransition(closed, closed));
        assertTrue(ElectricalFeedback.isTransition(null, closed));
        assertTrue(ElectricalFeedback.isTransition(open, closed));
    }
}
