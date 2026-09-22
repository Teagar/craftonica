package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.ComponentType;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.MechanicalPort;
import br.com.craftonica.robot.modular.JointPort;
import br.com.craftonica.robot.modular.StructuralPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic bounded discovery of physically attached component blocks. */
public final class AssemblyDiscovery {
    private static final Comparator<GridVector> POSITION_ORDER = new Comparator<GridVector>() {
        @Override public int compare(GridVector a, GridVector b) {
            if (a.x != b.x) return a.x < b.x ? -1 : 1;
            if (a.y != b.y) return a.y < b.y ? -1 : 1;
            return a.z == b.z ? 0 : a.z < b.z ? -1 : 1;
        }
    };

    private final ComponentCatalog catalog;
    private final AssemblyLimits limits;

    public AssemblyDiscovery(ComponentCatalog catalog, AssemblyLimits limits) {
        if (catalog == null || limits == null) throw new IllegalArgumentException("discovery dependencies");
        this.catalog = catalog;
        this.limits = limits;
    }

    public AssemblyDiscoveryResult discover(AssemblyWorldView world, GridVector anchor) {
        if (world == null || anchor == null) throw new IllegalArgumentException("world or anchor");
        if (!world.isLoaded(anchor)) return failure(AssemblyDiscoveryResult.Status.ANCHOR_UNLOADED, anchor, null);
        PlacedComponent root = world.componentAt(anchor);
        if (root == null) return failure(AssemblyDiscoveryResult.Status.ANCHOR_NOT_COMPONENT, anchor, null);
        AssemblyDiscoveryResult invalidRoot = validate(root, anchor);
        if (invalidRoot != null) return invalidRoot;

        TreeSet<GridVector> frontier = new TreeSet<GridVector>(POSITION_ORDER);
        Set<GridVector> processed = new HashSet<GridVector>();
        Set<GridVector> inspected = new HashSet<GridVector>();
        Map<GridVector, PlacedComponent> found = new HashMap<GridVector, PlacedComponent>();
        Map<String, AssemblyEdge> edges = new HashMap<String, AssemblyEdge>();
        frontier.add(anchor);
        found.put(anchor, root);
        inspected.add(anchor);

        while (!frontier.isEmpty()) {
            GridVector position = frontier.pollFirst();
            if (!processed.add(position)) continue;
            PlacedComponent placed = found.get(position);
            ComponentType type = catalog.require(placed.componentTypeId);
            GridVector local = root.orientation.toLocal(position.subtract(anchor));
            if (outside(local)) return failure(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED,
                    position, "maximum extent");
            if (processed.size() > limits.maximumComponents)
                return failure(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED, position, "maximum components");

            for (StructuralPort port : type.getStructuralPorts()) {
                Direction worldFace = placed.orientation.toWorld(port.pose.face);
                AssemblyDiscoveryResult result = inspectStructural(world, anchor, root, position, placed,
                        port, worldFace, frontier, inspected, found, edges);
                if (result != null) return result;
            }
            for (MechanicalPort port : type.getMechanicalPorts()) {
                Direction worldFace = placed.orientation.toWorld(port.pose.face);
                AssemblyDiscoveryResult result = inspectMechanical(world, anchor, root, position, placed,
                        port, worldFace, frontier, inspected, found, edges);
                if (result != null) return result;
            }
            for (JointPort port : type.getJointPorts()) {
                Direction worldFace = placed.orientation.toWorld(port.pose.face);
                AssemblyDiscoveryResult result = inspectJoint(world, anchor, root, position, placed,
                        port, worldFace, frontier, inspected, found, edges);
                if (result != null) return result;
            }
        }

        List<DiscoveredComponent> components = new ArrayList<DiscoveredComponent>();
        for (Map.Entry<GridVector, PlacedComponent> entry : found.entrySet()) {
            GridVector local = root.orientation.toLocal(entry.getKey().subtract(anchor));
            ComponentOrientation relative = new ComponentOrientation(
                    root.orientation.toLocal(entry.getValue().orientation.getForward()),
                    root.orientation.toLocal(entry.getValue().orientation.getUp()));
            components.add(new DiscoveredComponent(entry.getKey(), local, relative,
                    catalog.require(entry.getValue().componentTypeId)));
        }
        Collections.sort(components, new Comparator<DiscoveredComponent>() {
            @Override public int compare(DiscoveredComponent a, DiscoveredComponent b) {
                int position = POSITION_ORDER.compare(a.localPosition, b.localPosition);
                return position != 0 ? position : a.type.getId().compareTo(b.type.getId());
            }
        });
        List<AssemblyEdge> canonicalEdges = new ArrayList<AssemblyEdge>();
        for (AssemblyEdge edge : edges.values()) canonicalEdges.add(new AssemblyEdge(edge.kind,
                root.orientation.toLocal(edge.firstPosition.subtract(anchor)), edge.firstPort,
                root.orientation.toLocal(edge.secondPosition.subtract(anchor)), edge.secondPort));
        Collections.sort(canonicalEdges, new Comparator<AssemblyEdge>() {
            @Override public int compare(AssemblyEdge a, AssemblyEdge b) {
                return a.canonicalKey().compareTo(b.canonicalKey());
            }
        });
        return AssemblyDiscoveryResult.valid(new AssemblyGraph(anchor, root.orientation, components, canonicalEdges));
    }

