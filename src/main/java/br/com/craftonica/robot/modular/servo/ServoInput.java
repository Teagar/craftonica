package br.com.craftonica.robot.modular.servo;

public final class ServoInput {
    public enum SignalLossPolicy { HOLD, COAST, SAFE_POSITION }
    public final boolean wiringValid, powered;
    public final double supplyVolts;
    public final Integer pulseWidthMicros;
    public final SignalLossPolicy signalLossPolicy;
    public final double loadTorqueNm;

    public ServoInput(boolean wiringValid, boolean powered, double supplyVolts, Integer pulseWidthMicros,
            SignalLossPolicy policy, double loadTorqueNm) {
        if (Double.isNaN(supplyVolts) || Double.isInfinite(supplyVolts) || supplyVolts < 0.0
                || Double.isNaN(loadTorqueNm) || Double.isInfinite(loadTorqueNm) || policy == null)
            throw new IllegalArgumentException("servo input");
        this.wiringValid = wiringValid; this.powered = powered; this.supplyVolts = supplyVolts;
        this.pulseWidthMicros = pulseWidthMicros; this.signalLossPolicy = policy;
        this.loadTorqueNm = loadTorqueNm;
    }
}
