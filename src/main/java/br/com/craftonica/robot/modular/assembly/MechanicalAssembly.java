package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.ContactProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Derived mechanical paths and ground contacts; no blueprint labels are used. */
public final class MechanicalAssembly {
    private final List<DrivePath> drives;
    private final List<GroundContact> contacts;
    MechanicalAssembly(List<DrivePath> drives, List<GroundContact> contacts) {
        this.drives = Collections.unmodifiableList(new ArrayList<DrivePath>(drives));
        this.contacts = Collections.unmodifiableList(new ArrayList<GroundContact>(contacts));
    }
    public List<DrivePath> getDrives() { return drives; }
    public List<GroundContact> getContacts() { return contacts; }
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
        public final double wheelRadiusMetres, wheelWidthMetres, maximumTorqueNm;
        DrivePath(GridVector motorPosition, GridVector wheelPosition, Direction axleAxis,
                  double radius, double width, double maximumTorqueNm) {
            this.motorPosition = motorPosition; this.wheelPosition = wheelPosition; this.axleAxis = axleAxis;
            this.wheelRadiusMetres = radius; this.wheelWidthMetres = width; this.maximumTorqueNm = maximumTorqueNm;
        }
        public double linearSpeedMetresPerSecond(double angularVelocityRadPerSecond) {
            if (Double.isNaN(angularVelocityRadPerSecond) || Double.isInfinite(angularVelocityRadPerSecond))
                throw new IllegalArgumentException("angular velocity");
            return angularVelocityRadPerSecond * wheelRadiusMetres;
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
