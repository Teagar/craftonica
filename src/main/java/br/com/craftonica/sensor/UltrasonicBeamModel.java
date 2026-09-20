package br.com.craftonica.sensor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure cone sampling and apparent-target aggregation, independent of Minecraft and Forge. */
public strictfp final class UltrasonicBeamModel {
    public static final double HALF_ANGLE_DEGREES = 15.0;
    public static final int RAY_COUNT = 13;
    private static final Ray[] RAYS = createRays();

    private UltrasonicBeamModel() { }

    public static Ray[] rays(UltrasonicSensorPose pose) {
        if (pose == null) throw new IllegalArgumentException("pose");
        Ray[] result = new Ray[RAYS.length];
        for (int i = 0; i < RAYS.length; i++) {
            Ray template = RAYS[i];
            result[i] = new Ray(i, template.weight,
                    pose.ray(template.radialDegrees, template.azimuthDegrees),
                    template.radialDegrees, template.azimuthDegrees);
        }
        return result;
    }

    public static Echo resolve(List<RayReturn> returns) {
        if (returns == null || returns.isEmpty()) return null;
        Map<String, Candidate> candidates = new TreeMap<String, Candidate>();
        RayReturn[] canonical = new RayReturn[RAY_COUNT];
        for (RayReturn value : returns) {
            if (value == null || value.rayIndex < 0 || value.rayIndex >= RAY_COUNT) continue;
            RayReturn previous = canonical[value.rayIndex];
            if (previous == null || compare(value, previous) < 0) canonical[value.rayIndex] = value;
        }
        for (int rayIndex = 0; rayIndex < canonical.length; rayIndex++) {
            RayReturn value = canonical[rayIndex];
            if (value == null) continue;
            Candidate candidate = candidates.get(value.targetKey);
            if (candidate == null) {
                candidate = new Candidate(value.targetKey, value.material, value.mode);
                candidates.put(value.targetKey, candidate);
            }
            candidate.add(value, RAYS[value.rayIndex].weight);
        }

        Candidate best = null;
        double bestStrength = -1.0;
        for (Candidate candidate : candidates.values()) {
            double strength = candidate.returnStrength();
            if (best == null || strength > bestStrength + 1.0e-12
                    || (StrictMath.abs(strength - bestStrength) <= 1.0e-12
                    && candidate.minCentimeters < best.minCentimeters)) {
                best = candidate;
                bestStrength = strength;
            }
        }
        return best == null ? null : best.echo();
    }

    private static int compare(RayReturn first, RayReturn second) {
        int distance = Double.compare(first.centimeters, second.centimeters);
        if (distance != 0) return distance;
        int key = first.targetKey.compareTo(second.targetKey);
        if (key != 0) return key;
        int material = first.material.ordinal() - second.material.ordinal();
        return material != 0 ? material : first.mode.compareTo(second.mode);
    }

    public static final class Ray {
        public final int index;
        public final double weight;
        public final UltrasonicSensorPose.Vector direction;
        public final double radialDegrees;
        public final double azimuthDegrees;

        private Ray(int index, double weight, UltrasonicSensorPose.Vector direction,
                    double radialDegrees, double azimuthDegrees) {
            this.index = index;
            this.weight = weight;
            this.direction = direction;
            this.radialDegrees = radialDegrees;
            this.azimuthDegrees = azimuthDegrees;
        }
    }

    public static final class RayReturn {
        public final int rayIndex;
        public final String targetKey;
        public final double centimeters;
        public final double incidenceDegrees;
        public final AcousticMaterialProfile material;
        public final String mode;

        public RayReturn(int rayIndex, String targetKey, double centimeters,
                         double incidenceDegrees, AcousticMaterialProfile material, String mode) {
            if (rayIndex < 0 || rayIndex >= RAY_COUNT || targetKey == null || targetKey.length() == 0
                    || material == null || !finite(centimeters) || centimeters < 0.0
                    || !finite(incidenceDegrees) || mode == null) {
                throw new IllegalArgumentException("invalid ray return");
            }
            this.rayIndex = rayIndex;
            this.targetKey = targetKey;
            this.centimeters = centimeters;
            this.incidenceDegrees = clamp(StrictMath.abs(incidenceDegrees), 0.0, 90.0);
            this.material = material;
            this.mode = mode;
        }
    }

    public static final class Echo {
        public final double centimeters;
        public final double incidenceDegrees;
        public final double apparentCoverage;
        public final AcousticMaterialProfile material;
        public final String mode;

        private Echo(double centimeters, double incidenceDegrees, double apparentCoverage,
                     AcousticMaterialProfile material, String mode) {
            this.centimeters = centimeters;
            this.incidenceDegrees = incidenceDegrees;
            this.apparentCoverage = apparentCoverage;
            this.material = material;
            this.mode = mode;
        }
    }

    private static final class Candidate {
        final String key;
        final AcousticMaterialProfile material;
        final String mode;
        double weight;
        double weightedIncidence;
        double minCentimeters = Double.POSITIVE_INFINITY;

        Candidate(String key, AcousticMaterialProfile material, String mode) {
            this.key = key;
            this.material = material;
            this.mode = mode;
        }

        void add(RayReturn value, double rayWeight) {
            weight += rayWeight;
            weightedIncidence += value.incidenceDegrees * rayWeight;
            minCentimeters = Math.min(minCentimeters, value.centimeters);
        }

        double returnStrength() {
            double angle = weight <= 0.0 ? 90.0 : weightedIncidence / weight;
            double angular = Math.max(0.0, StrictMath.cos(StrictMath.toRadians(angle)));
            double distance = 1.0 / (1.0 + minCentimeters * minCentimeters / 90000.0);
            return weight * material.getReflectivity() * angular * distance;
        }

        Echo echo() {
            return new Echo(minCentimeters, weightedIncidence / weight, clamp(weight, 0.0, 1.0),
                    material, mode);
        }
    }

    private static Ray[] createRays() {
        Ray[] rays = new Ray[RAY_COUNT];
        rays[0] = template(0.20, 0.0, 0.0);
        for (int i = 0; i < 4; i++) rays[1 + i] = template(0.10, 7.5, i * 90.0);
        for (int i = 0; i < 8; i++) rays[5 + i] = template(0.05, HALF_ANGLE_DEGREES, i * 45.0);
        return rays;
    }

    private static Ray template(double weight, double radial, double azimuth) {
        return new Ray(-1, weight, null, radial, azimuth);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