    private AssemblyDiscoveryResult inspectStructural(AssemblyWorldView world, GridVector anchor,
            PlacedComponent root, GridVector position, PlacedComponent placed, StructuralPort port,
            Direction worldFace, TreeSet<GridVector> frontier, Set<GridVector> inspected,
            Map<GridVector, PlacedComponent> found, Map<String, AssemblyEdge> edges) {
        GridVector neighborPosition = position.add(worldFace.vector);
        AssemblyDiscoveryResult ready = inspect(world, anchor, root, neighborPosition, frontier, inspected, found);
        if (ready != null) return ready;
        PlacedComponent neighbor = found.get(neighborPosition);
        if (neighbor == null) neighbor = world.componentAt(neighborPosition);
        if (neighbor == null) return null;
        ComponentType neighborType = catalog.require(neighbor.componentTypeId);
        for (StructuralPort candidate : neighborType.getStructuralPorts()) {
            if (port.connectorFamily.equals(candidate.connectorFamily)
                    && neighbor.orientation.toWorld(candidate.pose.face) == worldFace.opposite()) {
                return connect(position, port.id, neighborPosition, candidate.id,
                        AssemblyEdge.Kind.STRUCTURAL, neighbor, frontier, found, edges);
            }
        }
        for (JointPort candidate : neighborType.getJointPorts()) {
            if (port.connectorFamily.equals(candidate.connectorFamily)
                    && neighbor.orientation.toWorld(candidate.pose.face) == worldFace.opposite()) {
                return connect(position, port.id, neighborPosition, candidate.id,
                        AssemblyEdge.Kind.JOINT, neighbor, frontier, found, edges);
            }
        }
        return null;
    }

    private AssemblyDiscoveryResult inspectJoint(AssemblyWorldView world, GridVector anchor,
            PlacedComponent root, GridVector position, PlacedComponent placed, JointPort port,
            Direction worldFace, TreeSet<GridVector> frontier, Set<GridVector> inspected,
            Map<GridVector, PlacedComponent> found, Map<String, AssemblyEdge> edges) {
        GridVector neighborPosition = position.add(worldFace.vector);
        AssemblyDiscoveryResult ready = inspect(world, anchor, root, neighborPosition, frontier, inspected, found);
        if (ready != null) return ready;
        PlacedComponent neighbor = found.get(neighborPosition);
        if (neighbor == null) neighbor = world.componentAt(neighborPosition);
        if (neighbor == null) return null;
        ComponentType neighborType = catalog.require(neighbor.componentTypeId);
        for (StructuralPort candidate : neighborType.getStructuralPorts()) {
            if (port.connectorFamily.equals(candidate.connectorFamily)
                    && neighbor.orientation.toWorld(candidate.pose.face) == worldFace.opposite()) {
                return connect(position, port.id, neighborPosition, candidate.id,
                        AssemblyEdge.Kind.JOINT, neighbor, frontier, found, edges);
            }
        }
        return null;
    }

    private AssemblyDiscoveryResult inspectMechanical(AssemblyWorldView world, GridVector anchor,
            PlacedComponent root, GridVector position, PlacedComponent placed, MechanicalPort port,
            Direction worldFace, TreeSet<GridVector> frontier, Set<GridVector> inspected,
            Map<GridVector, PlacedComponent> found, Map<String, AssemblyEdge> edges) {
        GridVector neighborPosition = position.add(worldFace.vector);
        AssemblyDiscoveryResult ready = inspect(world, anchor, root, neighborPosition, frontier, inspected, found);
        if (ready != null) return ready;
        PlacedComponent neighbor = found.get(neighborPosition);
        if (neighbor == null) neighbor = world.componentAt(neighborPosition);
        if (neighbor == null) return null;
        ComponentType neighborType = catalog.require(neighbor.componentTypeId);
        for (MechanicalPort candidate : neighborType.getMechanicalPorts()) {
            if (mechanicallyCompatible(port, placed.orientation, candidate, neighbor.orientation)
                    && neighbor.orientation.toWorld(candidate.pose.face) == worldFace.opposite()) {
                return connect(position, port.id, neighborPosition, candidate.id,
                        AssemblyEdge.Kind.MECHANICAL, neighbor, frontier, found, edges);
            }
        }
        return null;
    }

