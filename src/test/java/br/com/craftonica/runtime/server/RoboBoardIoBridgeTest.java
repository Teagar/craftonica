package br.com.craftonica.runtime.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RoboBoardIoBridgeTest {
    @Test
    public void digitalThresholdsRetainStableValueInIndeterminateBand() {
        RoboBoardIoBridge.Transfer low = RoboBoardIoBridge.transferDigital(1.5, true);
        RoboBoardIoBridge.Transfer high = RoboBoardIoBridge.transferDigital(3.0, false);
        RoboBoardIoBridge.Transfer retainedLow = RoboBoardIoBridge.transferDigital(2.0, false);
        RoboBoardIoBridge.Transfer retainedHigh = RoboBoardIoBridge.transferDigital(2.0, true);

        assertFalse(low.high);
        assertFalse(low.indeterminate);
        assertTrue(high.high);
        assertFalse(high.indeterminate);
        assertFalse(retainedLow.high);
        assertTrue(retainedLow.indeterminate);
        assertTrue(retainedHigh.high);
        assertTrue(retainedHigh.indeterminate);
    }

    @Test
    public void analogSamplesAreRoundedAndClampedToFiveVolts() {
        assertEquals(0, RoboBoardIoBridge.clampMicrovolts(-1.0));
        assertEquals(1234567, RoboBoardIoBridge.clampMicrovolts(1.234567));
        assertEquals(5000000, RoboBoardIoBridge.clampMicrovolts(9.0));
    }

}
