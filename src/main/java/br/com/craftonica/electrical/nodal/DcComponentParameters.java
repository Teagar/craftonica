package br.com.craftonica.electrical.nodal;

/** Defaults for the educational DC component models. This class is Forge-free. */
public final class DcComponentParameters {
    public static final double SOURCE_VOLTAGE = 5.0;
    public static final double SOURCE_INTERNAL_RESISTANCE_OHMS = 10.0;
    public static final double DIODE_FORWARD_VOLTAGE = 2.0;
    public static final double DIODE_DYNAMIC_RESISTANCE_OHMS = 1.0;
    private DcComponentParameters() { }
}
