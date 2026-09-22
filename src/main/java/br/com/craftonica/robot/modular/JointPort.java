package br.com.craftonica.robot.modular;

/** One physical side of a joint component, connected to a rigid body mount. */
public final class JointPort {
    public enum Role { PARENT, CHILD }
    public final String id;
    public final String connectorFamily;
    public final Role role;
    public final PortPose pose;

    public JointPort(String id, String connectorFamily, Role role, PortPose pose) {
        this.id = ContractValues.token(id, "joint port id");
        this.connectorFamily = ContractValues.id(connectorFamily, "joint connector family");
        if (role == null || pose == null) throw new IllegalArgumentException("joint port");
        this.role = role; this.pose = pose;
    }
}
