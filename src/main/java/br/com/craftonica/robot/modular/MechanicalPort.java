package br.com.craftonica.robot.modular;

public final class MechanicalPort {
    public enum Kind { ROTARY_SHAFT, WHEEL_HUB, CASTER_MOUNT }
    public enum Coupling { NEUTRAL, PLUG, SOCKET }

    public final String id;
    public final Kind kind;
    public final Coupling coupling;
    public final PortPose pose;
    public final Direction axis;
    public final double maximumTorqueNewtonMetres;

    public MechanicalPort(String id, Kind kind, Coupling coupling, PortPose pose,
                          Direction axis, double maximumTorqueNewtonMetres) {
        this.id = ContractValues.token(id, "mechanical port id");
        if (kind == null || coupling == null || pose == null || axis == null)
            throw new IllegalArgumentException("mechanical port");
        this.kind = kind;
        this.coupling = coupling;
        this.pose = pose;
        this.axis = axis;
        this.maximumTorqueNewtonMetres = ContractValues.positive(maximumTorqueNewtonMetres,
                "maximumTorqueNewtonMetres");
    }
}
