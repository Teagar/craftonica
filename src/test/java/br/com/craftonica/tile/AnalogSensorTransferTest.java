package br.com.craftonica.tile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnalogSensorTransferTest {
    @Test public void lightTransferIsLinearAndBounded() {
        assertEquals(0, TileEntityAnalogSensor.lightToMicrovolts(-1));
        assertEquals(0, TileEntityAnalogSensor.lightToMicrovolts(0));
        assertEquals(2666667, TileEntityAnalogSensor.lightToMicrovolts(8));
        assertEquals(5000000, TileEntityAnalogSensor.lightToMicrovolts(15));
        assertEquals(5000000, TileEntityAnalogSensor.lightToMicrovolts(16));
    }

    @Test public void temperatureTransferUsesDocumentedRange() {
        assertEquals(0, TileEntityAnalogSensor.temperatureToMicrovolts(-1.0));
        assertEquals(0, TileEntityAnalogSensor.temperatureToMicrovolts(-0.5));
        assertEquals(1000000, TileEntityAnalogSensor.temperatureToMicrovolts(0.0));
        assertEquals(3000000, TileEntityAnalogSensor.temperatureToMicrovolts(1.0));
        assertEquals(5000000, TileEntityAnalogSensor.temperatureToMicrovolts(2.5));
    }

    @Test public void visualLevelIsCoarseAndBounded() {
        assertEquals(0, TileEntityAnalogSensor.coarseLevel(-1));
        assertEquals(7, TileEntityAnalogSensor.coarseLevel(2500000));
        assertEquals(15, TileEntityAnalogSensor.coarseLevel(5000000));
        assertEquals(15, TileEntityAnalogSensor.coarseLevel(6000000));
    }
}
