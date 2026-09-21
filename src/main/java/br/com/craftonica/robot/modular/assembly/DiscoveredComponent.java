package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.ComponentType;
import br.com.craftonica.robot.modular.GridVector;

public final class DiscoveredComponent {
    public final GridVector worldPosition;
    public final GridVector localPosition;
    public final ComponentOrientation localOrientation;
    public final ComponentType type;

    DiscoveredComponent(GridVector worldPosition, GridVector localPosition,
                        ComponentOrientation localOrientation, ComponentType type) {
        this.worldPosition = worldPosition;
        this.localPosition = localPosition;
        this.localOrientation = localOrientation;
        this.type = type;
    }
}
