package br.com.craftonica.electrical;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CircuitDiagnosisTest {
    @Test
    public void distinguishesBurnedLedFromOpenSwitch() {
        CircuitResult burned = result(CircuitStatus.OPEN_CIRCUIT, 0.0, "led_burned");
        CircuitResult open = result(CircuitStatus.OPEN_CIRCUIT, 0.0, "switch_open");

        assertEquals("message.craftonica.multimeter.burned", CircuitDiagnosis.translationKey(burned));
        assertEquals("message.craftonica.multimeter.open", CircuitDiagnosis.translationKey(open));
    }

    @Test
    public void hidesInventedMeasurementsForUnsupportedNetworks() {
        assertFalse(CircuitDiagnosis.hasMeasurements(result(CircuitStatus.UNSUPPORTED_TOPOLOGY, 0.0, "branch")));
        assertFalse(CircuitDiagnosis.hasMeasurements(result(CircuitStatus.NETWORK_TOO_LARGE, 0.0, "limit")));
        assertFalse(CircuitDiagnosis.hasMeasurements(result(CircuitStatus.OVERCURRENT,
                Double.POSITIVE_INFINITY, "missing_resistor")));
    }

    @Test
    public void exposesFiniteNominalMeasurements() {
        assertTrue(CircuitDiagnosis.hasMeasurements(result(CircuitStatus.CLOSED, 0.013636, "ok")));
    }

    private CircuitResult result(CircuitStatus status, double current, String detail) {
        return new CircuitResult(status, 5.0, current, 220.0, detail);
    }
}
