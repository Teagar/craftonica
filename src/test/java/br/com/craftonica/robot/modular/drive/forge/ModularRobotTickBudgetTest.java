package br.com.craftonica.robot.modular.drive.forge;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public final class ModularRobotTickBudgetTest {
    @Test public void saturationSelectsSameLowestUuidsWithoutGrowingAQueue() {
        List<UUID> forward = new ArrayList<UUID>();
        for (int i = 80; i >= 0; i--) forward.add(new UUID(0L, i));
        List<UUID> reverse = new ArrayList<UUID>(forward); Collections.reverse(reverse);
        Set<UUID> a = ModularRobotTickBudget.select(forward, 0L);
        Set<UUID> b = ModularRobotTickBudget.select(reverse, 0L);
        assertEquals(ModularRobotTickBudget.MAX_ROBOTS_PER_DIMENSION_TICK, a.size());
        assertEquals(a, b); assertTrue(a.contains(new UUID(0L, 0L)));
        assertFalse(a.contains(new UUID(0L, 80L)));
        Set<UUID> next = ModularRobotTickBudget.select(forward, 64L);
        assertTrue(next.contains(new UUID(0L, 80L)));
        assertFalse(next.equals(a));
    }
}
