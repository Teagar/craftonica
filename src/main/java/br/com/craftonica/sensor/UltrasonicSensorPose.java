package br.com.craftonica.sensor;

/** Immutable world-space pose for an ultrasonic emitter. Minecraft pitch is positive downward. */
public strictfp final class UltrasonicSensorPose {
    public final double x;
    public final double y;
    public final double z;
    public final double yawDegrees;
    public final double pitchDegrees;

    public UltrasonicSensorPose(double x, double y, double z, double yawDegrees, double pitchDegrees) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yawDegrees, "yawDegrees");
        requireFinite(pitchDegrees, "pitchDegrees");
        if (pitchDegrees < -89.0 || pitchDegrees > 89.0) {
            throw new IllegalArgumentException("pitchDegrees");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawDegrees = normalizeYaw(yawDegrees);
        this.pitchDegrees = pitchDegrees;
    }

    public Vector forward() {
        double yaw = StrictMath.toRadians(yawDegrees);
        double pitch = StrictMath.toRadians(pitchDegrees);
        double cosPitch = StrictMath.cos(pitch);
        return new Vector(-StrictMath.sin(yaw) * cosPitch, -StrictMath.sin(pitch),
                StrictMath.cos(yaw) * cosPitch);
    }

    public Vector ray(double radialDegrees, double azimuthDegrees) {
        if (!finite(radialDegrees) || radialDegrees < 0.0 || radialDegrees > 45.0
                || !finite(azimuthDegrees)) throw new IllegalArgumentException("ray angles");
        Vector forward = forward();
        double yaw = StrictMath.toRadians(yawDegrees);
        Vector right = new Vector(StrictMath.cos(yaw), 0.0, StrictMath.sin(yaw));
        Vector up = forward.cross(right);
        double radial = StrictMath.toRadians(radialDegrees);
        double azimuth = StrictMath.toRadians(azimuthDegrees);
        double tangent = StrictMath.tan(radial);
        return forward.add(right.scale(tangent * StrictMath.cos(azimuth)))
                .add(up.scale(tangent * StrictMath.sin(azimuth))).normalized();
    }

    private static double normalizeYaw(double yaw) {
        double normalized = yaw % 360.0;
        return normalized < -180.0 ? normalized + 360.0
                : normalized >= 180.0 ? normalized - 360.0 : normalized;
    }

    private static void requireFinite(double value, String name) {
        if (!finite(value)) throw new IllegalArgumentException(name);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Vector {
        public final double x;
        public final double y;
        public final double z;

        private Vector(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        private Vector scale(double factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }

        private Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        private Vector normalized() {
            double length = StrictMath.sqrt(x * x + y * y + z * z);
            if (length < 1.0e-12) throw new IllegalArgumentException("zero direction");
            return new Vector(x / length, y / length, z / length);
        }

        public double dot(Vector other) {
            if (other == null) throw new IllegalArgumentException("other");
            return x * other.x + y * other.y + z * other.z;
        }
    }
}
