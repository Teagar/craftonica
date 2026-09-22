package br.com.craftonica.robot.modular;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

public final class StandardComponentCatalogTest {
    @Test public void publicRobotComponentsExposeFiniteVersionedContracts() {
        ComponentCatalog catalog = StandardComponentCatalog.create();
        String[] required = { StandardComponentCatalog.CHASSIS, StandardComponentCatalog.WIRE,
                StandardComponentCatalog.POWER_SOURCE, StandardComponentCatalog.ROBO_BOARD,
                StandardComponentCatalog.ROBO_PORT,
                StandardComponentCatalog.H_BRIDGE, StandardComponentCatalog.DC_MOTOR,
                 StandardComponentCatalog.H_BRIDGE_TERMINAL,
                 StandardComponentCatalog.AXLE, StandardComponentCatalog.BEARING,
                 StandardComponentCatalog.GEAR_12, StandardComponentCatalog.GEAR_36,
                 StandardComponentCatalog.SERVO,
                 StandardComponentCatalog.WHEEL,
                StandardComponentCatalog.WHEEL_150,
                StandardComponentCatalog.CASTER, StandardComponentCatalog.HC_SR04 };
        for (String id : required) {
            ComponentType type = catalog.require(id);
            assertEquals(1, type.getSchemaVersion());
            assertTrue(type.getMass().massKg > 0.0);
            assertFinite(type.getMass().massKg);
            assertFinite(type.getMass().principalInertiaKgMetresSquared.x);
            assertNotNull(type.getVolume());
            assertNotNull(type.getMaterial());
            for (ElectricalPort port : type.getElectricalPorts()) {
                assertFinite(port.maximumVoltage);
                assertFinite(port.maximumCurrentAmps);
            }
            for (MechanicalPort port : type.getMechanicalPorts())
                assertFinite(port.maximumTorqueNewtonMetres);
            if (type.getTransmission() != null) {
                assertFinite(type.getTransmission().efficiency);
                assertFinite(type.getTransmission().rotationalInertiaKgM2);
                assertFinite(type.getTransmission().maximumAngularVelocityRadPerSecond);
            }
            assertFiniteProfile(type.getActuator());
            assertFiniteProfile(type.getContact());
            assertFiniteProfile(type.getSensor());
        }
        assertEquals(18, catalog.all().size());
    }

    @Test public void profilesAndPortsDescribeRealPathsInsteadOfRobotSlots() {
        ComponentCatalog catalog = StandardComponentCatalog.create();
        assertEquals(0, catalog.require(StandardComponentCatalog.ROBO_BOARD).getElectricalPorts().size());
        assertEquals(1, catalog.require(StandardComponentCatalog.ROBO_PORT).getElectricalPorts().size());
        assertEquals(6, catalog.require(StandardComponentCatalog.H_BRIDGE).getElectricalPorts().size());
        assertEquals(0, catalog.require(StandardComponentCatalog.H_BRIDGE_TERMINAL).getElectricalPorts().size());
        assertEquals(4, catalog.require(StandardComponentCatalog.HC_SR04).getElectricalPorts().size());
        assertEquals(ActuatorProfile.Kind.H_BRIDGE,
                catalog.require(StandardComponentCatalog.H_BRIDGE).getActuator().kind);
        assertEquals(ActuatorProfile.Kind.DC_MOTOR,
                catalog.require(StandardComponentCatalog.DC_MOTOR).getActuator().kind);
        assertEquals(2, catalog.require(StandardComponentCatalog.AXLE).getMechanicalPorts().size());
        assertEquals(TransmissionProfile.Kind.BEARING,
                catalog.require(StandardComponentCatalog.BEARING).getTransmission().kind);
        assertEquals(12, catalog.require(StandardComponentCatalog.GEAR_12).getTransmission().toothCount);
        assertTrue(catalog.require(StandardComponentCatalog.GEAR_12).getTransmission().meshesWith(
                catalog.require(StandardComponentCatalog.GEAR_36).getTransmission()));
        assertEquals(ActuatorProfile.Kind.SERVO,
                catalog.require(StandardComponentCatalog.SERVO).getActuator().kind);
        assertEquals(3, catalog.require(StandardComponentCatalog.SERVO).getElectricalPorts().size());
        assertEquals(ContactProfile.Kind.DRIVEN_WHEEL,
                catalog.require(StandardComponentCatalog.WHEEL).getContact().kind);
        assertEquals(ContactProfile.Kind.PASSIVE_CASTER,
                catalog.require(StandardComponentCatalog.CASTER).getContact().kind);
        assertEquals(SensorProfile.Kind.ULTRASONIC,
                catalog.require(StandardComponentCatalog.HC_SR04).getSensor().kind);
    }

