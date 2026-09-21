package br.com.craftonica.robot.modular.drive;

/** Finite electrical and thermal limits for one modular H-bridge channel. */
public final class HBridgeParameters {
    public final double maximumSupplyVolts, voltageDropVolts, onResistanceOhms, brakeResistanceOhms;
    public final double maximumCurrentAmps, ambientTemperatureCelsius, maximumTemperatureCelsius;
    public final double thermalCapacityJoulesPerKelvin, thermalResistanceKelvinPerWatt;

    public HBridgeParameters(double maximumSupplyVolts, double voltageDropVolts,
            double onResistanceOhms, double brakeResistanceOhms, double maximumCurrentAmps,
            double ambientTemperatureCelsius, double maximumTemperatureCelsius,
            double thermalCapacityJoulesPerKelvin, double thermalResistanceKelvinPerWatt) {
        double[] positive = { maximumSupplyVolts, onResistanceOhms, brakeResistanceOhms,
                maximumCurrentAmps, thermalCapacityJoulesPerKelvin, thermalResistanceKelvinPerWatt };
        for (double value : positive) if (!finite(value) || value <= 0.0) throw new IllegalArgumentException("bridge parameters");
        if (!finite(voltageDropVolts) || voltageDropVolts < 0.0 || voltageDropVolts >= maximumSupplyVolts
                || !finite(ambientTemperatureCelsius) || !finite(maximumTemperatureCelsius)
                || maximumTemperatureCelsius <= ambientTemperatureCelsius)
            throw new IllegalArgumentException("bridge limits");
        this.maximumSupplyVolts = maximumSupplyVolts; this.voltageDropVolts = voltageDropVolts;
        this.onResistanceOhms = onResistanceOhms; this.brakeResistanceOhms = brakeResistanceOhms;
        this.maximumCurrentAmps = maximumCurrentAmps; this.ambientTemperatureCelsius = ambientTemperatureCelsius;
        this.maximumTemperatureCelsius = maximumTemperatureCelsius;
        this.thermalCapacityJoulesPerKelvin = thermalCapacityJoulesPerKelvin;
        this.thermalResistanceKelvinPerWatt = thermalResistanceKelvinPerWatt;
    }
    public static HBridgeParameters educational() {
        return new HBridgeParameters(12.0, 0.8, 0.2, 0.35, 1.0, 25.0, 110.0, 12.0, 10.0);
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
