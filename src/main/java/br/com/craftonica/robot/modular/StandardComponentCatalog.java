package br.com.craftonica.robot.modular;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned educational profiles for the public modular-robot component blocks. */
public final class StandardComponentCatalog {
    public static final String CHASSIS = "craftonica:robot_chassis";
    public static final String WIRE = "craftonica:electrical_wire";
    public static final String POWER_SOURCE = "craftonica:power_source";
    public static final String GROUND = "craftonica:ground";
    public static final String ROBO_BOARD = "craftonica:robo_board";
    public static final String ROBO_PORT = "craftonica:robo_port";
    public static final String H_BRIDGE = "craftonica:h_bridge_channel";
    public static final String H_BRIDGE_TERMINAL = "craftonica:h_bridge_terminal";
    public static final String DC_MOTOR = "craftonica:modular_dc_motor";
    public static final String AXLE = "craftonica:axle";
    public static final String BEARING = "craftonica:bearing";
    public static final String GEAR_12 = "craftonica:spur_gear_12t";
    public static final String GEAR_36 = "craftonica:spur_gear_36t";
    public static final String WHEEL = "craftonica:wheel";
    public static final String WHEEL_150 = "craftonica:wheel_150mm";
    public static final String CASTER = "craftonica:caster";
    public static final String HC_SR04 = "craftonica:modular_ultrasonic_sensor";

    private static final String RIGID = "craftonica:rigid_mount";
    private static final BoxVolume FULL = BoxVolume.FULL_BLOCK;

    private StandardComponentCatalog() { }

    public static ComponentCatalog create() {
        List<ComponentType> values = new ArrayList<ComponentType>();
        values.add(chassis());
        values.add(wire());
        values.add(powerSource());
        values.add(ground());
        values.add(roboBoard());
        values.add(roboPort());
        values.add(hBridge());
        values.add(hBridgeTerminal());
        values.add(motor());
        values.add(axle());
        values.add(bearing());
        values.add(gear(GEAR_12, 12, 0.18, 0.00004));
        values.add(gear(GEAR_36, 36, 0.42, 0.00035));
        values.add(wheel());
        values.add(wheel150());
        values.add(caster());
        values.add(ultrasonic());
        return new ComponentCatalog(values);
    }

    private static ComponentType chassis() {
        ComponentType.Builder value = base(CHASSIS, 8.0, ComponentMaterial.STEEL);
        for (Direction face : Direction.values()) value.structural(structural("mount_" + face.name().toLowerCase(), face));
        return value.build();
    }

    private static ComponentType wire() {
        ComponentType.Builder value = base(WIRE, 0.08, ComponentMaterial.COPPER,
                new BoxVolume(0.1, 0.0, 0.1, 0.9, 0.15, 0.9));
        for (Direction face : Direction.values()) value.electrical(electrical("wire_" + face.name().toLowerCase(),
                face, ElectricalPort.Domain.POWER, ElectricalPort.Flow.PASSIVE, 30.0, 5.0));
        value.structural(structural("mount_down", Direction.DOWN));
        return value.build();
    }

    private static ComponentType powerSource() {
        return base(POWER_SOURCE, 1.4, ComponentMaterial.ENGINEERING_PLASTIC)
                .structural(structural("mount_down", Direction.DOWN))
                .electrical(electrical("positive", Direction.NORTH, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.OUTPUT, 5.5, 2.0)).build();
    }

    private static ComponentType ground() {
        return base(GROUND, 0.2, ComponentMaterial.COPPER,
                new BoxVolume(0.1, 0.0, 0.1, 0.9, 0.2, 0.9))
                .structural(structural("mount_down", Direction.DOWN))
                .electrical(electrical("ground", Direction.NORTH, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.PASSIVE, 30.0, 5.0)).build();
    }

