package br.com.craftonica.electrical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CircuitGraph {
    private final Map<String, ElectricalComponent> components = new LinkedHashMap<String, ElectricalComponent>();
    private final Map<String, Set<String>> connections = new LinkedHashMap<String, Set<String>>();

    public CircuitGraph add(ElectricalComponent component) {
        if (components.containsKey(component.getId())) {
            throw new IllegalArgumentException("Duplicate component id: " + component.getId());
        }
        components.put(component.getId(), component);
        connections.put(component.getId(), new LinkedHashSet<String>());
        return this;
    }

    public CircuitGraph connect(String firstId, String secondId) {
        require(firstId);
        require(secondId);
        if (firstId.equals(secondId)) {
            throw new IllegalArgumentException("A component cannot connect to itself");
        }
        connections.get(firstId).add(secondId);
        connections.get(secondId).add(firstId);
        return this;
    }

    public int size() {
        return components.size();
    }

    public ElectricalComponent get(String id) {
        return components.get(id);
    }

    public Collection<ElectricalComponent> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    public List<String> neighbors(String id) {
        require(id);
        return Collections.unmodifiableList(new ArrayList<String>(connections.get(id)));
    }

    private void require(String id) {
        if (!components.containsKey(id)) {
            throw new IllegalArgumentException("Unknown component id: " + id);
        }
    }
}
