package br.com.craftonica.electrical;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MultimeterReadingTest {
    @Test
    public void cyclesThroughEveryMode() {
        assertEquals(MultimeterMode.CURRENT, MultimeterMode.VOLTAGE.next());
        assertEquals(MultimeterMode.RESISTANCE, MultimeterMode.CURRENT.next());
        assertEquals(MultimeterMode.CONTINUITY, MultimeterMode.RESISTANCE.next());
        assertEquals(MultimeterMode.VOLTAGE, MultimeterMode.CONTINUITY.next());
        assertEquals(MultimeterMode.RESISTANCE, MultimeterMode.fromId("resistance"));
        assertEquals(MultimeterMode.VOLTAGE, MultimeterMode.fromId("unknown"));
    }

    @Test
    public void convertsSolverValuesWithoutRecalculatingPhysics() {
        CircuitResult result = result(CircuitStatus.CLOSED, 5.0, 0.013636, 220.0);

        assertReading(MultimeterReading.Kind.VALUE, 5.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.VOLTAGE, result));
        assertReading(MultimeterReading.Kind.VALUE, 13.636,
                MultimeterReading.fromNetworkResult(MultimeterMode.CURRENT, result));
        assertReading(MultimeterReading.Kind.VALUE, 220.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.RESISTANCE, result));
        assertReading(MultimeterReading.Kind.CONTINUITY, 0.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.CONTINUITY, result));
    }

    @Test
    public void distinguishesOpenUnsafeAndUnsupportedReadings() {
        assertReading(MultimeterReading.Kind.NO_CONTINUITY, 0.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.CONTINUITY,
                        result(CircuitStatus.OPEN_CIRCUIT, 5.0, 0.0, 220.0)));
        assertReading(MultimeterReading.Kind.OVERCURRENT, 0.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.CURRENT,
                        result(CircuitStatus.OVERCURRENT, 5.0, Double.POSITIVE_INFINITY, 0.0)));
        assertReading(MultimeterReading.Kind.UNSUPPORTED, 0.0,
                MultimeterReading.fromNetworkResult(MultimeterMode.VOLTAGE,
                        result(CircuitStatus.UNSUPPORTED_TOPOLOGY, 0.0, 0.0, 0.0)));
    }

    private CircuitResult result(CircuitStatus status, double voltage, double current, double resistance) {
        return new CircuitResult(status, voltage, current, resistance, "test");
    }

    private void assertReading(MultimeterReading.Kind kind, double value, MultimeterReading reading) {
        assertEquals(kind, reading.getKind());
        assertEquals(value, reading.getValue(), 0.000001);
    }
}
