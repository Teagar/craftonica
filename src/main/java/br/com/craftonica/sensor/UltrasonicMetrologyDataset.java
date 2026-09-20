package br.com.craftonica.sensor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict bounded parser and statistics engine for the 4 x 10 x 10 HC-SR04 protocol. */
public strictfp final class UltrasonicMetrologyDataset {
    public static final int MATERIAL_COUNT = 4;
    public static final int DISTANCE_COUNT = 10;
    public static final int SAMPLES_PER_DISTANCE = 10;
    public static final int COMPLETE_SAMPLE_COUNT = MATERIAL_COUNT * DISTANCE_COUNT * SAMPLES_PER_DISTANCE;
    public static final int MAX_INPUT_BYTES = 65536;
    private static final String[] MATERIALS = { "MDF", "PLASTICO", "ISOPOR", "ESPUMA" };

    private final Map<String, Sample> samples = new LinkedHashMap<String, Sample>();

    public void merge(byte[] input) {
        if (input == null || input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("CSV size");
        String text = new String(input, StandardCharsets.US_ASCII).replace("\r", "");
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#'
                    || "material,nominal_cm,amostra,medida_cm,eco".equals(line)) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 5) continue;
            Sample sample = parse(fields);
            samples.put(sample.key(), sample);
        }
        if (samples.size() > COMPLETE_SAMPLE_COUNT) throw new IllegalArgumentException("Too many samples");
    }

    public int size() { return samples.size(); }
    public boolean isComplete() { return samples.size() == COMPLETE_SAMPLE_COUNT && missingCount() == 0; }
    public int missingCount() {
        int missing = 0;
        for (String material : MATERIALS) for (int nominal = 5; nominal <= 50; nominal += 5)
            for (int sample = 1; sample <= SAMPLES_PER_DISTANCE; sample++)
                if (!samples.containsKey(key(material, nominal, sample))) missing++;
        return missing;
    }

    public String renderRawCsv() {
        StringBuilder out = new StringBuilder("material,nominal_cm,amostra,medida_cm,eco\n");
        for (Sample sample : ordered()) {
            out.append(sample.material).append(',').append(format(sample.nominalCm, 2)).append(',')
                    .append(sample.sampleIndex).append(',');
            if (sample.echo) out.append(format(sample.measuredCm, 3)).append(",1\n");
            else out.append("NA,0\n");
        }
        return out.toString();
    }

    public String renderMetricsCsv() {
        StringBuilder out = new StringBuilder("record_type,material,nominal_cm,total,valid,timeouts,mean_cm,mean_abs_error_cm,stddev_cm,slope,intercept_cm,r_squared\n");
        for (String material : MATERIALS) {
            List<Sample> all = matching(material, 0);
            for (int nominal = 5; nominal <= 50; nominal += 5) {
                List<Sample> group = matching(material, nominal);
                Stats stats = Stats.of(group);
                out.append("distance,").append(material).append(',').append(format(nominal, 2)).append(',')
                        .append(group.size()).append(',').append(stats.valid).append(',')
                        .append(group.size() - stats.valid).append(',').append(optional(stats.mean, 3)).append(',')
                        .append(optional(stats.meanAbsoluteError, 3)).append(',')
                        .append(optional(stats.standardDeviation, 3)).append(",NA,NA,NA\n");
            }
            Stats stats = Stats.of(all);
            Regression regression = Regression.of(all);
            out.append("material,").append(material).append(",NA,").append(all.size()).append(',')
                    .append(stats.valid).append(',').append(all.size() - stats.valid).append(',')
                    .append(optional(stats.mean, 3)).append(',').append(optional(stats.meanAbsoluteError, 3))
                    .append(',').append(optional(stats.standardDeviation, 3)).append(',')
                    .append(optional(regression.slope, 6)).append(',').append(optional(regression.intercept, 6))
                    .append(',').append(optional(regression.rSquared, 6)).append('\n');
        }
        return out.toString();
    }

    private List<Sample> ordered() {
        List<Sample> result = new ArrayList<Sample>(samples.values());
        Collections.sort(result, new Comparator<Sample>() {
            @Override public int compare(Sample a, Sample b) {
                int material = materialIndex(a.material) - materialIndex(b.material);
                if (material != 0) return material;
                int nominal = Double.compare(a.nominalCm, b.nominalCm);
                return nominal != 0 ? nominal : a.sampleIndex - b.sampleIndex;
            }
        });
        return result;
    }

    private List<Sample> matching(String material, int nominal) {
        List<Sample> result = new ArrayList<Sample>();
        for (Sample sample : samples.values()) if (material.equals(sample.material)
                && (nominal == 0 || sample.nominalCm == nominal)) result.add(sample);
        return result;
    }

    private static Sample parse(String[] fields) {
        String material = fields[0].trim().toUpperCase(Locale.ROOT);
        if (materialIndex(material) < 0) throw new IllegalArgumentException("Unknown material");
        double nominal = number(fields[1]);
        int roundedNominal = (int) StrictMath.round(nominal);
        if (nominal != roundedNominal || roundedNominal < 5 || roundedNominal > 50 || roundedNominal % 5 != 0)
            throw new IllegalArgumentException("Invalid nominal distance");
        int index = integer(fields[2]);
        if (index < 1 || index > SAMPLES_PER_DISTANCE) throw new IllegalArgumentException("Invalid sample index");
        int echo = integer(fields[4]);
        if (echo != 0 && echo != 1) throw new IllegalArgumentException("Invalid echo flag");
        if (echo == 0) {
            if (!"NA".equals(fields[3].trim())) throw new IllegalArgumentException("Timeout must be NA");
            return new Sample(material, roundedNominal, index, false, Double.NaN);
        }
        double measured = number(fields[3]);
        if (measured < UltrasonicMeasurementModel.MIN_CM || measured > UltrasonicMeasurementModel.MAX_CM)
            throw new IllegalArgumentException("Measurement range");
        return new Sample(material, roundedNominal, index, true, measured);
    }

    private static int materialIndex(String material) {
        for (int i = 0; i < MATERIALS.length; i++) if (MATERIALS[i].equals(material)) return i;
        return -1;
    }
    private static String key(String material, int nominal, int sample) {
        return material + ':' + nominal + ':' + sample;
    }
    private static double number(String value) {
        double result;
        try { result = Double.parseDouble(value.trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid number"); }
        if (Double.isNaN(result) || Double.isInfinite(result)) throw new IllegalArgumentException("Non-finite number");
        return result;
    }
    private static int integer(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid integer"); }
    }
    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
    private static String optional(double value, int decimals) {
        return Double.isNaN(value) ? "NA" : format(value, decimals);
    }

    private static final class Sample {
        final String material; final int nominalCm, sampleIndex; final boolean echo; final double measuredCm;
        Sample(String material, int nominalCm, int sampleIndex, boolean echo, double measuredCm) {
            this.material = material; this.nominalCm = nominalCm; this.sampleIndex = sampleIndex;
            this.echo = echo; this.measuredCm = measuredCm;
        }
        String key() { return UltrasonicMetrologyDataset.key(material, nominalCm, sampleIndex); }
    }

    private static final class Stats {
        final int valid; final double mean, meanAbsoluteError, standardDeviation;
        Stats(int valid, double mean, double meanAbsoluteError, double standardDeviation) {
            this.valid = valid; this.mean = mean; this.meanAbsoluteError = meanAbsoluteError;
            this.standardDeviation = standardDeviation;
        }
        static Stats of(List<Sample> values) {
            int valid = 0; double sum = 0.0, absolute = 0.0;
            for (Sample value : values) if (value.echo) {
                valid++; sum += value.measuredCm; absolute += StrictMath.abs(value.measuredCm - value.nominalCm);
            }
            if (valid == 0) return new Stats(0, Double.NaN, Double.NaN, Double.NaN);
            double mean = sum / valid, squares = 0.0;
            for (Sample value : values) if (value.echo) {
                double delta = value.measuredCm - mean; squares += delta * delta;
            }
            return new Stats(valid, mean, absolute / valid,
                    valid > 1 ? StrictMath.sqrt(squares / (valid - 1)) : Double.NaN);
        }
    }

    private static final class Regression {
        final double slope, intercept, rSquared;
        Regression(double slope, double intercept, double rSquared) {
            this.slope = slope; this.intercept = intercept; this.rSquared = rSquared;
        }
        static Regression of(List<Sample> values) {
            int n = 0; double sumX = 0.0, sumY = 0.0;
            for (Sample value : values) if (value.echo) { n++; sumX += value.nominalCm; sumY += value.measuredCm; }
            if (n < 2) return new Regression(Double.NaN, Double.NaN, Double.NaN);
            double meanX = sumX / n, meanY = sumY / n, xx = 0.0, xy = 0.0, yy = 0.0;
            for (Sample value : values) if (value.echo) {
                double dx = value.nominalCm - meanX, dy = value.measuredCm - meanY;
                xx += dx * dx; xy += dx * dy; yy += dy * dy;
            }
            if (xx == 0.0) return new Regression(Double.NaN, Double.NaN, Double.NaN);
            double slope = xy / xx, intercept = meanY - slope * meanX;
            if (StrictMath.abs(intercept) < 1.0e-12) intercept = 0.0;
            double r2 = yy == 0.0 ? Double.NaN : xy * xy / (xx * yy);
            return new Regression(slope, intercept, r2);
        }
    }
}
