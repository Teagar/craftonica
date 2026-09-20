package br.com.craftonica.sensor;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.Assert.*;

public final class UltrasonicMetrologyDatasetTest {
    @Test public void completeProtocolProducesDistanceAndRegressionMetrics() {
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        dataset.merge(completeCsv().getBytes(StandardCharsets.US_ASCII));
        assertEquals(400, dataset.size());
        assertEquals(0, dataset.missingCount());
        assertTrue(dataset.isComplete());
        String metrics = dataset.renderMetricsCsv();
        assertTrue(metrics.contains("distance,MDF,50.00,10,10,0,50.100,0.100,0.000,NA,NA,NA"));
        assertTrue(metrics.contains("material,MDF,NA,100,100,0,27.600,0.100,14.434,1.000000,0.100000,1.000000"));
        assertTrue(metrics.contains("material,ESPUMA,NA,100,80,20,"));
        assertTrue(metrics.contains(",1.100000,0.000000,1.000000"));
    }

    @Test public void repeatedKeyReplacesInsteadOfDuplicating() {
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        dataset.merge("material,nominal_cm,amostra,medida_cm,eco\nMDF,5.00,1,5.100,1\n"
                .getBytes(StandardCharsets.US_ASCII));
        dataset.merge("MDF,5.00,1,NA,0\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(1, dataset.size());
        assertTrue(dataset.renderRawCsv().contains("MDF,5.00,1,NA,0"));
        assertEquals(399, dataset.missingCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void timeoutCannotMasqueradeAsZeroDistance() {
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        dataset.merge("MDF,5.00,1,0.000,0\n".getBytes(StandardCharsets.US_ASCII));
    }

    @Test(expected = IllegalArgumentException.class)
    public void outOfProtocolNominalIsRejected() {
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        dataset.merge("MDF,7.00,1,7.100,1\n".getBytes(StandardCharsets.US_ASCII));
    }

    @Test public void deterministicFourMaterialProtocolRetainsExpectedMetrologyOrdering() {
        AcousticMaterialProfile[] profiles = { AcousticMaterialProfile.MDF,
                AcousticMaterialProfile.RIGID_PLASTIC, AcousticMaterialProfile.STYROFOAM,
                AcousticMaterialProfile.FOAM };
        String[] names = { "MDF", "PLASTICO", "ISOPOR", "ESPUMA" };
        StringBuilder csv = new StringBuilder("material,nominal_cm,amostra,medida_cm,eco\n");
        for (int material = 0; material < profiles.length; material++)
            for (int nominal = 5; nominal <= 50; nominal += 5)
                for (int sample = 1; sample <= 10; sample++) {
                    UltrasonicMeasurementModel.Measurement measured = UltrasonicMeasurementModel.measure(
                            nominal, 0.0, profiles[material], material * 100000L + nominal * 100L + sample);
                    csv.append(names[material]).append(',').append(nominal).append(".00,")
                            .append(sample).append(',');
                    if (measured.echo) csv.append(String.format(Locale.ROOT, "%.3f,1\n", measured.measuredCentimeters));
                    else csv.append("NA,0\n");
                }
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        dataset.merge(csv.toString().getBytes(StandardCharsets.US_ASCII));
        assertTrue(dataset.isComplete());
        double[] mdf = materialMetrics(dataset.renderMetricsCsv(), "MDF");
        double[] plastic = materialMetrics(dataset.renderMetricsCsv(), "PLASTICO");
        double[] styrofoam = materialMetrics(dataset.renderMetricsCsv(), "ISOPOR");
        double[] foam = materialMetrics(dataset.renderMetricsCsv(), "ESPUMA");
        assertTrue(mdf[0] < styrofoam[0]);
        assertTrue(plastic[0] < styrofoam[0]);
        assertTrue(styrofoam[0] < foam[0]);
        assertTrue(mdf[1] > foam[1]);
        assertTrue(plastic[1] > foam[1]);
        assertTrue(foam[2] > mdf[2]);
    }

    private double[] materialMetrics(String csv, String material) {
        for (String line : csv.split("\n")) if (line.startsWith("material," + material + ",")) {
            String[] fields = line.split(",");
            return new double[] { Double.parseDouble(fields[7]), Double.parseDouble(fields[11]),
                    Double.parseDouble(fields[5]) };
        }
        fail("missing material metrics: " + material);
        return null;
    }

    private String completeCsv() {
        String[] materials = { "MDF", "PLASTICO", "ISOPOR", "ESPUMA" };
        StringBuilder csv = new StringBuilder("material,nominal_cm,amostra,medida_cm,eco\n");
        for (String material : materials) for (int nominal = 5; nominal <= 50; nominal += 5)
            for (int sample = 1; sample <= 10; sample++) {
                csv.append(material).append(',').append(String.format(Locale.ROOT, "%.2f", (double) nominal))
                        .append(',').append(sample).append(',');
                if ("ESPUMA".equals(material) && sample > 8) csv.append("NA,0\n");
                else {
                    double measured = "ESPUMA".equals(material) ? nominal * 1.1
                            : nominal + ("MDF".equals(material) ? 0.1 : 0.2);
                    csv.append(String.format(Locale.ROOT, "%.3f", measured)).append(",1\n");
                }
            }
        return csv.toString();
    }
}
