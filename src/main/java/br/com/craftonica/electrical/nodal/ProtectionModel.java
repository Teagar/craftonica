package br.com.craftonica.electrical.nodal;

/** Pure protection policy shared by the solver and the world adapter. */
public final class ProtectionModel {
    public static final double MAX_CURRENT_AMPS = 0.100;
    public static final double MAX_POWER_WATTS = 0.500;

    private ProtectionModel() { }

    public static boolean mustTrip(double current, double power) {
        return Double.isFinite(current) && Double.isFinite(power)
                && (Math.abs(current) > MAX_CURRENT_AMPS || Math.abs(power) > MAX_POWER_WATTS);
    }
}