    private AssemblyDiscoveryResult inspect(AssemblyWorldView world, GridVector anchor, PlacedComponent root,
            GridVector position, TreeSet<GridVector> frontier, Set<GridVector> inspected,
            Map<GridVector, PlacedComponent> found) {
        if (inspected.add(position) && inspected.size() > limits.maximumInspectedPositions)
            return failure(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED, position, "maximum inspected positions");
        if (!world.isLoaded(position))
            return failure(AssemblyDiscoveryResult.Status.UNLOADED_BOUNDARY, position, null);
        if (found.containsKey(position)) return null;
        PlacedComponent value = world.componentAt(position);
        if (value == null) return null;
        AssemblyDiscoveryResult invalid = validate(value, position);
        if (invalid != null) return invalid;
        GridVector local = root.orientation.toLocal(position.subtract(anchor));
        if (outside(local)) return failure(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED,
                position, "maximum extent");
        return null;
    }

    private AssemblyDiscoveryResult connect(GridVector firstPosition, String firstPort,
            GridVector secondPosition, String secondPort, AssemblyEdge.Kind kind,
            PlacedComponent second, TreeSet<GridVector> frontier,
            Map<GridVector, PlacedComponent> found, Map<String, AssemblyEdge> edges) {
        AssemblyEdge edge = new AssemblyEdge(kind, firstPosition, firstPort, secondPosition, secondPort);
        edges.put(edge.canonicalKey(), edge);
        if (edges.size() > limits.maximumEdges)
            return failure(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED, secondPosition, "maximum edges");
        found.put(secondPosition, second);
        frontier.add(secondPosition);
        return null;
    }

    private AssemblyDiscoveryResult validate(PlacedComponent value, GridVector position) {
        if (!catalog.contains(value.componentTypeId))
            return failure(AssemblyDiscoveryResult.Status.UNKNOWN_COMPONENT, position, value.componentTypeId);
        ComponentType type = catalog.require(value.componentTypeId);
        if (type.getSchemaVersion() != value.schemaVersion)
            return failure(AssemblyDiscoveryResult.Status.UNSUPPORTED_SCHEMA, position,
                    value.componentTypeId + ":" + value.schemaVersion);
        return null;
    }

    private boolean outside(GridVector local) {
        return StrictMath.abs(local.x) > limits.maximumExtentPerAxis
                || StrictMath.abs(local.y) > limits.maximumExtentPerAxis
                || StrictMath.abs(local.z) > limits.maximumExtentPerAxis;
    }

    private static boolean mechanicallyCompatible(MechanicalPort a, ComponentOrientation aOrientation,
                                                   MechanicalPort b, ComponentOrientation bOrientation) {
        boolean coupling = a.coupling == MechanicalPort.Coupling.NEUTRAL
                || b.coupling == MechanicalPort.Coupling.NEUTRAL
                || (a.coupling == MechanicalPort.Coupling.PLUG && b.coupling == MechanicalPort.Coupling.SOCKET)
                || (a.coupling == MechanicalPort.Coupling.SOCKET && b.coupling == MechanicalPort.Coupling.PLUG);
        boolean kind = a.kind == b.kind
                || (a.kind == MechanicalPort.Kind.ROTARY_SHAFT && b.kind == MechanicalPort.Kind.WHEEL_HUB)
                || (a.kind == MechanicalPort.Kind.WHEEL_HUB && b.kind == MechanicalPort.Kind.ROTARY_SHAFT);
        Direction aAxis = aOrientation.toWorld(a.axis);
        Direction bAxis = bOrientation.toWorld(b.axis);
        return coupling && kind && (aAxis == bAxis || aAxis == bAxis.opposite());
    }

    private static AssemblyDiscoveryResult failure(AssemblyDiscoveryResult.Status status,
                                                     GridVector position, String detail) {
        return AssemblyDiscoveryResult.failure(status, position, detail);
    }
}