    private static ComponentType roboBoard() {
        ComponentType.Builder value = base(ROBO_BOARD, 0.35, ComponentMaterial.ENGINEERING_PLASTIC,
                new BoxVolume(0.05, 0.0, 0.05, 0.95, 0.18, 0.95));
        for (Direction face : Direction.values()) value.structural(structural(
                "mount_" + face.name().toLowerCase(), face));
        return value.build();
    }

    private static ComponentType roboPort() {
        return base(ROBO_PORT, 0.08, ComponentMaterial.ENGINEERING_PLASTIC,
                new BoxVolume(0.2, 0.2, 0.2, 0.8, 0.8, 0.8))
                .structural(structural("mount_board", Direction.SOUTH))
                .electrical(electrical("terminal", Direction.NORTH, ElectricalPort.Domain.DIGITAL,
                        ElectricalPort.Flow.BIDIRECTIONAL, 5.5, 0.04)).build();
    }

    private static ComponentType hBridge() {
        Map<String, Double> parameters = parameters("maximum_current_amps", 1.0,
                "maximum_supply_volts", 12.0, "voltage_drop_volts", 0.8,
                "on_resistance_ohms", 0.2, "brake_resistance_ohms", 0.35,
                "pwm_frequency_hz", 490.0, "maximum_temperature_celsius", 110.0,
                "thermal_capacity_joules_per_kelvin", 12.0,
                "thermal_resistance_kelvin_per_watt", 10.0);
        ComponentType.Builder value = base(H_BRIDGE, 0.45, ComponentMaterial.ENGINEERING_PLASTIC,
                new BoxVolume(0.1, 0.0, 0.1, 0.9, 0.3, 0.9))
                .actuator(new ActuatorProfile("craftonica:h_bridge_educational", 1,
                        ActuatorProfile.Kind.H_BRIDGE, parameters));
        for (Direction face : Direction.values()) value.structural(structural(
                "terminal_" + face.name().toLowerCase(), face));
        value.electrical(electricalExtended("vcc", Direction.UP, ElectricalPort.Domain.POWER,
                ElectricalPort.Flow.INPUT, 12.0, 2.0));
        value.electrical(electricalExtended("gnd", Direction.DOWN, ElectricalPort.Domain.POWER,
                ElectricalPort.Flow.PASSIVE, 12.0, 2.0));
        value.electrical(electricalExtended("pwm", Direction.NORTH, ElectricalPort.Domain.DIGITAL,
                ElectricalPort.Flow.INPUT, 5.5, 0.001));
        value.electrical(electricalExtended("direction", Direction.SOUTH, ElectricalPort.Domain.DIGITAL,
                ElectricalPort.Flow.INPUT, 5.5, 0.001));
        value.electrical(electricalExtended("out_a", Direction.WEST, ElectricalPort.Domain.POWER,
                ElectricalPort.Flow.OUTPUT, 12.0, 1.0));
        value.electrical(electricalExtended("out_b", Direction.EAST, ElectricalPort.Domain.POWER,
                ElectricalPort.Flow.OUTPUT, 12.0, 1.0));
        return value.build();
    }

    private static ComponentType hBridgeTerminal() {
        return base(H_BRIDGE_TERMINAL, 0.06, ComponentMaterial.COPPER,
                new BoxVolume(0.2, 0.2, 0.2, 0.8, 0.8, 0.8))
                .structural(structural("mount_bridge", Direction.SOUTH))
                .structural(structural("mount_support", Direction.DOWN))
                .structural(structural("mount_outward", Direction.NORTH)).build();
    }

