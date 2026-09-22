package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.ContactProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Derived mechanical paths and ground contacts; no blueprint labels are used. */
public strictfp final class MechanicalAssembly {
    public enum Diagnostic {
        OPEN_PATH,
        UNKNOWN_PORT,
        INCOMPATIBLE_CONNECTION,
        INCOMPATIBLE_GEAR_MESH,
        TRANSMISSION_LOOP_UNSUPPORTED,
        TRANSMISSION_BRANCH_UNSUPPORTED,
        TRANSMISSION_LIMIT_EXCEEDED,
        MULTIPLE_INPUTS,
        TRACK_MOUNT_OPEN
    }
    private final List<DrivePath> drives;
    private final List<GroundContact> contacts;
    private final List<TransmissionDiagnostic> diagnostics;
    MechanicalAssembly(List<DrivePath> drives, List<GroundContact> contacts,
                       List<TransmissionDiagnostic> diagnostics) {
        this.drives = Collections.unmodifiableList(new ArrayList<DrivePath>(drives));
        this.contacts = Collections.unmodifiableList(new ArrayList<GroundContact>(contacts));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<TransmissionDiagnostic>(diagnostics));
    }
    public List<DrivePath> getDrives() { return drives; }
    public List<GroundContact> getContacts() { return contacts; }
    public List<TransmissionDiagnostic> getDiagnostics() { return diagnostics; }
    public int drivenWheelCount() { return drives.size(); }
    public int passiveCasterCount() {
        int count = 0;
        for (GroundContact contact : contacts)
            if (contact.kind == ContactProfile.Kind.PASSIVE_CASTER) count++;
        return count;
    }

    public static final class DrivePath {
        public final GridVector motorPosition, wheelPosition;
        public final Direction axleAxis;
        public final double wheelRadiusMetres, tractionLeverArmMetres, wheelWidthMetres, maximumTorqueNm;
        public final double speedRatio, efficiency, reflectedInertiaKgM2;
        public final int directionSign, gearStages;
        private final List<EncoderTap> encoderTaps;
        DrivePath(GridVector motorPosition, GridVector wheelPosition, Direction axleAxis,
                  double radius, double tractionLever, double width, double maximumTorqueNm, double speedRatio,
                  int directionSign, double efficiency, double reflectedInertiaKgM2, int gearStages,
                  List<EncoderTap> encoderTaps) {
            this.motorPosition = motorPosition; this.wheelPosition = wheelPosition; this.axleAxis = axleAxis;
            this.wheelRadiusMetres = radius; this.tractionLeverArmMetres = tractionLever;
            this.wheelWidthMetres = width; this.maximumTorqueNm = maximumTorqueNm;
            if (!finite(speedRatio) || speedRatio <= 0.0 || (directionSign != -1 && directionSign != 1)
                    || !finite(efficiency) || efficiency <= 0.0 || efficiency > 1.0
                    || !finite(tractionLever) || tractionLever <= 0.0 || tractionLever > radius
                    || !finite(reflectedInertiaKgM2) || reflectedInertiaKgM2 < 0.0 || gearStages < 0)
                throw new IllegalArgumentException("drive transmission");
            this.speedRatio = speedRatio; this.directionSign = directionSign; this.efficiency = efficiency;
            this.reflectedInertiaKgM2 = reflectedInertiaKgM2; this.gearStages = gearStages;
            this.encoderTaps = Collections.unmodifiableList(new ArrayList<EncoderTap>(encoderTaps));
        }
        public double linearSpeedMetresPerSecond(double angularVelocityRadPerSecond) {
            return outputAngularVelocity(angularVelocityRadPerSecond) * tractionLeverArmMetres;
        }
        public double outputAngularVelocity(double motorAngularVelocity) {
            requireFinite(motorAngularVelocity, "motor angular velocity");
            return directionSign * motorAngularVelocity / speedRatio;
        }
        public double motorAngularVelocity(double outputAngularVelocity) {
            requireFinite(outputAngularVelocity, "output angular velocity");
            return directionSign * outputAngularVelocity * speedRatio;
        }
        public double outputTorque(double motorTorque) {
            requireFinite(motorTorque, "motor torque");
            return directionSign * motorTorque * speedRatio * efficiency;
        }
        public double motorLoadTorque(double outputLoadTorque) {
            requireFinite(outputLoadTorque, "output load torque");
            return directionSign * outputLoadTorque / (speedRatio * efficiency);
        }
        public double reflectedInertiaAtMotor(double outputInertiaKgM2) {
            if (!finite(outputInertiaKgM2) || outputInertiaKgM2 < 0.0)
                throw new IllegalArgumentException("output inertia");
            return reflectedInertiaKgM2 + outputInertiaKgM2 / (speedRatio * speedRatio);
        }
        public List<EncoderTap> getEncoderTaps() { return encoderTaps; }
        private static void requireFinite(double value, String label) {
            if (!finite(value)) throw new IllegalArgumentException(label);
        }
        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    public static final class EncoderTap {
        public final GridVector position; public final double speedRatio; public final int directionSign;
        EncoderTap(GridVector position,double speedRatio,int directionSign){
            if(position==null||!finite(speedRatio)||speedRatio<=0.0||(directionSign!=-1&&directionSign!=1))
                throw new IllegalArgumentException("encoder tap");
            this.position=position;this.speedRatio=speedRatio;this.directionSign=directionSign;
        }
        public double angularVelocity(double motorAngularVelocity){
            if(!finite(motorAngularVelocity))throw new IllegalArgumentException("motor angular velocity");
            return directionSign*motorAngularVelocity/speedRatio;
        }
        private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    }

    public static final class TransmissionDiagnostic {
        public final Diagnostic code;
        public final GridVector position;
        public final String detail;
        TransmissionDiagnostic(Diagnostic code, GridVector position, String detail) {
            if (code == null || position == null) throw new IllegalArgumentException("transmission diagnostic");
            this.code = code; this.position = position; this.detail = detail == null ? "" : detail;
        }
    }

    public static final class GroundContact {
        public final GridVector position;
        public final ContactProfile.Kind kind;
        public final boolean driven;
        public final double radiusMetres, longitudinalFriction, lateralFriction, rollingFriction;
        GroundContact(GridVector position, ContactProfile.Kind kind, boolean driven, double radius, double longitudinal,
                      double lateral, double rolling) {
            this.position = position; this.kind = kind; this.driven = driven; this.radiusMetres = radius;
            this.longitudinalFriction = longitudinal; this.lateralFriction = lateral; this.rollingFriction = rolling;
        }
    }
}
