package br.com.craftonica.robot.modular;

import java.util.Map;

public final class SensorProfile extends ParameterizedProfile {
    public enum Kind { ULTRASONIC }
    public final Kind kind;

    public SensorProfile(String id, int schemaVersion, Kind kind, Map<String, Double> parameters) {
        super(id, schemaVersion, parameters);
        if (kind == null) throw new IllegalArgumentException("kind");
        this.kind = kind;
    }
}
