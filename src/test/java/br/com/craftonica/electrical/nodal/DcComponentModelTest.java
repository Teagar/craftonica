package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;

import static org.junit.Assert.*;

public class DcComponentModelTest {
    private static final NodeId OUT = NodeId.named("out");
    private static final NodeId INTERNAL = NodeId.named("internal");
    private static final NodeId ANODE = NodeId.named("anode");

    private BranchId id(String kind, int ordinal) { return new BranchId(new BlockPosition(ordinal, 0, 0), kind, 0); }

    private MnaSystem.Builder base() {
        return MnaSystem.builder().thevenin(id("source", 0), OUT, INTERNAL, 5.0, 10.0);
    }

    @Test public void directLedIncludesTheveninAndDynamicResistance() {
        MnaSystem.Builder builder = base();
        builder.resistor(id("resistor", 1), OUT, ANODE, 220.0).led(id("led", 2), ANODE, NodeId.REFERENCE);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(0.012987012987, result.getBranchResult(id("led", 2)).getCurrent(), 1e-12);
    }

    @Test public void reverseLedIsOffAndDiagnosed() {
        MnaSystem.Builder builder = base();
        builder.resistor(id("resistor", 1), OUT, ANODE, 220.0).led(id("led", 2), NodeId.REFERENCE, ANODE);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(0.0, result.getBranchResult(id("led", 2)).getCurrent(), 0.0);
        assertEquals(DiagnosticCode.POLARITY_INCORRECT, result.getDiagnostics().get(0).getCode());
    }

    @Test public void parallelLedsHaveIndependentBranchCurrents() {
        NodeId a = NodeId.named("a");
        NodeId b = NodeId.named("b");
        MnaSystem.Builder builder = base();
        builder.resistor(id("ra", 1), OUT, a, 220.0).led(id("la", 2), a, NodeId.REFERENCE);
        builder.resistor(id("rb", 3), OUT, b, 1000.0).led(id("lb", 4), b, NodeId.REFERENCE);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(0.012864064153, result.getBranchResult(id("la", 2)).getCurrent(), 1e-12);
        assertEquals(0.002840118060, result.getBranchResult(id("lb", 4)).getCurrent(), 1e-10);
    }

    @Test public void burnedLedIsAnOpenBranch() {
        MnaSystem.Builder builder = base();
        builder.resistor(id("resistor", 1), OUT, ANODE, 220.0).led(id("led", 2), ANODE, NodeId.REFERENCE, true);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(0.0, result.getBranchResult(id("led", 2)).getCurrent(), 0.0);
    }

    @Test public void openSwitchDoesNotConductAndClosedSwitchUsesContactResistance() {
        MnaSystem.Builder open = base();
        open.switchBranch(id("open", 1), OUT, NodeId.REFERENCE, false);
        assertEquals(0.0, new DcNodalSolver().solve(open.build()).getBranchResult(id("open", 1)).getCurrent(), 0.0);
        MnaSystem.Builder closed = base();
        closed.switchBranch(id("closed", 1), OUT, NodeId.REFERENCE, true);
        assertEquals(5.0 / 10.01, new DcNodalSolver().solve(closed.build()).getBranchResult(id("closed", 1)).getCurrent(), 1e-12);
    }

    @Test public void nonlinearLimitIsHardAndDeterministic() {
        MnaSystem.Builder builder = base();
        for (int i = 0; i < NodalLimits.MAX_ACTIVE_LEDS + 1; i++)
            builder.led(id("led" + i, i + 1), NodeId.REFERENCE, OUT);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertEquals(SolveStatus.NONLINEAR_LIMIT, result.getStatus());
    }

    @Test public void diodeDoesNotReceiveLedGameplayDiagnostics() {
        MnaSystem.Builder builder = base();
        builder.diode(id("diode", 1), OUT, NodeId.REFERENCE, 0.7, 1.0);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        for (CircuitDiagnostic diagnostic : result.getDiagnostics()) {
            assertNotEquals(DiagnosticCode.LED_ABOVE_RECOMMENDED_CURRENT, diagnostic.getCode());
            assertNotEquals(DiagnosticCode.LED_OVERCURRENT, diagnostic.getCode());
        }
    }
}
