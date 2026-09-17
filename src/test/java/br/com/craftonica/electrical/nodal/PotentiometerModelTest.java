package br.com.craftonica.electrical.nodal;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.electrical.nodal.Face;
import java.util.Arrays;
import java.util.HashMap;

public class PotentiometerModelTest {
    @Test public void extremesKeepNominalSumAndPositiveBranches() {
        for (int step : new int[]{0, 50, 100}) {
            double a = PotentiometerModel.resistanceA(step);
            double b = PotentiometerModel.resistanceB(step);
            assertEquals(10000.0, a + b, 0.0000001);
            assertTrue(a >= PotentiometerModel.MIN_BRANCH_OHMS);
            assertTrue(b >= PotentiometerModel.MIN_BRANCH_OHMS);
        }
    }

    @Test public void centerIsSymmetricAndLimitsAreClamped() {
        assertEquals(5000.0, PotentiometerModel.resistanceA(50), 0.0000001);
        assertEquals(5000.0, PotentiometerModel.resistanceB(50), 0.0000001);
        assertEquals(PotentiometerModel.resistanceA(0), PotentiometerModel.resistanceA(-4), 0.0);
        assertEquals(PotentiometerModel.resistanceB(100), PotentiometerModel.resistanceB(104), 0.0);
    }

    @Test public void cursorMovesVoltageDividerInExpectedDirection() {
        double low = 5.0 * PotentiometerModel.resistanceB(0) / 10000.0;
        double high = 5.0 * PotentiometerModel.resistanceB(100) / 10000.0;
        assertTrue(low > high);
    }

    @Test public void snapshotProducesTwoDeterministicNodalBranches() {
        BlockPosition position = new BlockPosition(1, 2, 3);
        ComponentSnapshot snapshot = new ComponentSnapshot(position, "potentiometer", Arrays.asList(
                new TerminalSnapshot(position, Face.WEST, 0),
                new TerminalSnapshot(position, Face.UP, 1),
                new TerminalSnapshot(position, Face.EAST, 2)),
                new HashMap<String, Double>(), new HashMap<String, String>(),
                java.util.Collections.<int[]>emptyList());
        NodalCircuit circuit = new NodalCircuitBuilder().add(snapshot).build();
        assertEquals(2, circuit.getBranches().size());
        assertEquals(0, circuit.getBranches().get(0).getId().getOrdinal());
        assertEquals(1, circuit.getBranches().get(1).getId().getOrdinal());
    }
}
