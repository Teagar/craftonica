package br.com.craftonica.electrical;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleCircuitSolverTest {
    private final SimpleCircuitSolver solver = new SimpleCircuitSolver();

    @Test
    public void solvesNominalLedCircuit() {
        CircuitResult result = solver.solve(seriesCircuit(true, false, 220.0));

        assertEquals(CircuitStatus.CLOSED, result.getStatus());
        assertEquals(5.0, result.getSourceVoltage(), 0.0001);
        assertEquals(220.0, result.getEquivalentResistanceOhms(), 0.0001);
        assertEquals(0.013636, result.getCurrentAmps(), 0.000001);
    }

    @Test
    public void openSwitchStopsCurrent() {
        CircuitResult result = solver.solve(seriesCircuit(false, false, 220.0));

        assertEquals(CircuitStatus.OPEN_CIRCUIT, result.getStatus());
        assertEquals(0.0, result.getCurrentAmps(), 0.0);
    }

    @Test
    public void reversedLedStopsCurrent() {
        CircuitResult result = solver.solve(seriesCircuit(true, true, 220.0));

        assertEquals(CircuitStatus.REVERSED_POLARITY, result.getStatus());
        assertEquals(0.0, result.getCurrentAmps(), 0.0);
    }

    @Test
    public void missingResistorIsOvercurrent() {
        CircuitResult result = solver.solve(seriesCircuit(true, false, null));

        assertEquals(CircuitStatus.OVERCURRENT, result.getStatus());
        assertTrue(Double.isInfinite(result.getCurrentAmps()));
    }

    @Test
    public void lowResistanceIsMeasuredOvercurrent() {
        CircuitResult result = solver.solve(seriesCircuit(true, false, 47.0));

        assertEquals(CircuitStatus.OVERCURRENT, result.getStatus());
        assertEquals(0.063829, result.getCurrentAmps(), 0.000001);
    }

    @Test
    public void burnedLedOpensCircuitPermanently() {
        CircuitResult result = solver.solve(seriesCircuit(true, false, 220.0, false));

        assertEquals(CircuitStatus.OPEN_CIRCUIT, result.getStatus());
        assertEquals("led_burned", result.getDetail());
        assertEquals(0.0, result.getCurrentAmps(), 0.0);
    }

    @Test
    public void branchIsUnsupported() {
        CircuitGraph graph = seriesCircuit(true, false, 220.0)
                .add(BasicElectricalComponent.wire("branch"))
                .connect("resistor", "branch");

        assertEquals(CircuitStatus.UNSUPPORTED_TOPOLOGY, solver.solve(graph).getStatus());
    }

    @Test
    public void duplicateSourceIsUnsupported() {
        CircuitGraph graph = seriesCircuit(true, false, 220.0)
                .add(BasicElectricalComponent.source("source2", 5.0))
                .connect("source2", "wire");

        assertEquals(CircuitStatus.UNSUPPORTED_TOPOLOGY, solver.solve(graph).getStatus());
    }

    @Test
    public void duplicateGroundIsUnsupported() {
        CircuitGraph graph = seriesCircuit(true, false, 220.0)
                .add(BasicElectricalComponent.ground("ground2"))
                .connect("led", "ground2");

        assertEquals(CircuitStatus.UNSUPPORTED_TOPOLOGY, solver.solve(graph).getStatus());
    }

    @Test
    public void oversizedNetworkFailsBeforeTraversal() {
        CircuitGraph graph = new CircuitGraph();
        for (int index = 0; index <= SimpleCircuitSolver.MAX_NETWORK_SIZE; index++) {
            graph.add(BasicElectricalComponent.wire("wire-" + index));
        }

        assertEquals(CircuitStatus.NETWORK_TOO_LARGE, solver.solve(graph).getStatus());
    }

    private CircuitGraph seriesCircuit(boolean switchClosed, boolean reversedLed, Double resistance) {
        return seriesCircuit(switchClosed, reversedLed, resistance, true);
    }

    private CircuitGraph seriesCircuit(boolean switchClosed, boolean reversedLed, Double resistance,
                                       boolean ledFunctional) {
        CircuitGraph graph = new CircuitGraph()
                .add(BasicElectricalComponent.source("source", 5.0))
                .add(BasicElectricalComponent.wire("wire"))
                .add(BasicElectricalComponent.electricalSwitch("switch", switchClosed));
        graph.connect("source", "wire").connect("wire", "switch");

        String ledInput;
        if (resistance != null) {
            graph.add(BasicElectricalComponent.resistor("resistor", resistance));
            graph.connect("switch", "resistor");
            ledInput = "resistor";
        } else {
            ledInput = "switch";
        }

        String anodeNeighbor = reversedLed ? "ground" : ledInput;
        graph.add(BasicElectricalComponent.led("led", 2.0, anodeNeighbor, ledFunctional))
                .add(BasicElectricalComponent.ground("ground"))
                .connect(ledInput, "led")
                .connect("led", "ground");
        return graph;
    }
}
