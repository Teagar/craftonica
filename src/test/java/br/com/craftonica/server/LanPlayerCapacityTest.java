package br.com.craftonica.server;

import org.junit.Test;

import static org.junit.Assert.*;

public final class LanPlayerCapacityTest {
    @Test public void appliesOnlyToCoudeIntegratedWorld() {
        assertTrue(LanPlayerCapacity.appliesTo(false, "COUDE"));
        assertTrue(LanPlayerCapacity.appliesTo(false, " coude "));
        assertFalse(LanPlayerCapacity.appliesTo(true, "COUDE"));
        assertFalse(LanPlayerCapacity.appliesTo(false, "New World"));
        assertFalse(LanPlayerCapacity.appliesTo(false, null));
    }
}
