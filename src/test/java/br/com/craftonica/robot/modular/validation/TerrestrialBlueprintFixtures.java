package br.com.craftonica.robot.modular.validation;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Reproducible test fixtures only. Production code has no blueprint identifiers or generators. */
final class TerrestrialBlueprintFixtures {
    private TerrestrialBlueprintFixtures() { }

    static ModularRobotManifest twoWheelCaster() {
        return build(new int[][] {{-2,0,-1}, {2,0,-1}}, new int[][] {{0,0,2}}, false, false);
    }
    static ModularRobotManifest threeWheel() {
        return build(new int[][] {{-2,0,-1}, {2,0,-1}, {0,0,2}}, new int[0][], false, false);
    }
    static ModularRobotManifest fourWheel() {
        return build(new int[][] {{-2,0,-2}, {2,0,-2}, {-2,0,2}, {2,0,2}}, new int[0][], false, false);
    }
    static ModularRobotManifest skidSteer() {
        return build(new int[][] {{-3,0,-2}, {3,0,-2}, {-3,0,2}, {3,0,2}}, new int[0][], true, false);
    }
    static ModularRobotManifest largerWheels() {
        return build(new int[][] {{-2,0,-1}, {2,0,-1}}, new int[][] {{0,0,2}}, false, true);
    }
    static ModularRobotManifest heavierChassis() {
        return build(new int[][] {{-2,0,-1}, {2,0,-1}}, new int[][] {{0,0,2}}, true, false);
    }
    static ModularRobotManifest openMechanicalPath() {
        ModularRobotManifest complete = twoWheelCaster();
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>(complete.getEdges()); edges.remove(1);
        return new ModularRobotManifest(UUID.randomUUID(), complete.getModules(), edges,
                complete.getElectricalNetlist());
    }
    static ModularRobotManifest openSignal() {
        return build(new int[][] {{-2,0,-1}, {2,0,-1}}, new int[][] {{0,0,2}}, true, false, true);
    }

    private static ModularRobotManifest build(int[][] drives, int[][] casters,
            boolean longChassis, boolean largeWheel) {
        return build(drives, casters, longChassis, largeWheel, false);
    }

    private static ModularRobotManifest build(int[][] drives, int[][] casters,
            boolean longChassis, boolean largeWheel, boolean openFirstSignal) {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        List<MobileTerminal> terminals = new ArrayList<MobileTerminal>();
        modules.add(module(StandardComponentCatalog.CHASSIS, p(0,1,0), ComponentOrientation.NORTH_UP));
        if (longChassis) {
            modules.add(module(StandardComponentCatalog.CHASSIS, p(0,1,-1), ComponentOrientation.NORTH_UP));
            modules.add(module(StandardComponentCatalog.CHASSIS, p(0,1,1), ComponentOrientation.NORTH_UP));
        }
        GridVector source = p(0,3,0), ground = p(0,4,0);
        modules.add(module(StandardComponentCatalog.POWER_SOURCE, source, ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.GROUND, ground, ComponentOrientation.NORTH_UP));
        terminals.add(t(source, StandardComponentCatalog.POWER_SOURCE, "positive", 0, ""));
        terminals.add(t(ground, StandardComponentCatalog.GROUND, "ground", 1, ""));
        for (int index = 0; index < drives.length; index++) {
            GridVector wheel = p(drives[index][0], drives[index][1], drives[index][2]);
            GridVector axle = wheel.add(p(0,1,0)), motor = wheel.add(p(0,2,0));
            GridVector bridge = p(-6 + index * 3, 3, 5);
            GridVector pwmPort = p(-6 + index * 3, 4, 5), directionPort = p(-6 + index * 3, 5, 5);
            String wheelType = largeWheel ? StandardComponentCatalog.WHEEL_150 : StandardComponentCatalog.WHEEL;
            modules.add(module(wheelType, wheel, new ComponentOrientation(Direction.EAST, Direction.UP)));
            modules.add(module(StandardComponentCatalog.AXLE, axle, ComponentOrientation.NORTH_UP));
            modules.add(module(StandardComponentCatalog.DC_MOTOR, motor, ComponentOrientation.NORTH_UP));
            modules.add(module(StandardComponentCatalog.H_BRIDGE, bridge, ComponentOrientation.NORTH_UP));
            modules.add(module(StandardComponentCatalog.ROBO_PORT, pwmPort, ComponentOrientation.NORTH_UP));
            modules.add(module(StandardComponentCatalog.ROBO_PORT, directionPort, ComponentOrientation.NORTH_UP));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, motor, "shaft", axle, "shaft_in"));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL, axle, "shaft_out", wheel, "hub"));
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "vcc", 0, ""));
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "gnd", 1, ""));
            int base = 2 + index * 4;
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "pwm", base, ""));
            if (!(openFirstSignal && index == 0))
                terminals.add(t(pwmPort, StandardComponentCatalog.ROBO_PORT, "terminal", base, "D" + (2 + index * 2)));
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "direction", base + 1, ""));
            terminals.add(t(directionPort, StandardComponentCatalog.ROBO_PORT, "terminal", base + 1,
                    "D" + (3 + index * 2)));
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "out_a", base + 2, ""));
            terminals.add(t(motor, StandardComponentCatalog.DC_MOTOR, "motor_positive", base + 2, ""));
            terminals.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "out_b", base + 3, ""));
            terminals.add(t(motor, StandardComponentCatalog.DC_MOTOR, "motor_negative", base + 3, ""));
        }
        for (int[] value : casters)
            modules.add(module(StandardComponentCatalog.CASTER, p(value[0],value[1],value[2]), ComponentOrientation.NORTH_UP));
        return new ModularRobotManifest(UUID.randomUUID(), modules, edges, new MobileElectricalNetlist(terminals));
    }

    private static MobileTerminal t(GridVector p, String type, String port, int network, String role) {
        return new MobileTerminal(p, type, port, Direction.NORTH, role, network);
    }
    private static ModularBlockSnapshot module(String type, GridVector p, ComponentOrientation orientation) {
        return new ModularBlockSnapshot(type, 1, p, orientation, block(type), 0, null);
    }
    private static String block(String type) {
        if (StandardComponentCatalog.AXLE.equals(type)) return "craftonica:mechanical_axle";
        if (StandardComponentCatalog.WHEEL.equals(type)) return "craftonica:robot_wheel";
        if (StandardComponentCatalog.WHEEL_150.equals(type)) return "craftonica:robot_wheel_150";
        if (StandardComponentCatalog.CASTER.equals(type)) return "craftonica:robot_caster";
        return type;
    }
    private static GridVector p(int x,int y,int z) { return new GridVector(x,y,z); }
}
