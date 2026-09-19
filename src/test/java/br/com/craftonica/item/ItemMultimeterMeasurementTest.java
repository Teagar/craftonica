package br.com.craftonica.item;

import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import br.com.craftonica.electrical.MultimeterMode;
import br.com.craftonica.electrical.nodal.ResistanceMeasurement;
import org.junit.Test;

import static org.junit.Assert.*;

public class ItemMultimeterMeasurementTest {
    private static final CircuitResult CLOSED = new CircuitResult(CircuitStatus.CLOSED, 5.0, 0.01, 220.0, "test");

    @Test public void voltagePreservesProbeOrder() {
        assertEquals(3.0, ItemMultimeter.measurement(MultimeterMode.VOLTAGE, 5.0, 2.0, null, CLOSED).getSourceVoltage(), 0.0);
        assertEquals(-3.0, ItemMultimeter.measurement(MultimeterMode.VOLTAGE, 2.0, 5.0, null, CLOSED).getSourceVoltage(), 0.0);
    }

    @Test public void resistanceAndContinuityUseAuxiliaryResult() {
        CircuitResult continuous = ItemMultimeter.resistanceMeasurement(ResistanceMeasurement.valid(10.0));
        assertEquals(CircuitStatus.CLOSED, continuous.getStatus());
        assertEquals(10.0, continuous.getEquivalentResistanceOhms(), 0.0);
        assertEquals(CircuitStatus.OPEN_CIRCUIT,
                ItemMultimeter.resistanceMeasurement(ResistanceMeasurement.valid(10.000001)).getStatus());
        assertTrue(Double.isInfinite(ItemMultimeter.resistanceMeasurement(ResistanceMeasurement.openCircuit())
                .getEquivalentResistanceOhms()));
    }
}
