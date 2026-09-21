package br.com.craftonica.robot.modular;

import java.util.Map;

public final class ActuatorProfile extends ParameterizedProfile {
    public enum Kind { DC_MOTOR, H_BRIDGE }
    public final Kind kind;

    public ActuatorProfile(String id, int schemaVersion, Kind kind, Map<String, Double> parameters) {
        super(id, schemaVersion, parameters);
        if (kind == null) throw new IllegalArgumentException("kind");
        this.kind = kind;
    }
}
