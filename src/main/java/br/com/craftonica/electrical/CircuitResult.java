package br.com.craftonica.electrical;

public final class CircuitResult {
    private final CircuitStatus status;
    private final double sourceVoltage;
    private final double currentAmps;
    private final double equivalentResistanceOhms;
    private final String detail;

    public CircuitResult(CircuitStatus status, double sourceVoltage, double currentAmps,
                         double equivalentResistanceOhms, String detail) {
        this.status = status;
        this.sourceVoltage = sourceVoltage;
        this.currentAmps = currentAmps;
        this.equivalentResistanceOhms = equivalentResistanceOhms;
        this.detail = detail;
    }

    public CircuitStatus getStatus() {
        return status;
    }

    public double getSourceVoltage() {
        return sourceVoltage;
    }

    public double getCurrentAmps() {
        return currentAmps;
    }

    public double getEquivalentResistanceOhms() {
        return equivalentResistanceOhms;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isEnergized() {
        return status == CircuitStatus.CLOSED || status == CircuitStatus.OVERCURRENT;
    }
}