    private static ComponentType motor() {
        Map<String, Double> parameters = parameters("armature_resistance_ohms", 4.0,
                "torque_constant_nm_per_amp", 0.04, "back_emf_volt_seconds_per_rad", 0.04,
                "rotor_inertia_kg_m2", 0.0002, "viscous_friction_nm_seconds_per_rad", 0.0001,
                "maximum_current_amps", 1.0, "maximum_angular_velocity_rad_per_second", 300.0,
                "maximum_temperature_celsius", 120.0,
                "thermal_capacity_joules_per_kelvin", 20.0,
                "thermal_resistance_kelvin_per_watt", 8.0);
        return base(DC_MOTOR, 0.6, ComponentMaterial.STEEL,
                new BoxVolume(0.15, 0.15, 0.05, 0.85, 0.85, 0.95))
                .structural(structural("mount_down", Direction.DOWN))
                .electrical(electrical("motor_positive", Direction.NORTH, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.PASSIVE, 12.0, 1.0))
                .electrical(electrical("motor_negative", Direction.UP, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.PASSIVE, 12.0, 1.0))
                .mechanical(mechanical("shaft", Direction.SOUTH, MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.PLUG, 0.25))
                .actuator(new ActuatorProfile("craftonica:dc_motor_educational", 1,
                        ActuatorProfile.Kind.DC_MOTOR, parameters)).build();
    }

    private static ComponentType axle() {
        return base(AXLE, 0.25, ComponentMaterial.STEEL,
                new BoxVolume(0.42, 0.42, 0.0, 0.58, 0.58, 1.0))
                .structural(structural("mount_down", Direction.DOWN))
                .mechanical(mechanical("shaft_in", Direction.NORTH, MechanicalPort.Kind.ROTARY_SHAFT,
                         MechanicalPort.Coupling.SOCKET, 0.5))
                .mechanical(mechanical("shaft_out", Direction.SOUTH, MechanicalPort.Kind.ROTARY_SHAFT,
                         MechanicalPort.Coupling.PLUG, 0.5))
                .transmission(TransmissionProfile.shaft(1.0, 0.00003, 400.0)).build();
    }

    private static ComponentType bearing() {
        return base(BEARING, 0.3, ComponentMaterial.STEEL,
                new BoxVolume(0.25, 0.25, 0.0, 0.75, 0.75, 1.0))
                .structural(structural("mount_down", Direction.DOWN))
                .mechanical(mechanical("shaft_in", Direction.NORTH, MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.SOCKET, 0.75))
                .mechanical(mechanical("shaft_out", Direction.SOUTH, MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.PLUG, 0.75))
                .transmission(TransmissionProfile.bearing(0.99, 0.00002, 500.0)).build();
    }