    @Test public void orientationSupportsVerticalAndHorizontalBlockPoses() {
        ComponentOrientation north = ComponentOrientation.NORTH_UP;
        assertEquals(Direction.NORTH, north.toWorld(Direction.NORTH));
        assertEquals(Direction.EAST, north.toWorld(Direction.EAST));
        ComponentOrientation vertical = new ComponentOrientation(Direction.UP, Direction.SOUTH);
        assertEquals(Direction.UP, vertical.toWorld(Direction.NORTH));
        assertEquals(Direction.SOUTH, vertical.toWorld(Direction.UP));
    }

    @Test public void catalogAndDescriptorsAreImmutable() {
        ComponentCatalog catalog = StandardComponentCatalog.create();
        Collection<ComponentType> all = catalog.all();
        try { all.clear(); fail("catalog must be immutable"); } catch (UnsupportedOperationException expected) { }
        try {
            catalog.require(StandardComponentCatalog.HC_SR04).getElectricalPorts().clear();
            fail("ports must be immutable");
        } catch (UnsupportedOperationException expected) { }
        try {
            catalog.require(StandardComponentCatalog.DC_MOTOR).getActuator().getParameters().clear();
            fail("parameters must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void invalidMissingNonFiniteNegativeAndDuplicateDataAreRejected() {
        expectInvalid(new Action() { public void run() {
            ComponentType.builder("craftonica:missing", 1).build();
        }});
        expectInvalid(new Action() { public void run() {
            new Vector3(Double.NaN, 0.0, 0.0);
        }});
        expectInvalid(new Action() { public void run() {
            MassProperties.box(-1.0, BoxVolume.FULL_BLOCK);
        }});
        expectInvalid(new Action() { public void run() {
            ComponentType.builder("craftonica:duplicate", 1)
                    .physical(BoxVolume.FULL_BLOCK, MassProperties.box(1.0, BoxVolume.FULL_BLOCK),
                            ComponentMaterial.STEEL)
                    .structural(new StructuralPort("same", "craftonica:rigid_mount", PortPose.center(Direction.UP)))
                    .electrical(new ElectricalPort("same", ElectricalPort.Domain.POWER,
                            ElectricalPort.Flow.PASSIVE, PortPose.center(Direction.UP), 5.0, 1.0)).build();
        }});
        expectInvalid(new Action() { public void run() {
            new ComponentCatalog(Arrays.asList(StandardComponentCatalog.create().require(
                    StandardComponentCatalog.CHASSIS), StandardComponentCatalog.create().require(
                    StandardComponentCatalog.CHASSIS)));
        }});
        expectInvalid(new Action() { public void run() {
            new SensorProfile("craftonica:bad", 1, SensorProfile.Kind.ULTRASONIC,
                    Collections.singletonMap("range", Double.valueOf(Double.POSITIVE_INFINITY)));
        }});
        expectInvalid(new Action() { public void run() {
            new PortPose(GridVector.ZERO, Direction.UP, new Vector3(0.5, 0.5, 0.5));
        }});
        expectInvalid(new Action() { public void run() {
            TransmissionProfile.spurGear(0, 0.01, 0.9, 0.01, 10.0);
        }});
        expectInvalid(new Action() { public void run() {
            TransmissionProfile.bearing(1.1, 0.01, 10.0);
        }});
    }

    private static void assertFinite(double value) {
        assertFalse(Double.isNaN(value));
        assertFalse(Double.isInfinite(value));
    }

    private static void assertFiniteProfile(ParameterizedProfile profile) {
        if (profile == null) return;
        assertTrue(profile.getSchemaVersion() > 0);
        for (Double value : profile.getParameters().values()) assertFinite(value.doubleValue());
    }

    private static void expectInvalid(Action action) {
        try { action.run(); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    private interface Action { void run(); }
}
