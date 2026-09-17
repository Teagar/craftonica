package br.com.craftonica.electrical.nodal;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionModelTest {
    @Test public void nominalCurrentDoesNotTrip() {
        assertFalse(ProtectionModel.mustTrip(0.013636, 0.07));
    }
    @Test public void finiteShortTripsByCurrentOrPower() {
        assertTrue(ProtectionModel.mustTrip(0.5, 0.25));
        assertTrue(ProtectionModel.mustTrip(0.05, 0.6));
    }
    @Test public void stateTripsOnlyWhenPendingIsApplied() {
        ProtectionState state = new ProtectionState();
        state.observe(0.5, 2.5);
        assertFalse(state.isTripped());
        assertTrue(state.isPendingTrip());
        assertTrue(state.applyPendingTrip());
        assertTrue(state.isTripped());
    }
    @Test public void rearmClearsTripAndPending() {
        ProtectionState state = new ProtectionState();
        state.observe(0.5, 2.5);
        state.applyPendingTrip();
        assertTrue(state.reset());
        assertFalse(state.isTripped());
        assertFalse(state.isPendingTrip());
    }
    @Test public void breakerBranchLimitsShortCurrentWithTheveninSource() {
        BranchId source = new BranchId(new br.com.craftonica.network.BlockPosition(0, 0, 0), "source", 0);
        BranchId breaker = new BranchId(new br.com.craftonica.network.BlockPosition(1, 0, 0), "breaker", 0);
        NodeId out = NodeId.named("out");
        MnaSystem.Builder builder = MnaSystem.builder();
        builder.powerSource(source, out, NodeId.named("internal"));
        builder.breaker(breaker, out, NodeId.REFERENCE, true);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(5.0 / 10.01, Math.abs(result.getBranchResult(breaker).getCurrent()), 1e-9);
    }
}
