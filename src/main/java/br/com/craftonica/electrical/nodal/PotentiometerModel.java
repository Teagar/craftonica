package br.com.craftonica.electrical.nodal;

/** Pure deterministic track model. The cursor never makes a singular zero-ohm branch. */
public final class PotentiometerModel {
    public static final double NOMINAL_OHMS = 10000.0;
    public static final double MIN_BRANCH_OHMS = 1.0;
    public static final int MIN_STEP = 0;
    public static final int MAX_STEP = 100;

    private PotentiometerModel() { }

    public static int clampStep(int step) { return Math.max(MIN_STEP, Math.min(MAX_STEP, step)); }
    public static double resistanceA(int step) {
        int clamped = clampStep(step);
        return MIN_BRANCH_OHMS + (NOMINAL_OHMS - 2.0 * MIN_BRANCH_OHMS) * clamped / MAX_STEP;
    }
    public static double resistanceB(int step) { return NOMINAL_OHMS - resistanceA(step); }
}
