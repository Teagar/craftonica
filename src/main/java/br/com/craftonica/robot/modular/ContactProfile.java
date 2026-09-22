package br.com.craftonica.robot.modular;

import java.util.Map;

public final class ContactProfile extends ParameterizedProfile {
    public enum Kind { DRIVEN_WHEEL, PASSIVE_CASTER, DRIVEN_TRACK, OMNI_WHEEL, MECANUM_WHEEL }
    public final Kind kind;

    public ContactProfile(String id, int schemaVersion, Kind kind, Map<String, Double> parameters) {
        super(id, schemaVersion, parameters);
        if (kind == null) throw new IllegalArgumentException("kind");
        this.kind = kind;
    }
}
