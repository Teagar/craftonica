package br.com.craftonica.network;

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
}
