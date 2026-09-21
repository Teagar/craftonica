package br.com.craftonica.robot.modular;

public final class StructuralPort {
    public final String id;
    public final String connectorFamily;
    public final PortPose pose;

    public StructuralPort(String id, String connectorFamily, PortPose pose) {
        this.id = ContractValues.token(id, "structural port id");
        this.connectorFamily = ContractValues.id(connectorFamily, "connector family");
        if (pose == null) throw new IllegalArgumentException("pose");
        this.pose = pose;
    }
}
