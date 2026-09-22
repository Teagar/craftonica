package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class MechanicalAssemblyAnalyzerTest {
    private final ComponentCatalog catalog = StandardComponentCatalog.create();

    @Test public void completeMotorAxleWheelPathAloneTransmitsTorque() {
        Fixture complete = new Fixture(); complete.chain(0, StandardComponentCatalog.WHEEL, true);
        MechanicalAssembly connected = MechanicalAssemblyAnalyzer.analyze(complete.graph());
        assertEquals(1, connected.drivenWheelCount());
        MechanicalAssembly.DrivePath drive = connected.getDrives().get(0);
        assertEquals(0.25, drive.maximumTorqueNm, 0.0);
        assertEquals(0.05, drive.wheelRadiusMetres, 0.0);
        assertEquals(0.5, drive.linearSpeedMetresPerSecond(10.0), 0.0);

        Fixture broken = new Fixture(); broken.chain(0, StandardComponentCatalog.WHEEL, false);
        MechanicalAssembly disconnected = MechanicalAssemblyAnalyzer.analyze(broken.graph());
        assertEquals(0, disconnected.drivenWheelCount());
        assertEquals(1, disconnected.getContacts().size());
        assertFalse(disconnected.getContacts().get(0).driven);
        assertEquals(ContactProfile.Kind.DRIVEN_WHEEL, disconnected.getContacts().get(0).kind);
        assertEquals(0, disconnected.passiveCasterCount());
        assertEquals(MechanicalAssembly.Diagnostic.OPEN_PATH,
                disconnected.getDiagnostics().get(0).code);
    }

    @Test public void wheelRadiusChangesLinearSpeedWithoutLayoutRule() {
        Fixture fixture = new Fixture();
        fixture.chain(-2, StandardComponentCatalog.WHEEL, true);
        fixture.chain(2, StandardComponentCatalog.WHEEL_150, true);
        MechanicalAssembly mechanical = MechanicalAssemblyAnalyzer.analyze(fixture.graph());
        assertEquals(2, mechanical.drivenWheelCount());
        double small = 0, large = 0;
        for (MechanicalAssembly.DrivePath drive : mechanical.getDrives()) {
            if (drive.wheelRadiusMetres == 0.05) small = drive.linearSpeedMetresPerSecond(20.0);
            if (drive.wheelRadiusMetres == 0.075) large = drive.linearSpeedMetresPerSecond(20.0);
        }
        assertEquals(1.0, small, 0.0); assertEquals(1.5, large, 1.0e-12);
    }

    @Test public void mecanumRollerAngleProjectsPhysicalDriveLever(){
        Fixture fixture=new Fixture();fixture.chain(0,StandardComponentCatalog.MECANUM_LEFT,true);
        MechanicalAssembly.DrivePath drive=MechanicalAssemblyAnalyzer.analyze(fixture.graph()).getDrives().get(0);
        assertEquals(0.05,drive.wheelRadiusMetres,0.0);
        assertEquals(0.05/StrictMath.sqrt(2.0),drive.tractionLeverArmMetres,1.0e-12);
        assertEquals(0.5/StrictMath.sqrt(2.0),drive.linearSpeedMetresPerSecond(10.0),1.0e-12);
    }

    @Test public void twoWdCasterAndFourWdAreCountsDerivedFromSameGraphTraversal() {
        Fixture two = new Fixture(); two.chain(-2, StandardComponentCatalog.WHEEL, true);
        two.chain(2, StandardComponentCatalog.WHEEL, true); two.caster(0, 0, -2);
        MechanicalAssembly twoWd = MechanicalAssemblyAnalyzer.analyze(two.graph());
        assertEquals(2, twoWd.drivenWheelCount()); assertEquals(1, twoWd.passiveCasterCount());

        Fixture four = new Fixture();
        four.chain(-3, StandardComponentCatalog.WHEEL, true); four.chain(-1, StandardComponentCatalog.WHEEL, true);
        four.chain(1, StandardComponentCatalog.WHEEL, true); four.chain(3, StandardComponentCatalog.WHEEL, true);
        MechanicalAssembly fourWd = MechanicalAssemblyAnalyzer.analyze(four.graph());
        assertEquals(4, fourWd.drivenWheelCount()); assertEquals(0, fourWd.passiveCasterCount());
    }

    @Test public void spurGearReductionReversesSpeedAmplifiesTorqueAndReflectsInertia() {
        Fixture fixture = new Fixture();
        GridVector motor = p(0, 0, 0), small = p(0, 0, 1), large = p(1, 0, 1);
        GridVector axle = p(1, 0, 2), wheel = p(1, 0, 3);
        fixture.components.add(fixture.component(motor, StandardComponentCatalog.DC_MOTOR,
                ComponentOrientation.NORTH_UP));
        fixture.components.add(fixture.component(small, StandardComponentCatalog.GEAR_12,
                ComponentOrientation.NORTH_UP));
        fixture.components.add(fixture.component(large, StandardComponentCatalog.GEAR_36,
                new ComponentOrientation(Direction.SOUTH, Direction.UP)));
        fixture.components.add(fixture.component(axle, StandardComponentCatalog.AXLE,
                ComponentOrientation.NORTH_UP));
        fixture.components.add(fixture.component(wheel, StandardComponentCatalog.WHEEL,
                new ComponentOrientation(Direction.EAST, Direction.UP)));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, motor, "shaft", small, "shaft_in"));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, small, "mesh", large, "mesh"));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, large, "shaft_out", axle, "shaft_in"));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, axle, "shaft_out", wheel, "hub"));

        MechanicalAssembly result = MechanicalAssemblyAnalyzer.analyze(fixture.graph());
        assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
        MechanicalAssembly.DrivePath drive = result.getDrives().get(0);
        assertEquals(3.0, drive.speedRatio, 0.0);
        assertEquals(-1, drive.directionSign);
        assertEquals(0.96, drive.efficiency, 0.0);
        assertEquals(1, drive.gearStages);
        assertEquals(-10.0, drive.outputAngularVelocity(30.0), 0.0);
        assertEquals(-2.88, drive.outputTorque(1.0), 1.0e-12);
        assertEquals(-0.5, drive.linearSpeedMetresPerSecond(30.0), 1.0e-12);
        assertEquals(-1.0 / 2.88, drive.motorLoadTorque(1.0), 1.0e-12);
        assertEquals(0.5, drive.maximumTorqueNm, 1.0e-12);
        assertTrue(drive.reflectedInertiaKgM2 > 0.0001);
        assertTrue(drive.reflectedInertiaKgM2 < 0.0002);
    }

    @Test public void incompatibleGearMeshIsDisconnectedWithDiagnostic() {
        Fixture fixture = new Fixture();
        GridVector motor = p(0,0,0), gear = p(0,0,1), incompatible = p(1,0,1);
        fixture.components.add(fixture.component(motor, StandardComponentCatalog.DC_MOTOR,
                ComponentOrientation.NORTH_UP));
        fixture.components.add(fixture.component(gear, StandardComponentCatalog.GEAR_12,
                ComponentOrientation.NORTH_UP));
        ComponentType wrongModule = ComponentType.builder("craftonica:test_wrong_module", 1)
                .physical(BoxVolume.FULL_BLOCK, MassProperties.box(0.2, BoxVolume.FULL_BLOCK), ComponentMaterial.STEEL)
                .mechanical(new MechanicalPort("shaft", MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.SOCKET, PortPose.center(Direction.NORTH), Direction.NORTH, 1.0))
                .mechanical(new MechanicalPort("mesh", MechanicalPort.Kind.GEAR_MESH,
                        MechanicalPort.Coupling.NEUTRAL, PortPose.center(Direction.WEST), Direction.NORTH, 1.0))
                .transmission(TransmissionProfile.spurGear(24, 0.02, 0.95, 0.0001, 100.0)).build();
        fixture.components.add(new DiscoveredComponent(incompatible, incompatible,
                ComponentOrientation.NORTH_UP, wrongModule));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, motor, "shaft", gear, "shaft_in"));
        fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, gear, "mesh", incompatible, "mesh"));

        MechanicalAssembly result = MechanicalAssemblyAnalyzer.analyze(fixture.graph());
        assertEquals(0, result.drivenWheelCount());
        assertTrue(has(result, MechanicalAssembly.Diagnostic.INCOMPATIBLE_GEAR_MESH));
        assertTrue(has(result, MechanicalAssembly.Diagnostic.OPEN_PATH));
    }

    @Test public void transmissionBudgetRefusesWholeDriveInsteadOfIgnoringEdges() {
        Fixture fixture = new Fixture(); fixture.chain(0, StandardComponentCatalog.WHEEL, true);
        GridVector motor = p(0,0,0), axle = p(0,0,1);
        for (int i = 0; i < MechanicalAssemblyAnalyzer.MAX_TRANSMISSION_EDGES; i++)
            fixture.edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,
                    motor, "shaft", axle, "shaft_in"));
        MechanicalAssembly result = MechanicalAssemblyAnalyzer.analyze(fixture.graph());
        assertEquals(0, result.drivenWheelCount());
        assertTrue(has(result, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED));
    }

    private static boolean has(MechanicalAssembly assembly, MechanicalAssembly.Diagnostic code) {
        for (MechanicalAssembly.TransmissionDiagnostic value : assembly.getDiagnostics())
            if (value.code == code) return true;
        return false;
    }

    private final class Fixture {
        final List<DiscoveredComponent> components = new ArrayList<DiscoveredComponent>();
        final List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        void chain(int x, String wheelType, boolean complete) {
            GridVector motor = p(x, 0, 0), axle = p(x, 0, 1), wheel = p(x, 0, 2);
            components.add(component(motor, StandardComponentCatalog.DC_MOTOR, ComponentOrientation.NORTH_UP));
            components.add(component(axle, StandardComponentCatalog.AXLE, ComponentOrientation.NORTH_UP));
            components.add(component(wheel, wheelType, new ComponentOrientation(Direction.EAST, Direction.UP)));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, motor, "shaft", axle, "shaft_in"));
            if (complete) edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, axle, "shaft_out", wheel, "hub"));
        }
        void caster(int x, int y, int z) { components.add(component(p(x,y,z), StandardComponentCatalog.CASTER, ComponentOrientation.NORTH_UP)); }
        AssemblyGraph graph() { return new AssemblyGraph(GridVector.ZERO, ComponentOrientation.NORTH_UP, components, edges); }
        DiscoveredComponent component(GridVector p, String type, ComponentOrientation orientation) {
            return new DiscoveredComponent(p, p, orientation, catalog.require(type));
        }
    }
    private static GridVector p(int x, int y, int z) { return new GridVector(x,y,z); }
}
