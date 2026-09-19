package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiodeAndSwitchModelTest {
    private BranchId id(String kind, int x) { return new BranchId(new BlockPosition(x, 0, 0), kind, 0); }

    private MnaSystem.Builder circuit(boolean closed, boolean forward) {
        NodeId out = NodeId.named("out");
        NodeId diodeNode = NodeId.named("diode");
        MnaSystem.Builder b = MnaSystem.builder();
        b.powerSource(id("source", 0), out, NodeId.named("internal"));
        b.switchBranch(id("lever", 1), out, diodeNode, closed);
        b.resistor(id("resistor", 2), diodeNode, NodeId.named("led"), 220.0);
        if (forward) b.diode(id("diode", 3), NodeId.named("led"), NodeId.REFERENCE, 2.0, 1.0);
        else b.diode(id("diode", 3), NodeId.REFERENCE, NodeId.named("led"), 2.0, 1.0);
        return b;
    }

    @Test public void directDiodeConductsAndReverseDoesNot() {
        NodalCircuitResult direct = new DcNodalSolver().solve(circuit(true, true).build());
        NodalCircuitResult reverse = new DcNodalSolver().solve(circuit(true, false).build());
        assertTrue(direct.isSolved());
        assertEquals(3.0 / 231.01, direct.getBranchResult(id("diode", 3)).getCurrent(), 1e-12);
        assertEquals(0.0, reverse.getBranchResult(id("diode", 3)).getCurrent(), 0.0);
        assertEquals(DiagnosticCode.POLARITY_INCORRECT, reverse.getDiagnostics().get(0).getCode());
    }

    @Test public void openLeverIsDeterministicAndZeroCurrent() {
        MnaSystem system = circuit(false, true).build();
        NodalCircuitResult first = new DcNodalSolver().solve(system);
        NodalCircuitResult second = new DcNodalSolver().solve(system);
        assertEquals(first.getStatus(), second.getStatus());
        assertEquals(first.getBranchResults().toString(), second.getBranchResults().toString());
    }
}
