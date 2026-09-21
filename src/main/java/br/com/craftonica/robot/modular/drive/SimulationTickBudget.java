package br.com.craftonica.robot.modular.drive;

/** Atomic per-robot budget; a frame is delayed rather than partially executed. */
public final class SimulationTickBudget {
    public static final int MAX_AVR_FRAMES = 1;
    public static final int MAX_NETLIST_EVALUATIONS = 1;
    public static final int MAX_DRIVE_STEPS = 64;
    public static final int MAX_PHYSICS_SUBSTEPS = 4;

    private int avrFrames, netlistEvaluations, driveSteps, physicsSubsteps;

    public boolean reserveFrame(int drives, int physics) {
        if (drives < 0 || physics < 0) throw new IllegalArgumentException("budget request");
        if (avrFrames + 1 > MAX_AVR_FRAMES || netlistEvaluations + 1 > MAX_NETLIST_EVALUATIONS
                || driveSteps + drives > MAX_DRIVE_STEPS
                || physicsSubsteps + physics > MAX_PHYSICS_SUBSTEPS) return false;
        avrFrames++; netlistEvaluations++; driveSteps += drives; physicsSubsteps += physics;
        return true;
    }

    public int getAvrFrames() { return avrFrames; }
    public int getDriveSteps() { return driveSteps; }
}
