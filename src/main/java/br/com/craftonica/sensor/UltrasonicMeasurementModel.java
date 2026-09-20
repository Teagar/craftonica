package br.com.craftonica.sensor;

/** Pure, deterministic metrology model suitable for repeatable lessons and tests. */
public strictfp final class UltrasonicMeasurementModel {
    public static final double MIN_CM = 2.0;
    public static final double MAX_CM = 400.0;
    public static final double MICROSECONDS_PER_CM = 58.0;
    public static final int AVR_CYCLES_PER_MICROSECOND = 16;

    private UltrasonicMeasurementModel() { }

    public static Measurement measure(double trueCm, double incidenceDegrees,
                                      AcousticMaterialProfile material, long seed) {
        if (material == null || Double.isNaN(trueCm) || Double.isInfinite(trueCm)
                || trueCm < MIN_CM || trueCm > MAX_CM) return Measurement.noEcho();
        double angle = Math.max(0.0, Math.min(80.0, Math.abs(incidenceDegrees)));
        double angularResponse = Math.max(0.05, StrictMath.cos(StrictMath.toRadians(angle)));
        double distanceLoss = 1.0 / (1.0 + trueCm * trueCm / 90000.0);
        double confidence = material.getReflectivity() * angularResponse * distanceLoss;
        double dropout = clamp((0.72 - confidence) * 0.70, 0.0, 0.65);
        double uniform = unit(mix(seed));
        if (uniform < dropout) return Measurement.noEcho();

        double scale = trueCm / 50.0;
        double bias = material.getBiasAt50Cm() * scale * scale;
        double sigma = material.getSigmaAt50Cm() * Math.max(0.35, scale)
                / Math.max(0.35, angularResponse);
        double measured = clamp(trueCm + bias + gaussian(seed ^ 0x9e3779b97f4a7c15L) * sigma,
                MIN_CM, MAX_CM);
        long echoCycles = Math.max(1L, Math.round(measured * MICROSECONDS_PER_CM
                * AVR_CYCLES_PER_MICROSECOND));
        return new Measurement(true, trueCm, measured, echoCycles, confidence);
    }

    private static double gaussian(long seed) {
        double u1 = Math.max(1.0e-12, unit(mix(seed)));
        double u2 = unit(mix(seed + 0x632be59bd9b4e019L));
        return StrictMath.sqrt(-2.0 * StrictMath.log(u1)) * StrictMath.cos(2.0 * StrictMath.PI * u2);
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27; value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) {
        return ((value >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Measurement {
        public final boolean echo;
        public final double trueCentimeters;
        public final double measuredCentimeters;
        public final long echoCycles;
        public final double confidence;

        Measurement(boolean echo, double trueCentimeters, double measuredCentimeters,
                    long echoCycles, double confidence) {
            this.echo = echo;
            this.trueCentimeters = trueCentimeters;
            this.measuredCentimeters = measuredCentimeters;
            this.echoCycles = echoCycles;
            this.confidence = confidence;
        }

        public static Measurement noEcho() {
            return new Measurement(false, Double.NaN, Double.NaN, 0L, 0.0);
        }
    }
}
