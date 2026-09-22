package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public final class KinematicAssemblyAnalyzerTest {
    private final ComponentCatalog catalog = StandardComponentCatalog.create();

    @Test public void legacyAssembliesWithoutJointRemainOneRigidBody() {
        KinematicAssembly result = KinematicAssemblyAnalyzer.analyze(graph(Arrays.asList(
                component(p(0, 0, 0), StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(p(4, 0, 0), StandardComponentCatalog.AXLE, ComponentOrientation.NORTH_UP)),
                new ArrayList<AssemblyEdge>()));
        assertTrue(result.isValid());
        assertEquals(1, result.getBodies().size());
        assertEquals(2, result.getBodies().get(0).modulePositions.size());
    }

    @Test public void derivesRevoluteAxisFromPlacedOrientationAndPartitionsRigidBodies() {
        GridVector parent = p(0, 0, 0), hinge = p(0, 0, 1), child = p(0, 0, 2);
        ComponentOrientation rotated = new ComponentOrientation(Direction.EAST, Direction.UP);
        AssemblyGraph graph = graph(Arrays.asList(
                component(parent, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(hinge, StandardComponentCatalog.REVOLUTE_JOINT, rotated),
                component(child, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP)),
                Arrays.asList(joint(parent, "mount_south", hinge, "parent"),
                        joint(hinge, "child", child, "mount_north")));

        KinematicAssembly result = KinematicAssemblyAnalyzer.analyze(graph);

        assertTrue(result.getDiagnostics().toString(), result.isValid());
        assertEquals(2, result.getBodies().size());
        assertEquals(1, result.getJoints().size());
        KinematicAssembly.Joint value = result.getJoints().get(0);
        assertEquals(JointProfile.Kind.REVOLUTE, value.kind);
        assertEquals(Direction.SOUTH, value.axis);
        assertEquals(0, value.parentBodyId);
        assertEquals(1, value.childBodyId);
        assertFalse(value.hasDriveConnection());
    }

    @Test public void derivesPrismaticAxisAndRecordsOnePhysicalDriveConnection() {
        GridVector parent = p(0, 0, 0), slider = p(0, 0, 1), child = p(0, 0, 2), drive = p(-1, 0, 1);
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        edges.add(joint(parent, "mount_south", slider, "parent"));
        edges.add(joint(slider, "child", child, "mount_north"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, slider, "drive", drive, "linear_output"));
        KinematicAssembly result = KinematicAssemblyAnalyzer.analyze(graph(Arrays.asList(
                component(parent, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(slider, StandardComponentCatalog.PRISMATIC_JOINT, ComponentOrientation.NORTH_UP),
                component(child, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(drive, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP)), edges));

        assertFalse(result.isValid()); // the unmounted drive is a separate, explicit orphan body
        assertEquals(Direction.NORTH, result.getJoints().get(0).axis);
        assertTrue(result.getJoints().get(0).hasDriveConnection());
        assertEquals(drive, result.getJoints().get(0).driveConnectionPosition);
        assertEquals(KinematicAssembly.Diagnostic.ORPHAN_BODY, result.getDiagnostics().get(0).code);
    }

    @Test public void rejectsMissingSideRigidBypassAndMultipleParentsExplicitly() {
        GridVector parent = p(0, 0, 0), hinge = p(0, 0, 1), child = p(0, 0, 2);
        KinematicAssembly missing = KinematicAssemblyAnalyzer.analyze(graph(Arrays.asList(
                component(parent, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(hinge, StandardComponentCatalog.REVOLUTE_JOINT, ComponentOrientation.NORTH_UP)),
                Arrays.asList(joint(parent, "mount_south", hinge, "parent"))));
        assertEquals(KinematicAssembly.Diagnostic.JOINT_CONNECTION_MISSING,
                missing.getDiagnostics().get(0).code);

        KinematicAssembly bypass = KinematicAssemblyAnalyzer.analyze(graph(Arrays.asList(
                component(parent, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP),
                component(hinge, StandardComponentCatalog.REVOLUTE_JOINT, ComponentOrientation.NORTH_UP),
                component(child, StandardComponentCatalog.CHASSIS, ComponentOrientation.NORTH_UP)),
                Arrays.asList(new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL, parent, "a", child, "b"),
                        joint(parent, "mount_south", hinge, "parent"),
                        joint(hinge, "child", child, "mount_north"))));
        assertEquals(KinematicAssembly.Diagnostic.JOINT_RIGIDLY_BYPASSED,
                bypass.getDiagnostics().get(0).code);
    }

    private DiscoveredComponent component(GridVector position, String type, ComponentOrientation orientation) {
        return new DiscoveredComponent(position, position, orientation, catalog.require(type));
    }
    private static AssemblyEdge joint(GridVector a, String ap, GridVector b, String bp) {
        return new AssemblyEdge(AssemblyEdge.Kind.JOINT, a, ap, b, bp);
    }
    private static AssemblyGraph graph(List<DiscoveredComponent> components, List<AssemblyEdge> edges) {
        return new AssemblyGraph(GridVector.ZERO, ComponentOrientation.NORTH_UP, components, edges);
    }
    private static GridVector p(int x, int y, int z) { return new GridVector(x, y, z); }
}
