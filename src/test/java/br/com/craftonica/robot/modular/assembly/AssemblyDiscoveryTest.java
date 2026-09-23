package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public final class AssemblyDiscoveryTest {
    private final ComponentCatalog catalog = StandardComponentCatalog.create();

    @Test public void straightWideAndAsymmetricShapesUseTheSameDiscovery() {
        AssemblyDiscovery discovery = new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1);
        FakeWorld straight = loadedWorld();
        placeChassis(straight, 0, 0, 0); placeChassis(straight, 1, 0, 0); placeChassis(straight, 2, 0, 0);
        FakeWorld wide = loadedWorld();
        placeChassis(wide, 0, 0, 0); placeChassis(wide, -1, 0, 0); placeChassis(wide, 1, 0, 0);
        placeChassis(wide, 0, 0, -1); placeChassis(wide, 0, 0, 1);
        FakeWorld asymmetric = loadedWorld();
        placeChassis(asymmetric, 0, 0, 0); placeChassis(asymmetric, 0, 1, 0);
        placeChassis(asymmetric, 1, 1, 0); placeChassis(asymmetric, 1, 1, 1);

        AssemblyGraph a = valid(discovery.discover(straight, p(0, 0, 0)));
        AssemblyGraph b = valid(discovery.discover(wide, p(0, 0, 0)));
        AssemblyGraph c = valid(discovery.discover(asymmetric, p(0, 0, 0)));
        assertEquals(3, a.getComponents().size());
        assertEquals(5, b.getComponents().size());
        assertEquals(4, c.getComponents().size());
        assertNotEquals(localKey(a), localKey(b));
        assertNotEquals(localKey(b), localKey(c));
        assertEquals(2, a.edgeCount(AssemblyEdge.Kind.STRUCTURAL));
    }

    @Test public void anchorOrientationDefinesCanonicalLocalCoordinates() {
        FakeWorld world = loadedWorld();
        world.put(p(10, 5, 10), StandardComponentCatalog.CHASSIS,
                new ComponentOrientation(Direction.EAST, Direction.UP));
        world.put(p(11, 5, 10), StandardComponentCatalog.CHASSIS,
                new ComponentOrientation(Direction.SOUTH, Direction.UP));
        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(10, 5, 10)));
        assertEquals("0:0:-1,0:0:0", localKey(graph));
        assertEquals(Direction.EAST, graph.getComponents().get(0).localOrientation.getForward());
        AssemblyEdge edge = graph.getEdges().get(0);
        assertTrue(GridVector.ZERO.equals(edge.firstPosition) || GridVector.ZERO.equals(edge.secondPosition));
    }

    @Test public void unloadedBoundaryStopsWithoutReadingIt() {
        FakeWorld world = new FakeWorld();
        GridVector anchor = p(0, 5, 0);
        world.put(anchor, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP);
        for (Direction direction : Direction.values()) world.loaded.add(anchor.add(direction.vector));
        GridVector east = anchor.add(Direction.EAST.vector);
        world.loaded.remove(east);
        AssemblyDiscoveryResult result = new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, anchor);
        assertEquals(AssemblyDiscoveryResult.Status.UNLOADED_BOUNDARY, result.status);
        assertEquals(east, result.diagnosticPosition);
        assertFalse(world.reads.contains(east));
    }

    @Test public void componentAndExtentLimitsFailExplicitly() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0); placeChassis(world, 1, 0, 0); placeChassis(world, 2, 0, 0);
        AssemblyDiscoveryResult count = new AssemblyDiscovery(catalog, new AssemblyLimits(2, 20, 20, 10))
                .discover(world, p(0, 0, 0));
        assertEquals(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED, count.status);
        assertEquals("maximum components", count.detail);
        AssemblyDiscoveryResult extent = new AssemblyDiscovery(catalog, new AssemblyLimits(10, 20, 20, 1))
                .discover(world, p(0, 0, 0));
        assertEquals(AssemblyDiscoveryResult.Status.LIMIT_EXCEEDED, extent.status);
        assertEquals("maximum extent", extent.detail);
    }

    @Test public void disconnectedStructureIsNotCaptured() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0); placeChassis(world, 1, 0, 0);
        placeChassis(world, 8, 0, 0); placeChassis(world, 9, 0, 0);
        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));
        assertEquals(2, graph.getComponents().size());
        for (DiscoveredComponent component : graph.getComponents()) assertTrue(component.worldPosition.x < 8);
    }

    @Test public void incompatibleAdjacentComponentIsNotCapturedByProximity() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0);
        world.put(p(1, 0, 0), StandardComponentCatalog.POWER_SOURCE, ComponentOrientation.NORTH_UP);
        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));
        assertEquals(1, graph.getComponents().size());
    }

    @Test public void mechanicalChainCanJoinWheelWithoutBlueprintRule() {
        FakeWorld world = loadedWorld();
        world.put(p(0, 0, 0), StandardComponentCatalog.DC_MOTOR, ComponentOrientation.NORTH_UP);
        world.put(p(0, 0, 1), StandardComponentCatalog.AXLE, ComponentOrientation.NORTH_UP);
        world.put(p(0, 0, 2), StandardComponentCatalog.WHEEL,
                new ComponentOrientation(Direction.EAST, Direction.UP));
        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));
        assertEquals(3, graph.getComponents().size());
        assertEquals(2, graph.edgeCount(AssemblyEdge.Kind.MECHANICAL));
    }

    @Test public void electricalLeadOnMotorTerminalIsCapturedWithoutStructuralMount() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0);
        world.put(p(0, 1, 0), StandardComponentCatalog.DC_MOTOR, ComponentOrientation.NORTH_UP);
        world.put(p(0, 2, 0), StandardComponentCatalog.WIRE, ComponentOrientation.NORTH_UP);

        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));

        assertEquals(3, graph.getComponents().size());
        assertEquals(1, graph.edgeCount(AssemblyEdge.Kind.ELECTRICAL));
    }

    @Test public void jointPortsDiscoverHorizontalAndVerticalParentChildBodies() {
        FakeWorld horizontal = loadedWorld();
        placeChassis(horizontal, 0, 0, 0);
        horizontal.put(p(0, 0, 1), StandardComponentCatalog.REVOLUTE_JOINT,
                ComponentOrientation.NORTH_UP);
        placeChassis(horizontal, 0, 0, 2);
        AssemblyGraph horizontalGraph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(horizontal, p(0, 0, 0)));
        assertEquals(2, horizontalGraph.edgeCount(AssemblyEdge.Kind.JOINT));
        assertTrue(KinematicAssemblyAnalyzer.analyze(horizontalGraph).isValid());

        FakeWorld vertical = loadedWorld();
        placeChassis(vertical, 0, 0, 0);
        vertical.put(p(0, 1, 0), StandardComponentCatalog.PRISMATIC_JOINT,
                new ComponentOrientation(Direction.DOWN, Direction.NORTH));
        placeChassis(vertical, 0, 2, 0);
        AssemblyGraph verticalGraph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(vertical, p(0, 0, 0)));
        assertEquals(2, verticalGraph.edgeCount(AssemblyEdge.Kind.JOINT));
        KinematicAssembly kinematic = KinematicAssemblyAnalyzer.analyze(verticalGraph);
        assertTrue(kinematic.isValid());
        assertEquals(Direction.DOWN, kinematic.getJoints().get(0).axis);
    }

    @Test public void mountedServoConnectsToHingeOnlyThroughMatchingMechanicalPorts() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0); placeChassis(world, 0, 0, 2);
        world.put(p(0, 0, 1), StandardComponentCatalog.REVOLUTE_JOINT,
                ComponentOrientation.NORTH_UP);
        world.put(p(-1, 0, 1), StandardComponentCatalog.SERVO,
                new ComponentOrientation(Direction.EAST, Direction.UP));
        placeChassis(world, -1, -1, 1); placeChassis(world, -1, -1, 0);
        placeChassis(world, 0, -1, 0);

        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));
        assertEquals(1, graph.edgeCount(AssemblyEdge.Kind.MECHANICAL));
        KinematicAssembly kinematic = KinematicAssemblyAnalyzer.analyze(graph);
        assertTrue(kinematic.getDiagnostics().toString(), kinematic.isValid());
        assertTrue(kinematic.getJoints().get(0).hasDriveConnection());
        assertEquals(new GridVector(-1, 0, 1), kinematic.getJoints().get(0).driveConnectionPosition);
    }

    @Test public void physicalBridgeTerminalConnectsCoreToSupportedStructure() {
        FakeWorld world = loadedWorld();
        placeChassis(world, 0, 0, 0);
        world.put(p(0, 1, 0), StandardComponentCatalog.H_BRIDGE_TERMINAL,
                ComponentOrientation.NORTH_UP);
        world.put(p(0, 1, 1), StandardComponentCatalog.H_BRIDGE,
                ComponentOrientation.NORTH_UP);
        AssemblyGraph graph = valid(new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1)
                .discover(world, p(0, 0, 0)));
        assertEquals(3, graph.getComponents().size());
        assertEquals(2, graph.edgeCount(AssemblyEdge.Kind.STRUCTURAL));
    }

    @Test public void unknownAndWrongSchemaAreRejected() {
        FakeWorld unknown = loadedWorld();
        unknown.put(p(0, 0, 0), "other:part", ComponentOrientation.NORTH_UP);
        assertEquals(AssemblyDiscoveryResult.Status.UNKNOWN_COMPONENT,
                new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1).discover(unknown, p(0, 0, 0)).status);
        FakeWorld schema = loadedWorld();
        schema.components.put(p(0, 0, 0), new PlacedComponent(StandardComponentCatalog.CHASSIS, 2,
                ComponentOrientation.NORTH_UP));
        assertEquals(AssemblyDiscoveryResult.Status.UNSUPPORTED_SCHEMA,
                new AssemblyDiscovery(catalog, AssemblyLimits.PROFILE_1).discover(schema, p(0, 0, 0)).status);
    }

    private static AssemblyGraph valid(AssemblyDiscoveryResult result) {
        assertEquals(AssemblyDiscoveryResult.Status.VALID, result.status);
        assertNotNull(result.graph);
        return result.graph;
    }

    private static String localKey(AssemblyGraph graph) {
        StringBuilder value = new StringBuilder();
        for (DiscoveredComponent component : graph.getComponents()) {
            if (value.length() > 0) value.append(',');
            value.append(component.localPosition);
        }
        return value.toString();
    }

    private static FakeWorld loadedWorld() { return new FakeWorld(true); }
    private static GridVector p(int x, int y, int z) { return new GridVector(x, y, z); }
    private static void placeChassis(FakeWorld world, int x, int y, int z) {
        world.put(p(x, y, z), StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP);
    }

    private static final class FakeWorld implements AssemblyWorldView {
        final Map<GridVector, PlacedComponent> components = new HashMap<GridVector, PlacedComponent>();
        final Set<GridVector> loaded = new HashSet<GridVector>();
        final Set<GridVector> reads = new HashSet<GridVector>();
        final boolean allLoaded;
        FakeWorld() { this(false); }
        FakeWorld(boolean allLoaded) { this.allLoaded = allLoaded; }
        void put(GridVector position, String id, ComponentOrientation orientation) {
            components.put(position, new PlacedComponent(id, 1, orientation)); loaded.add(position);
        }
        @Override public boolean isLoaded(GridVector position) { return allLoaded || loaded.contains(position); }
        @Override public PlacedComponent componentAt(GridVector position) {
            if (!isLoaded(position)) throw new AssertionError("read unloaded position " + position);
            reads.add(position);
            return components.get(position);
        }
    }
}
