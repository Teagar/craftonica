package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.JointProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical rigid-body partition and one-DOF joint tree. */
public final class KinematicAssembly {
    public enum Diagnostic {
        NONE, JOINT_CONNECTION_MISSING, JOINT_CONNECTION_AMBIGUOUS, JOINT_RIGIDLY_BYPASSED,
        MULTIPLE_JOINT_PARENTS, KINEMATIC_LOOP_UNSUPPORTED, ORPHAN_BODY, LIMIT_EXCEEDED
    }
    private final List<Body> bodies;
    private final List<Joint> joints;
    private final List<Problem> diagnostics;

    KinematicAssembly(List<Body> bodies, List<Joint> joints, List<Problem> diagnostics) {
        this.bodies = immutable(bodies); this.joints = immutable(joints); this.diagnostics = immutable(diagnostics);
    }
    public List<Body> getBodies() { return bodies; }
    public List<Joint> getJoints() { return joints; }
    public List<Problem> getDiagnostics() { return diagnostics; }
    public boolean isValid() { return diagnostics.isEmpty(); }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static final class Body {
        public final int bodyId;
        public final List<GridVector> modulePositions;
        Body(int bodyId, List<GridVector> positions) {
            this.bodyId = bodyId; this.modulePositions = immutable(positions);
        }
    }

    public static final class Joint {
        public final GridVector modulePosition;
        public final int parentBodyId, childBodyId;
        public final JointProfile.Kind kind;
        public final JointProfile profile;
        public final Direction axis;
        public final double minimumPosition, maximumPosition, maximumVelocity, maximumEffort;
        /** First component on the physical transmission path, not necessarily the actuator itself. */
        public final GridVector driveConnectionPosition;
        Joint(GridVector position, int parent, int child, JointProfile profile, Direction axis,
                GridVector driveConnectionPosition) {
            this.modulePosition = position; this.parentBodyId = parent; this.childBodyId = child;
            this.kind = profile.kind; this.profile = profile; this.axis = axis; this.minimumPosition = profile.minimumPosition;
            this.maximumPosition = profile.maximumPosition; this.maximumVelocity = profile.maximumVelocity;
            this.maximumEffort = profile.maximumEffort; this.driveConnectionPosition = driveConnectionPosition;
        }
        public boolean hasDriveConnection() { return driveConnectionPosition != null; }
    }

    public static final class Problem {
        public final Diagnostic code;
        public final GridVector position;
        public final String detail;
        Problem(Diagnostic code, GridVector position, String detail) {
            this.code = code; this.position = position; this.detail = detail == null ? "" : detail;
        }
    }
}
