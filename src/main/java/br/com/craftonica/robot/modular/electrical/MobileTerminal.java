package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;

public final class MobileTerminal {
    public final GridVector modulePosition;
    public final String componentTypeId;
    public final String portId;
    public final Direction face;
    public final String role;
    public final int networkId;

    public MobileTerminal(GridVector modulePosition, String componentTypeId, String portId,
                          Direction face, String role, int networkId) {
        if (modulePosition == null || componentTypeId == null || componentTypeId.length() == 0
                || portId == null || portId.length() == 0 || face == null || networkId < 0)
            throw new IllegalArgumentException("mobile terminal");
        this.modulePosition = modulePosition; this.componentTypeId = componentTypeId;
        this.portId = portId; this.face = face; this.role = role == null ? "" : role;
        this.networkId = networkId;
    }

    public String key() { return modulePosition + "/" + portId; }
}
