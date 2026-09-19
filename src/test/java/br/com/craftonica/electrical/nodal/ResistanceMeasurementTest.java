package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;

import static org.junit.Assert.*;

public class ResistanceMeasurementTest {
    private static final NodeId OUT = NodeId.named("out");
    private static final NodeId INTERNAL = NodeId.named("internal");

    @Test public void zerosIndependentSourcesAndUsesOneVoltTestSource() {
        MnaSystem energized = MnaSystem.builder()
                .thevenin(id("source", 0), OUT, INTERNAL, 5.0, 10.0)
                .resistor(id("load", 1), OUT, NodeId.REFERENCE, 220.0)
                .build();
        BranchId test = id("measurement_test", 2);
        MnaSystem auxiliary = energized.deenergizedWithTestSource(test, OUT, NodeId.REFERENCE);
        NodalCircuitResult result = new DcNodalSolver().solve(auxiliary);
        assertTrue(result.isSolved());
        double expected = 1.0 / (1.0 / 10.0 + 1.0 / 220.0);
        assertEquals(expected, 1.0 / Math.abs(result.getBranchResult(test).getCurrent()), 1e-9);
    }

    @Test public void rejectsLedAndDiodeNetworksExplicitly() {
        MnaSystem led = MnaSystem.builder().led(id("led", 0), OUT, NodeId.REFERENCE).build();
        MnaSystem diode = MnaSystem.builder().diode(id("diode", 0), OUT, NodeId.REFERENCE).build();
        assertTrue(led.hasNonlinearElements());
        assertTrue(diode.hasNonlinearElements());
        assertNull(led.deenergizedWithTestSource(id("test", 1), OUT, NodeId.REFERENCE));
        assertNull(diode.deenergizedWithTestSource(id("test", 1), OUT, NodeId.REFERENCE));
    }

    @Test public void preservesClosedSwitchResistanceAndOpenState() {
        BranchId test = id("test", 2);
        MnaSystem closed = MnaSystem.builder().switchBranch(id("switch", 0), OUT, NodeId.REFERENCE, true).build()
                .deenergizedWithTestSource(test, OUT, NodeId.REFERENCE);
        NodalCircuitResult closedResult = new DcNodalSolver().solve(closed);
        assertEquals(0.01, 1.0 / Math.abs(closedResult.getBranchResult(test).getCurrent()), 1e-12);
        MnaSystem open = MnaSystem.builder().switchBranch(id("switch", 0), OUT, NodeId.REFERENCE, false).build()
                .deenergizedWithTestSource(test, OUT, NodeId.REFERENCE);
        assertEquals(0.0, new DcNodalSolver().solve(open).getBranchResult(id("switch", 0)).getCurrent(), 0.0);
    }

    private static BranchId id(String kind, int x) {
        return new BranchId(new BlockPosition(x, 0, 0), kind, 0);
    }
}
