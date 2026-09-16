package br.com.craftonica.electrical;

public final class CircuitDiagnosis {
    private CircuitDiagnosis() {
    }

    public static String translationKey(CircuitResult result) {
        if (result.getStatus() == CircuitStatus.OPEN_CIRCUIT && "led_burned".equals(result.getDetail())) {
            return "message.craftonica.multimeter.burned";
        }
        switch (result.getStatus()) {
            case CLOSED:
                return "message.craftonica.multimeter.closed";
            case OPEN_CIRCUIT:
                return "message.craftonica.multimeter.open";
            case REVERSED_POLARITY:
                return "message.craftonica.multimeter.reversed";
            case OVERCURRENT:
                return "message.craftonica.multimeter.overcurrent";
            case NETWORK_TOO_LARGE:
                return "message.craftonica.multimeter.too_large";
            default:
                return "message.craftonica.multimeter.unsupported";
        }
    }

    public static boolean hasMeasurements(CircuitResult result) {
        return result.getStatus() != CircuitStatus.UNSUPPORTED_TOPOLOGY
                && result.getStatus() != CircuitStatus.NETWORK_TOO_LARGE
                && !Double.isInfinite(result.getCurrentAmps())
                && !Double.isNaN(result.getCurrentAmps());
    }
}
