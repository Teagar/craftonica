package br.com.craftonica.electrical;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SimpleCircuitSolver {
    public static final int MAX_NETWORK_SIZE = 1024;
    public static final double LED_OVERCURRENT_AMPS = 0.030;

    public CircuitResult solve(CircuitGraph graph) {
        if (graph.size() > MAX_NETWORK_SIZE) {
            return result(CircuitStatus.NETWORK_TOO_LARGE, 0.0, 0.0, 0.0, "network_limit");
        }

        List<ElectricalComponent> sources = componentsOf(graph, ComponentKind.SOURCE);
        List<ElectricalComponent> grounds = componentsOf(graph, ComponentKind.GROUND);
        List<ElectricalComponent> leds = componentsOf(graph, ComponentKind.LED);
        if (sources.size() != 1 || grounds.size() != 1 || leds.size() != 1) {
            return result(CircuitStatus.UNSUPPORTED_TOPOLOGY, 0.0, 0.0, 0.0, "component_count");
        }

        ElectricalComponent source = sources.get(0);
        String groundId = grounds.get(0).getId();
        Set<String> reachable = reachableFrom(graph, source.getId());
        if (!reachable.contains(groundId)) {
            return result(CircuitStatus.OPEN_CIRCUIT, source.getSourceVoltage(), 0.0, 0.0, "no_return_path");
        }
        if (reachable.size() != graph.size() || !isSinglePath(graph, source.getId(), groundId)) {
            return result(CircuitStatus.UNSUPPORTED_TOPOLOGY, source.getSourceVoltage(), 0.0, 0.0, "not_series");
        }

        List<String> path = orderedPath(graph, source.getId(), groundId);
        if (path == null) {
            return result(CircuitStatus.UNSUPPORTED_TOPOLOGY, source.getSourceVoltage(), 0.0, 0.0, "cycle");
        }

        double resistance = 0.0;
        double forwardDrop = 0.0;
        boolean open = false;
        boolean reversed = false;
        for (int index = 1; index < path.size() - 1; index++) {
            ElectricalComponent component = graph.get(path.get(index));
            resistance += component.getResistanceOhms();
            if (component.getKind() == ComponentKind.SWITCH && !component.isClosed()) {
                open = true;
            }
            if (component.getKind() == ComponentKind.LED) {
                forwardDrop += component.getForwardVoltage();
                reversed = !path.get(index - 1).equals(component.getAnodeNeighborId());
            }
        }

        if (open) {
            return result(CircuitStatus.OPEN_CIRCUIT, source.getSourceVoltage(), 0.0, resistance, "switch_open");
        }
        if (reversed) {
            return result(CircuitStatus.REVERSED_POLARITY, source.getSourceVoltage(), 0.0, resistance, "led_reversed");
        }
        if (resistance <= 0.0) {
            return result(CircuitStatus.OVERCURRENT, source.getSourceVoltage(), Double.POSITIVE_INFINITY, 0.0, "missing_resistor");
        }

        double current = Math.max(0.0, source.getSourceVoltage() - forwardDrop) / resistance;
        CircuitStatus status = current > LED_OVERCURRENT_AMPS ? CircuitStatus.OVERCURRENT : CircuitStatus.CLOSED;
        return result(status, source.getSourceVoltage(), current, resistance,
                status == CircuitStatus.CLOSED ? "ok" : "led_overcurrent");
    }

    private List<ElectricalComponent> componentsOf(CircuitGraph graph, ComponentKind kind) {
        List<ElectricalComponent> result = new ArrayList<ElectricalComponent>();
        for (ElectricalComponent component : graph.components()) {
            if (component.getKind() == kind) {
                result.add(component);
            }
        }
        return result;
    }

    private Set<String> reachableFrom(CircuitGraph graph, String start) {
        Set<String> visited = new HashSet<String>();
        Deque<String> pending = new ArrayDeque<String>();
        pending.add(start);
        while (!pending.isEmpty() && visited.size() <= MAX_NETWORK_SIZE) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (String neighbor : graph.neighbors(current)) {
                if (!visited.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return visited;
    }

    private boolean isSinglePath(CircuitGraph graph, String sourceId, String groundId) {
        for (ElectricalComponent component : graph.components()) {
            int expectedDegree = component.getId().equals(sourceId) || component.getId().equals(groundId) ? 1 : 2;
            if (graph.neighbors(component.getId()).size() != expectedDegree) {
                return false;
            }
        }
        return true;
    }

    private List<String> orderedPath(CircuitGraph graph, String sourceId, String groundId) {
        List<String> path = new ArrayList<String>();
        Set<String> visited = new HashSet<String>();
        String previous = null;
        String current = sourceId;
        while (current != null && visited.add(current)) {
            path.add(current);
            if (current.equals(groundId)) {
                return path;
            }
            String next = null;
            for (String neighbor : graph.neighbors(current)) {
                if (!neighbor.equals(previous)) {
                    next = neighbor;
                    break;
                }
            }
            previous = current;
            current = next;
        }
        return null;
    }

    private CircuitResult result(CircuitStatus status, double voltage, double current,
                                 double resistance, String detail) {
        return new CircuitResult(status, voltage, current, resistance, detail);
    }
}
