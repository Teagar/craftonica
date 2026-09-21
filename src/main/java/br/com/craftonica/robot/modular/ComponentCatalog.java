package br.com.craftonica.robot.modular;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministically ordered immutable component catalog. */
public final class ComponentCatalog {
    private final Map<String, ComponentType> types;

    public ComponentCatalog(Collection<ComponentType> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("components");
        Map<String, ComponentType> copy = new LinkedHashMap<String, ComponentType>();
        for (ComponentType value : values) {
            if (value == null || copy.put(value.getId(), value) != null)
                throw new IllegalArgumentException("duplicate component");
        }
        types = Collections.unmodifiableMap(copy);
    }

    public ComponentType require(String id) {
        ComponentType value = types.get(id);
        if (value == null) throw new IllegalArgumentException("unknown component: " + id);
        return value;
    }

    public boolean contains(String id) { return types.containsKey(id); }
    public Collection<ComponentType> all() {
        return Collections.unmodifiableList(new ArrayList<ComponentType>(types.values()));
    }
}
