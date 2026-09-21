package br.com.craftonica.robot.modular;

public final class ElectricalPort {
    public enum Domain { POWER, DIGITAL, ANALOG }
    public enum Flow { INPUT, OUTPUT, BIDIRECTIONAL, PASSIVE }

    public final String id;
    public final Domain domain;
    public final Flow flow;
    public final PortPose pose;
    public final double maximumVoltage;
    public final double maximumCurrentAmps;

    public ElectricalPort(String id, Domain domain, Flow flow, PortPose pose,
                          double maximumVoltage, double maximumCurrentAmps) {
        this.id = ContractValues.token(id, "electrical port id");
        if (domain == null || flow == null || pose == null) throw new IllegalArgumentException("electrical port");
        this.domain = domain;
        this.flow = flow;
        this.pose = pose;
        this.maximumVoltage = ContractValues.positive(maximumVoltage, "maximumVoltage");
        this.maximumCurrentAmps = ContractValues.positive(maximumCurrentAmps, "maximumCurrentAmps");
    }
}
