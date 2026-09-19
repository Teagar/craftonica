package br.com.craftonica.tile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SamplingBudgetTest {
    @Test
    public void eachTileSamplesOncePerFourTicks() {
        int sensorSamples = 0;
        int actuatorSamples = 0;
        for (long tick = 0; tick < 40; tick++) {
            if (TileEntityAnalogSensor.isSampleTick(tick, 13, 7, -4)) sensorSamples++;
            if (TileEntityEducationalActuator.isSampleTick(tick, 13, 7, -4)) actuatorSamples++;
        }
        assertEquals(10, sensorSamples);
        assertEquals(10, actuatorSamples);
    }

    @Test
    public void adjacentTilesAreDistributedAcrossTickPhases() {
        int[] samples = new int[TileEntityAnalogSensor.SAMPLE_INTERVAL_TICKS];
        for (int x = 0; x < 64; x++) {
            for (int tick = 0; tick < samples.length; tick++) {
                if (TileEntityAnalogSensor.isSampleTick(tick, x, 0, 0)) samples[tick]++;
            }
        }
        for (int count : samples) assertEquals(16, count);
    }
}
