package br.com.craftonica.electrical;

public final class MultimeterReading {
    public enum Kind {
        VALUE,
        CONTINUITY,
        NO_CONTINUITY,
        OVERCURRENT,
        UNSUPPORTED
    }

    private final Kind kind;
    private final double value;

    private MultimeterReading(Kind kind, double value) {
        this.kind = kind;
        this.value = value;
    }

    public static MultimeterReading fromNetworkResult(MultimeterMode mode, CircuitResult result) {
        if (result.getStatus() == CircuitStatus.UNSUPPORTED_TOPOLOGY
                || result.getStatus() == CircuitStatus.NETWORK_TOO_LARGE) {
            return new MultimeterReading(Kind.UNSUPPORTED, 0.0);
        }
        switch (mode) {
            case VOLTAGE:
                return new MultimeterReading(Kind.VALUE, result.getSourceVoltage());
            case CURRENT:
                return Double.isInfinite(result.getCurrentAmps()) || Double.isNaN(result.getCurrentAmps())
                        ? new MultimeterReading(Kind.OVERCURRENT, 0.0)
                        : new MultimeterReading(Kind.VALUE, result.getCurrentAmps() * 1000.0);
            case RESISTANCE:
                return new MultimeterReading(Kind.VALUE, result.getEquivalentResistanceOhms());
            case CONTINUITY:
                boolean continuous = result.getStatus() == CircuitStatus.CLOSED
                        || result.getStatus() == CircuitStatus.OVERCURRENT;
                return new MultimeterReading(continuous ? Kind.CONTINUITY : Kind.NO_CONTINUITY, 0.0);
            default:
                throw new IllegalArgumentException("Unknown multimeter mode: " + mode);
        }
    }

    public Kind getKind() {
        return kind;
    }

    public double getValue() {
        return value;
    }
}
