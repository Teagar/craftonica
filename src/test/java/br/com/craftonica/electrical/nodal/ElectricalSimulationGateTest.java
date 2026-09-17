package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Deterministic work-unit gates for the 0.3 dense solver and topology builder. */
public class ElectricalSimulationGateTest {
    @Test public void matrixBoundaryAccepts256AndRejects257BeforeAllocation() {
        assertEquals(SolveStatus.SOLVED, solveGroundedNodes(256).getStatus());
        assertEquals(SolveStatus.MATRIX_LIMIT, solveGroundedNodes(257).getStatus());
    }

    @Test public void oneThousandTwentyFourWiresCollapseIntoOneNode() {
        NodalCircuitBuilder builder = new NodalCircuitBuilder();
        for (int x = 0; x < NodalLimits.MAX_BLOCKS; x++) {
            BlockPosition position = new BlockPosition(x, 0, 0);
            List<TerminalSnapshot> terminals = Arrays.asList(new TerminalSnapshot(position, Face.WEST, 0),
                    new TerminalSnapshot(position, Face.EAST, 1));
            builder.add(new ComponentSnapshot(position, "wire", terminals,
                    Collections.<String, Double>emptyMap(), Collections.<String, String>emptyMap(),
                    Collections.singletonList(new int[]{0, 1})));
        }
        NodalCircuit circuit = builder.build();
        assertEquals(1, circuit.getNodes().size());
        assertEquals(NodalLimits.MAX_BLOCKS * 2, circuit.getTerminalToNode().size());
        assertEquals(DiagnosticCode.MISSING_REFERENCE, circuit.getDiagnostics().get(0).getCode());
    }

    @Test public void rfcReferenceCasesStayInsidePublishedTolerance() {
        NodeId out = NodeId.named("out"), anode = NodeId.named("anode"), internal = NodeId.named("internal");
        BranchId source = id(0, "source"), resistor = id(1, "resistor"), led = id(2, "led");
        MnaSystem.Builder builder = MnaSystem.builder().thevenin(source, out, internal, 5.0, 10.0)
                .resistor(resistor, out, anode, 220.0).led(led, anode, NodeId.REFERENCE);
        NodalCircuitResult result = new DcNodalSolver().solve(builder.build());
        assertTrue(result.isSolved());
        assertEquals(0.012987012987, result.getBranchResult(led).getCurrent(), 1e-9);
        assertEquals(2.012987012987, result.getBranchResult(led).getVoltage(), 1e-9);
        assertTrue(result.getResidual() <= DenseLuSolver.RESIDUAL_TOLERANCE);
    }

    private NodalCircuitResult solveGroundedNodes(int count) {
        MnaSystem.Builder builder = MnaSystem.builder();
        for (int i = 0; i < count; i++) builder.resistor(id(i, "r"), NodeId.named("n" + i), NodeId.REFERENCE, 1000.0);
        return new DcNodalSolver().solve(builder.build());
    }

    private static BranchId id(int x, String kind) {
        return new BranchId(new BlockPosition(x, 0, 0), kind, 0);
    }
}
