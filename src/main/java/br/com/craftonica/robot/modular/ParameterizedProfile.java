package br.com.craftonica.robot.modular;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ParameterizedProfile {
    private final String id;
    private final int schemaVersion;
    private final Map<String, Double> parameters;

    ParameterizedProfile(String id, int schemaVersion, Map<String, Double> parameters) {
        this.id = ContractValues.id(id, "profile id");
        if (schemaVersion <= 0 || parameters == null) throw new IllegalArgumentException("profile");
        this.schemaVersion = schemaVersion;
        Map<String, Double> copy = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : parameters.entrySet()) {
            String name = ContractValues.token(entry.getKey(), "parameter name");
            if (entry.getValue() == null || copy.containsKey(name)) throw new IllegalArgumentException("parameter");
            copy.put(name, ContractValues.nonNegative(entry.getValue().doubleValue(), name));
        }
        if (copy.isEmpty()) throw new IllegalArgumentException("parameters");
        this.parameters = Collections.unmodifiableMap(copy);
    }

    public final String getId() { return id; }
    public final int getSchemaVersion() { return schemaVersion; }
    public final Map<String, Double> getParameters() { return parameters; }
}
