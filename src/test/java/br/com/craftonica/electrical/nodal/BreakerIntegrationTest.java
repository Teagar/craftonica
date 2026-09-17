package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Integration at the Forge-independent boundary: snapshot state drives the nodal branch. */
public class BreakerIntegrationTest {
    @Test public void closedBreakerIsAConductiveProtectionBranch() {
        List<TerminalSnapshot> terminals = Arrays.asList(
                new TerminalSnapshot(new BlockPosition(0, 0, 0), Face.WEST, 0),
                new TerminalSnapshot(new BlockPosition(0, 0, 0), Face.EAST, 1));
        Map<String, String> state = new HashMap<String, String>();
        state.put("closed", "true");
        ComponentSnapshot snapshot = new ComponentSnapshot(new BlockPosition(0, 0, 0), "breaker", terminals,
                Collections.<String, Double>emptyMap(), state, Collections.<int[]>emptyList());
        assertEquals("true", snapshot.getState().get("closed"));
        MnaSystem.Builder system = MnaSystem.builder();
        system.breaker(new BranchId(new BlockPosition(0, 0, 0), "breaker", 0),
                NodeId.named("out"), NodeId.REFERENCE, true);
        assertEquals(MnaSystem.Element.Kind.BREAKER, system.build().getElements().get(0).getKind());
    }
}
