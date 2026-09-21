package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssemblyGraph {
    public final GridVector anchorWorldPosition;
    public final ComponentOrientation anchorOrientation;
    private final List<DiscoveredComponent> components;
    private final List<AssemblyEdge> edges;

    AssemblyGraph(GridVector anchorWorldPosition, ComponentOrientation anchorOrientation,
                  List<DiscoveredComponent> components, List<AssemblyEdge> edges) {
        this.anchorWorldPosition = anchorWorldPosition;
        this.anchorOrientation = anchorOrientation;
        this.components = Collections.unmodifiableList(new ArrayList<DiscoveredComponent>(components));
        this.edges = Collections.unmodifiableList(new ArrayList<AssemblyEdge>(edges));
    }

    public List<DiscoveredComponent> getComponents() { return components; }
    public List<AssemblyEdge> getEdges() { return edges; }

    public int edgeCount(AssemblyEdge.Kind kind) {
        int count = 0;
        for (AssemblyEdge edge : edges) if (edge.kind == kind) count++;
        return count;
    }
}
