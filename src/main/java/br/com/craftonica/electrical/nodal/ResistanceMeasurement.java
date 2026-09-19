package br.com.craftonica.electrical.nodal;

public final class ResistanceMeasurement {
    public enum Status { VALID, OPEN_CIRCUIT, NONLINEAR_UNSUPPORTED, UNAVAILABLE }

    private final Status status;
    private final double ohms;

    private ResistanceMeasurement(Status status, double ohms) {
        this.status = status;
        this.ohms = ohms;
    }

    public static ResistanceMeasurement valid(double ohms) { return new ResistanceMeasurement(Status.VALID, ohms); }
    public static ResistanceMeasurement openCircuit() { return new ResistanceMeasurement(Status.OPEN_CIRCUIT, Double.POSITIVE_INFINITY); }
    public static ResistanceMeasurement nonlinearUnsupported() { return new ResistanceMeasurement(Status.NONLINEAR_UNSUPPORTED, Double.NaN); }
    public static ResistanceMeasurement unavailable() { return new ResistanceMeasurement(Status.UNAVAILABLE, Double.NaN); }

    public Status getStatus() { return status; }
    public double getOhms() { return ohms; }
}
