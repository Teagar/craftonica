package br.com.craftonica.electrical;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedStateTest {
    @Test
    public void nominalCurrentProducesProportionalBrightness() {
        LedState state = new LedState();

        state.update(result(CircuitStatus.CLOSED, 0.013636));

        assertEquals(11, state.getBrightness());
        assertFalse(state.isBurned());
    }

    @Test
    public void burnsOnlyOnTwentiethContinuousOvercurrentTick() {
        LedState state = new LedState();
        for (int tick = 0; tick < 19; tick++) {
            state.update(result(CircuitStatus.OVERCURRENT, 0.050));
        }
        assertFalse(state.isBurned());
        assertEquals(19, state.getOvercurrentTicks());

        state.update(result(CircuitStatus.OVERCURRENT, 0.050));

        assertTrue(state.isBurned());
        assertEquals(0, state.getBrightness());
    }

    @Test
    public void safeTickResetsOvercurrentCounter() {
        LedState state = new LedState();
        for (int tick = 0; tick < 19; tick++) {
            state.update(result(CircuitStatus.OVERCURRENT, 0.050));
        }

        state.update(result(CircuitStatus.OPEN_CIRCUIT, 0.0));

        assertEquals(0, state.getOvercurrentTicks());
        assertFalse(state.isBurned());
    }

    @Test
    public void burnedStateCannotLightAgain() {
        LedState state = new LedState();
        state.setBurned(true);

        state.update(result(CircuitStatus.CLOSED, 0.013636));

        assertTrue(state.isBurned());
        assertEquals(0, state.getBrightness());
    }

    private CircuitResult result(CircuitStatus status, double current) {
        return new CircuitResult(status, 5.0, current, 220.0, "test");
    }
}
