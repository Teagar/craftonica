package br.com.craftonica.robot.modular.physics.articulated;

/** Atomic articulated solver budget. A rejected reservation performs no partial step. */
public final class ArticulatedTickBudget {
    public static final int MAX_BODY_STEPS = 64;
    public static final int MAX_JOINT_STEPS = 62;
    public static final int MAX_COLLISION_TESTS = 256;
    public static final int MAX_COLLISION_TESTS_PER_SUBSTEP = 128;
    private int bodySteps, jointSteps, collisionTests;

    public boolean reserve(int bodies, int joints, int collisions) {
        if (bodies < 0 || joints < 0 || collisions < 0) throw new IllegalArgumentException("budget request");
        if (collisions > MAX_COLLISION_TESTS_PER_SUBSTEP || bodySteps + bodies > MAX_BODY_STEPS || jointSteps + joints > MAX_JOINT_STEPS
                || collisionTests + collisions > MAX_COLLISION_TESTS) return false;
        bodySteps += bodies; jointSteps += joints; collisionTests += collisions; return true;
    }
    public int getBodySteps() { return bodySteps; }
    public int getJointSteps() { return jointSteps; }
    public int getCollisionTests() { return collisionTests; }
}
