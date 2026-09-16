package br.com.craftonica.electrical;

public enum CircuitStatus {
    CLOSED,
    OPEN_CIRCUIT,
    REVERSED_POLARITY,
    OVERCURRENT,
    UNSUPPORTED_TOPOLOGY,
    NETWORK_TOO_LARGE
}
