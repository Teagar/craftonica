package br.com.craftonica.electrical;

public final class BasicElectricalComponent implements ElectricalComponent {
    private final String id;
    private final ComponentKind kind;
    private final double resistanceOhms;
    private final double sourceVoltage;
    private final double forwardVoltage;
    private final boolean closed;
    private final String anodeNeighborId;

    private BasicElectricalComponent(String id, ComponentKind kind, double resistanceOhms,
                                     double sourceVoltage, double forwardVoltage, boolean closed,
                                     String anodeNeighborId) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id is required");
        }
        this.id = id;
        this.kind = kind;
        this.resistanceOhms = resistanceOhms;
        this.sourceVoltage = sourceVoltage;
        this.forwardVoltage = forwardVoltage;
        this.closed = closed;
        this.anodeNeighborId = anodeNeighborId;
    }

    public static BasicElectricalComponent wire(String id) {
        return new BasicElectricalComponent(id, ComponentKind.WIRE, 0.0, 0.0, 0.0, true, null);
    }

    public static BasicElectricalComponent source(String id, double voltage) {
        return new BasicElectricalComponent(id, ComponentKind.SOURCE, 0.0, voltage, 0.0, true, null);
    }

    public static BasicElectricalComponent ground(String id) {
        return new BasicElectricalComponent(id, ComponentKind.GROUND, 0.0, 0.0, 0.0, true, null);
    }

    public static BasicElectricalComponent electricalSwitch(String id, boolean closed) {
        return new BasicElectricalComponent(id, ComponentKind.SWITCH, 0.0, 0.0, 0.0, closed, null);
    }

    public static BasicElectricalComponent resistor(String id, double resistanceOhms) {
        if (resistanceOhms <= 0.0) {
            throw new IllegalArgumentException("Resistance must be positive");
        }
        return new BasicElectricalComponent(id, ComponentKind.RESISTOR, resistanceOhms, 0.0, 0.0, true, null);
    }

    public static BasicElectricalComponent led(String id, double forwardVoltage, String anodeNeighborId) {
        if (forwardVoltage < 0.0 || anodeNeighborId == null) {
            throw new IllegalArgumentException("LED voltage and anode connection are required");
        }
        return new BasicElectricalComponent(id, ComponentKind.LED, 0.0, 0.0, forwardVoltage, true, anodeNeighborId);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ComponentKind getKind() {
        return kind;
    }

    @Override
    public double getResistanceOhms() {
        return resistanceOhms;
    }

    @Override
    public double getSourceVoltage() {
        return sourceVoltage;
    }

    @Override
    public double getForwardVoltage() {
        return forwardVoltage;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String getAnodeNeighborId() {
        return anodeNeighborId;
    }
}
