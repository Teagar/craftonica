package br.com.craftonica.sensor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UltrasonicMeasurementModelTest {
    @Test public void rigidTargetsAreMoreAccurateAndRepeatableThanFoam() {
        List<Double> rigid = samples(AcousticMaterialProfile.MDF, 50.0, 0.0);
        List<Double> foam = samples(AcousticMaterialProfile.FOAM, 50.0, 0.0);
        assertTrue(rigid.size() >= 95);
        assertTrue(foam.size() >= 50);
        assertTrue(Math.abs(mean(rigid) - 50.0) < 0.5);
        assertTrue(mean(foam) - 50.0 > 2.0);
        assertTrue(deviation(foam) > deviation(rigid) * 4.0);
    }

    @Test public void incidenceDegradesConfidenceAndCanLoseEchoes() {
        int normal = echoes(AcousticMaterialProfile.FOAM, 50.0, 0.0);
        int angled = echoes(AcousticMaterialProfile.FOAM, 50.0, 60.0);
        assertTrue(normal > angled);
    }

    @Test public void convertsCentimetersToAvrEchoCycles() {
        UltrasonicMeasurementModel.Measurement measurement = UltrasonicMeasurementModel.measure(
                25.0, 0.0, AcousticMaterialProfile.RIGID_PLASTIC, 41L);
        assertTrue(measurement.echo);
        assertEquals(Math.round(measurement.measuredCentimeters * 58.0 * 16.0), measurement.echoCycles);
        assertFalse(UltrasonicMeasurementModel.measure(1.0, 0.0,
                AcousticMaterialProfile.MDF, 1L).echo);
    }

    @Test public void smallerApparentTargetsLoseMoreEchoes() {
        int full = echoes(AcousticMaterialProfile.RIGID_WORLD, 200.0, 0.0, 1.0);
        int medium = echoes(AcousticMaterialProfile.RIGID_WORLD, 200.0, 0.0, 0.50);
        int small = echoes(AcousticMaterialProfile.RIGID_WORLD, 200.0, 0.0, 0.20);
        assertTrue(full > medium);
        assertTrue(medium > small);
    }

    private List<Double> samples(AcousticMaterialProfile profile, double cm, double angle) {
        List<Double> values = new ArrayList<Double>();
        for (long seed = 0; seed < 100; seed++) {
            UltrasonicMeasurementModel.Measurement value = UltrasonicMeasurementModel.measure(cm, angle, profile, seed);
            if (value.echo) values.add(value.measuredCentimeters);
        }
        return values;
    }

    private int echoes(AcousticMaterialProfile profile, double cm, double angle) {
        return samples(profile, cm, angle).size();
    }

    private int echoes(AcousticMaterialProfile profile, double cm, double angle, double coverage) {
        int count = 0;
        for (long seed = 0; seed < 1000; seed++) {
            if (UltrasonicMeasurementModel.measure(cm, angle, profile, coverage, seed).echo) count++;
        }
        return count;
    }

    private double mean(List<Double> values) {
        double sum = 0.0; for (double value : values) sum += value; return sum / values.size();
    }

    private double deviation(List<Double> values) {
        double mean = mean(values), sum = 0.0;
        for (double value : values) { double delta = value - mean; sum += delta * delta; }
        return StrictMath.sqrt(sum / (values.size() - 1));
    }
}
