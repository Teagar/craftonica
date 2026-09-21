package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ComponentOrientation;

public final class PlacedComponent {
    public final String componentTypeId;
    public final int schemaVersion;
    public final ComponentOrientation orientation;

    public PlacedComponent(String componentTypeId, int schemaVersion, ComponentOrientation orientation) {
        if (componentTypeId == null || componentTypeId.length() == 0 || schemaVersion <= 0 || orientation == null)
            throw new IllegalArgumentException("placed component");
        this.componentTypeId = componentTypeId;
        this.schemaVersion = schemaVersion;
        this.orientation = orientation;
    }
}
