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
