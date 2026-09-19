package br.com.craftonica.network;

import br.com.craftonica.electrical.nodal.NodalCircuit;
import br.com.craftonica.electrical.nodal.NodalCircuitBuilder;
import br.com.craftonica.electrical.nodal.NodalCircuitResult;
import br.com.craftonica.electrical.nodal.ComponentSnapshot;
import br.com.craftonica.electrical.nodal.Face;
import br.com.craftonica.electrical.nodal.TerminalSnapshot;
import br.com.craftonica.electrical.CircuitResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Contract tests for the deterministic policy used by the Forge manager. */
public class ElectricalNetworkManagerPolicyTest {
    @Test public void exposesTheRequiredPerTickBudgets() {
        assertEquals(256, br.com.craftonica.electrical.nodal.NodalLimits.MAX_UNKNOWNS);
        assertEquals(512, ElectricalNetworkManager.MAX_UNKNOWNS_PER_TICK_FOR_TEST);
        assertEquals(4, ElectricalNetworkManager.MAX_NETWORKS_PER_TICK_FOR_TEST);
    }

    @Test public void nodalPreparationIsIndependentOfSnapshotInsertionOrder() {
        List<ComponentSnapshot> snapshots = new ArrayList<ComponentSnapshot>();
        snapshots.add(component(3, "ground"));
        snapshots.add(component(1, "resistor"));
        snapshots.add(component(2, "wire"));
        NodalCircuit first = build(snapshots);
        Collections.reverse(snapshots);
        NodalCircuit second = build(snapshots);
        assertEquals(first.getTopologyFingerprint(), second.getTopologyFingerprint());
        assertEquals(first.getBranches().size(), second.getBranches().size());
        assertTrue(first.getNodes().size() > 0);
    }

    @Test public void networkCacheOrdersBlockPositionsDuringConstruction() throws Exception {
        Constructor<?> constructor = Class.forName("br.com.craftonica.network.ElectricalNetworkManager$NetworkCache")
                .getDeclaredConstructor(long.class, Set.class, NodalCircuitResult.class,
                        CircuitResult.class, NodalCircuit.class, br.com.craftonica.electrical.nodal.MnaSystem.class, Set.class);
        constructor.setAccessible(true);
        Object cache = constructor.newInstance(1L, new HashSet<BlockPosition>(Arrays.asList(
                new BlockPosition(2, 0, 0), new BlockPosition(1, 5, 0),
                new BlockPosition(1, 4, 9))), null, null, null, null, Collections.emptySet());
        Field members = cache.getClass().getDeclaredField("members");
        members.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<BlockPosition> ordered = (Set<BlockPosition>) members.get(cache);

        assertEquals(Arrays.asList(new BlockPosition(1, 4, 9), new BlockPosition(1, 5, 0),
                new BlockPosition(2, 0, 0)), new ArrayList<BlockPosition>(ordered));
    }

    private NodalCircuit build(List<ComponentSnapshot> snapshots) {
        NodalCircuitBuilder builder = new NodalCircuitBuilder();
        for (ComponentSnapshot snapshot : snapshots) builder.add(snapshot);
        return builder.build();
    }

    private ComponentSnapshot component(int x, String kind) {
        List<TerminalSnapshot> terminals = new ArrayList<TerminalSnapshot>();
        terminals.add(new TerminalSnapshot(new BlockPosition(x, 0, 0), Face.WEST, 0));
        terminals.add(new TerminalSnapshot(new BlockPosition(x, 0, 0), Face.EAST, 1));
        return new ComponentSnapshot(new BlockPosition(x, 0, 0), kind, terminals,
                Collections.<String, Double>emptyMap(), Collections.<String, String>emptyMap(),
                "wire".equals(kind) ? Collections.singletonList(new int[]{0, 1}) : Collections.<int[]>emptyList());
    }
}