    private static ComponentType gear(String id, int teeth, double mass, double inertia) {
        return base(id, mass, ComponentMaterial.STEEL,
                new BoxVolume(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
                .structural(structural("mount_down", Direction.DOWN))
                .mechanical(mechanical("shaft_in", Direction.NORTH, MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.SOCKET, 1.0))
                .mechanical(mechanical("shaft_out", Direction.SOUTH, MechanicalPort.Kind.ROTARY_SHAFT,
                        MechanicalPort.Coupling.PLUG, 1.0))
                .mechanical(mechanical("mesh", Direction.EAST, Direction.NORTH,
                        MechanicalPort.Kind.GEAR_MESH, MechanicalPort.Coupling.NEUTRAL, 1.0))
                .transmission(TransmissionProfile.spurGear(teeth, 0.01, 0.96, inertia, 350.0)).build();
    }

    private static ComponentType wheel() {
        return wheel(WHEEL, 0.5, 0.05, 0.03);
    }

    private static ComponentType wheel150() {
        return wheel(WHEEL_150, 0.8, 0.075, 0.04);
    }

    private static ComponentType wheel(String id, double mass, double radius, double width) {
        return base(id, mass, ComponentMaterial.RUBBER,
                new BoxVolume(0.35, 0.05, 0.05, 0.65, 0.95, 0.95))
                .mechanical(mechanical("hub", Direction.WEST, MechanicalPort.Kind.WHEEL_HUB,
                        MechanicalPort.Coupling.SOCKET, 0.5))
                .contact(new ContactProfile(id, 1,
                        ContactProfile.Kind.DRIVEN_WHEEL,
                        parameters("radius_metres", radius, "width_metres", width,
                                "longitudinal_friction", 0.9, "lateral_friction", 0.7))).build();
    }

    private static ComponentType caster() {
        return base(CASTER, 0.3, ComponentMaterial.STEEL,
                new BoxVolume(0.25, 0.0, 0.25, 0.75, 0.75, 0.75))
                .structural(structural("mount_up", Direction.UP))
                .mechanical(mechanical("caster_mount", Direction.UP, MechanicalPort.Kind.CASTER_MOUNT,
                        MechanicalPort.Coupling.SOCKET, 0.2))
                .contact(new ContactProfile("craftonica:caster_50mm", 1,
                        ContactProfile.Kind.PASSIVE_CASTER,
                        parameters("radius_metres", 0.025, "rolling_friction", 0.03,
                                "swivel_friction", 0.01))).build();
    }

    private static ComponentType ultrasonic() {
        return base(HC_SR04, 0.12, ComponentMaterial.ENGINEERING_PLASTIC,
                new BoxVolume(0.1, 0.1, 0.25, 0.9, 0.75, 0.75))
                .structural(structural("mount_down", Direction.DOWN))
                .electrical(electrical("vcc", Direction.UP, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.INPUT, 5.5, 0.05))
                .electrical(electrical("gnd", Direction.SOUTH, ElectricalPort.Domain.POWER,
                        ElectricalPort.Flow.PASSIVE, 5.5, 0.05))
                .electrical(electrical("trig", Direction.WEST, ElectricalPort.Domain.DIGITAL,
                        ElectricalPort.Flow.INPUT, 5.5, 0.001))
                .electrical(electrical("echo", Direction.EAST, ElectricalPort.Domain.DIGITAL,
                        ElectricalPort.Flow.OUTPUT, 5.5, 0.02))
                .sensor(new SensorProfile("craftonica:hc_sr04_educational", 1,
                        SensorProfile.Kind.ULTRASONIC,
                        parameters("minimum_range_metres", 0.02, "maximum_range_metres", 4.0,
                                "half_angle_radians", StrictMath.toRadians(15.0),
                                "sound_speed_metres_per_second", 343.0))).build();
    }

    private static ComponentType.Builder base(String id, double massKg, ComponentMaterial material) {
        return base(id, massKg, material, FULL);
    }

    private static ComponentType.Builder base(String id, double massKg, ComponentMaterial material,
                                              BoxVolume volume) {
        return ComponentType.builder(id, 1).physical(volume, MassProperties.box(massKg, volume), material);
    }

    private static StructuralPort structural(String id, Direction face) {
        return new StructuralPort(id, RIGID, PortPose.center(face));
    }

    private static ElectricalPort electrical(String id, Direction face, ElectricalPort.Domain domain,
                                             ElectricalPort.Flow flow, double volts, double amps) {
        return new ElectricalPort(id, domain, flow, PortPose.center(face), volts, amps);
    }

    private static ElectricalPort electricalExtended(String id, Direction face,
            ElectricalPort.Domain domain, ElectricalPort.Flow flow, double volts, double amps) {
        return new ElectricalPort(id, domain, flow,
                new PortPose(face.vector, face, PortPose.center(face).offsetMetres), volts, amps);
    }

    private static MechanicalPort mechanical(String id, Direction face, MechanicalPort.Kind kind,
                                              MechanicalPort.Coupling coupling, double torque) {
        return new MechanicalPort(id, kind, coupling, PortPose.center(face), face, torque);
    }

    private static MechanicalPort mechanical(String id, Direction face, Direction axis,
            MechanicalPort.Kind kind, MechanicalPort.Coupling coupling, double torque) {
        return new MechanicalPort(id, kind, coupling, PortPose.center(face), axis, torque);
    }

    private static Map<String, Double> parameters(Object... pairs) {
        Map<String, Double> values = new LinkedHashMap<String, Double>();
        for (int i = 0; i < pairs.length; i += 2)
            values.put((String) pairs[i], Double.valueOf(((Number) pairs[i + 1]).doubleValue()));
        return values;
    }
}
