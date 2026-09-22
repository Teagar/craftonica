package br.com.craftonica.robot.modular.physics;

import java.util.List;

/**
 * Deterministic profile-1 rigid body. It models yaw and translation, but deliberately
 * omits suspension, tyre deformation, continuous roll/pitch and body-to-body momentum.
 */
public strictfp final class TerrestrialRigidBodyModel {
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.80665;
    public static final int MAX_SUBSTEPS_PER_TICK = 4;
    public static final int MAX_FORCES_PER_SUBSTEP = 24;

    private TerrestrialRigidBodyModel() { }

    public static State step(RigidBodyProperties body, State before, List<AppliedForce> forces,
                             List<Integer> supportedContacts, double dtSeconds) {
        if (body == null || before == null || forces == null || supportedContacts == null
                || supportedContacts.size() > body.getContacts().size() || forces.size() > MAX_FORCES_PER_SUBSTEP
                || !finite(dtSeconds) || dtSeconds <= 0.0 || dtSeconds > 0.05)
            throw new IllegalArgumentException("rigid body step");
        boolean[] supported = new boolean[body.getContacts().size()];
        for (Integer index : supportedContacts) {
            if (index == null || index.intValue() < 0 || index.intValue() >= supported.length
                    || supported[index.intValue()]) throw new IllegalArgumentException("supported contact");
            supported[index.intValue()] = true;
        }
        double yaw = before.yawRadians;
        double cosine = StrictMath.cos(yaw), sine = StrictMath.sin(yaw);
        double forceLocalX = 0.0, forceLocalZ = 0.0, torqueY = 0.0;
        for (AppliedForce force : forces) {
            if (force.contactIndex < 0 || force.contactIndex >= supported.length)
                throw new IllegalArgumentException("force contact");
            if (!supported[force.contactIndex]) continue;
            RigidBodyProperties.Contact contact = body.getContacts().get(force.contactIndex);
            if (contact.tractionDirection.x == 0.0 && contact.tractionDirection.z == 0.0) continue;
            double normalForce = body.massKg * GRAVITY_METRES_PER_SECOND_SQUARED
                    / supportedContacts.size();
            double limit = StrictMath.min(force.maximumMagnitudeNewtons,
                    normalForce * contact.longitudinalFriction);
            double longitudinal = StrictMath.copySign(StrictMath.min(
                    StrictMath.abs(force.longitudinalForceNewtons), limit), force.longitudinalForceNewtons);
            double fx = contact.tractionDirection.x * longitudinal;
            double fz = contact.tractionDirection.z * longitudinal;
            forceLocalX += fx; forceLocalZ += fz;
            double rx = contact.pointMetres.x - body.centerOfMassMetres.x;
            double rz = contact.pointMetres.z - body.centerOfMassMetres.z;
            torqueY += rz * fx - rx * fz;
        }

        double localVelocityX = cosine * before.velocityX - sine * before.velocityZ;
        double localVelocityZ = sine * before.velocityX + cosine * before.velocityZ;
        if (!supportedContacts.isEmpty()) {
            double normalPerContact = body.massKg * GRAVITY_METRES_PER_SECOND_SQUARED / supportedContacts.size();
            for (int contactIndex = 0; contactIndex < body.getContacts().size(); contactIndex++) {
                if (!supported[contactIndex]) continue;
                RigidBodyProperties.Contact contact = body.getContacts().get(contactIndex);
                double rx = contact.pointMetres.x - body.centerOfMassMetres.x;
                double rz = contact.pointMetres.z - body.centerOfMassMetres.z;
                double contactX = localVelocityX + before.angularVelocityRadiansPerSecond * rz;
                double contactZ = localVelocityZ - before.angularVelocityRadiansPerSecond * rx;
                if (contact.tractionDirection.x != 0.0 || contact.tractionDirection.z != 0.0) {
                    double dx = contact.tractionDirection.x, dz = contact.tractionDirection.z;
                    double longitudinal = contactX * dx + contactZ * dz;
                    double lateral = contactX * -dz + contactZ * dx;
                    double longForce = oppose(longitudinal, normalPerContact * contact.rollingFriction,
                            body.massKg / (supportedContacts.size() * dtSeconds));
                    double lateralForce = oppose(lateral, normalPerContact * contact.lateralFriction,
                            body.massKg / (supportedContacts.size() * dtSeconds));
                    double fx = longForce * dx - lateralForce * dz;
                    double fz = longForce * dz + lateralForce * dx;
                    forceLocalX += fx; forceLocalZ += fz; torqueY += rz * fx - rx * fz;
                } else {
                    double speed = StrictMath.sqrt(contactX * contactX + contactZ * contactZ);
                    if (speed > 0.0) {
                        double drag = StrictMath.min(normalPerContact * contact.rollingFriction,
                                body.massKg * speed / (supportedContacts.size() * dtSeconds));
                        double fx = -drag * contactX / speed, fz = -drag * contactZ / speed;
                        forceLocalX += fx; forceLocalZ += fz; torqueY += rz * fx - rx * fz;
                    }
                }
            }
        }

        double worldForceX = cosine * forceLocalX + sine * forceLocalZ;
        double worldForceZ = -sine * forceLocalX + cosine * forceLocalZ;
        double velocityX = before.velocityX + worldForceX / body.massKg * dtSeconds;
        double velocityZ = before.velocityZ + worldForceZ / body.massKg * dtSeconds;
        double angularVelocity = before.angularVelocityRadiansPerSecond
                + torqueY / body.principalInertiaKgMetresSquared.y * dtSeconds;
        double velocityY = !supportedContacts.isEmpty() ? 0.0
                : before.velocityY - GRAVITY_METRES_PER_SECOND_SQUARED * dtSeconds;
        return new State(before.x + velocityX * dtSeconds, before.y + velocityY * dtSeconds,
                before.z + velocityZ * dtSeconds, wrap(yaw + angularVelocity * dtSeconds),
                velocityX, velocityY, velocityZ, angularVelocity);
    }

    private static double oppose(double velocity, double maximumForce, double stopFactor) {
        if (velocity == 0.0 || maximumForce <= 0.0) return 0.0;
        return -StrictMath.copySign(StrictMath.min(maximumForce, StrictMath.abs(velocity) * stopFactor), velocity);
    }

    private static double wrap(double angle) {
        angle %= StrictMath.PI * 2.0;
        if (angle >= StrictMath.PI) angle -= StrictMath.PI * 2.0;
        if (angle < -StrictMath.PI) angle += StrictMath.PI * 2.0;
        return angle;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    public static final class State {
        public final double x, y, z, yawRadians;
        public final double velocityX, velocityY, velocityZ, angularVelocityRadiansPerSecond;

        public State(double x, double y, double z, double yawRadians, double velocityX,
                     double velocityY, double velocityZ, double angularVelocity) {
            this.x = checked(x, "x"); this.y = checked(y, "y");
            this.z = checked(z, "z"); this.yawRadians = checked(yawRadians, "yaw");
            this.velocityX = checked(velocityX, "velocityX");
            this.velocityY = checked(velocityY, "velocityY");
            this.velocityZ = checked(velocityZ, "velocityZ");
            this.angularVelocityRadiansPerSecond = checked(angularVelocity, "angularVelocity");
        }

        public State stopPlanar() { return new State(x, y, z, yawRadians, 0.0, velocityY, 0.0, 0.0); }
        public State stopVertical(double restoredY) {
            return new State(x, restoredY, z, yawRadians, velocityX, 0.0, velocityZ,
                    angularVelocityRadiansPerSecond);
        }
    }

    public static final class AppliedForce {
        public final int contactIndex;
        public final double longitudinalForceNewtons, maximumMagnitudeNewtons;

        public AppliedForce(int contactIndex, double longitudinalForceNewtons,
                            double maximumMagnitudeNewtons) {
            if (contactIndex < 0) throw new IllegalArgumentException("contactIndex");
            this.contactIndex = contactIndex;
            this.longitudinalForceNewtons = checked(longitudinalForceNewtons, "longitudinalForceNewtons");
            this.maximumMagnitudeNewtons = checked(maximumMagnitudeNewtons, "maximumMagnitudeNewtons");
            if (maximumMagnitudeNewtons <= 0.0) throw new IllegalArgumentException("maximumMagnitudeNewtons");
        }
    }

    private static double checked(double value, String name) {
        if (!finite(value)) throw new IllegalArgumentException(name);
        return value;
    }
}
