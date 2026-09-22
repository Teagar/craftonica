package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularManifestNbtCodec;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public final class MobileElectricalNetlistTest {
    @Test public void changingOnlyOneWireFaceChangesTheCapturedNetlist() {
        List<ModularBlockSnapshot> connected = Arrays.asList(wire(0, 0), wire(1, 0));
        MobileElectricalNetlist joined = extract(connected);
        assertEquals(1, joined.getNetworkCount());
        List<ModularBlockSnapshot> cut = Arrays.asList(wire(0, 1 << Direction.EAST.ordinal()), wire(1, 0));
        MobileElectricalNetlist separated = extract(cut);
        assertEquals(2, separated.getNetworkCount());
        assertNotEquals(joined.network(p(0, 0, 0), "wire_east"), separated.network(p(1, 0, 0), "wire_west"));
    }

    @Test public void changingOnlyRoboPortRoleChangesPinBinding() {
        List<ModularBlockSnapshot> d5 = Arrays.asList(board(-1), wire(0, 0), roboPort(1, 5));
        List<ModularBlockSnapshot> d9 = Arrays.asList(board(-1), wire(0, 0), roboPort(1, 9));
        MobileTerminal first = extract(d5).terminal(p(1, 0, 0), "terminal");
        MobileTerminal second = extract(d9).terminal(p(1, 0, 0), "terminal");
        assertEquals("D5", first.role); assertEquals("D9", second.role);
        assertEquals(first.networkId, second.networkId);
    }

    @Test public void physicallySeparateWireGroupsRemainIsolated() {
        MobileElectricalNetlist netlist = extract(Arrays.asList(wire(0, 0), wire(1, 0), wire(5, 0), wire(6, 0)));
        assertEquals(2, netlist.getNetworkCount());
        assertEquals(netlist.network(p(0, 0, 0), "wire_east"), netlist.network(p(1, 0, 0), "wire_west"));
        assertNotEquals(netlist.network(p(1, 0, 0), "wire_east"), netlist.network(p(5, 0, 0), "wire_west"));
    }

    @Test public void incompatibleDirectTerminalDomainsDoNotCreateAContact() {
        List<ModularBlockSnapshot> modules = bridgeModules();
        ModularBlockSnapshot source = new ModularBlockSnapshot(StandardComponentCatalog.POWER_SOURCE, 1,
                p(0, 0, -2), new ComponentOrientation(Direction.SOUTH, Direction.UP),
                StandardComponentCatalog.POWER_SOURCE, 0, null);
        modules.add(source);
        MobileElectricalNetlist netlist = extract(modules);
        assertNotEquals(netlist.network(p(0, 0, 0), "pwm"),
                netlist.network(p(0, 0, -2), "positive"));
    }

    @Test public void physicalBridgeTerminalIsBoundToCoreRoleButKeepsWireNetsIsolated() {
        List<ModularBlockSnapshot> modules = bridgeModules();
        ModularBlockSnapshot northWire = wireAt(p(0, 0, -2), 0);
        ModularBlockSnapshot southWire = wireAt(p(0, 0, 2), 0);
        modules.add(northWire); modules.add(southWire);
        MobileElectricalNetlist netlist = extract(modules);
        assertEquals(netlist.network(p(0, 0, 0), "pwm"), netlist.network(p(0, 0, -2), "wire_south"));
        assertEquals(netlist.network(p(0, 0, 0), "direction"), netlist.network(p(0, 0, 2), "wire_north"));
        assertNotEquals(netlist.network(p(0, 0, 0), "pwm"), netlist.network(p(0, 0, 0), "direction"));
    }

    @Test public void missingPhysicalBridgeTerminalRejectsCapture() {
        List<ModularBlockSnapshot> modules = bridgeModules(); modules.remove(modules.size() - 1);
        try { extract(modules); fail("missing terminal"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("terminal")); }
    }

    @Test public void completePhysicalBridgeBindingEnablesDriveWithoutFixedPins() {
        MobileElectricalNetlist netlist = bridgeNetlist(true, "D11", "D4");
        MobileElectricalEvaluation evaluation = MobileElectricalEvaluator.evaluate(netlist);
        MobileElectricalEvaluation.DriveBinding drive = evaluation.getDrives().get(p(0, 0, 0));
        assertTrue(drive.enabled); assertEquals("D11", drive.pwmRole); assertEquals("D4", drive.directionRole);
        assertEquals(p(3, 0, 0), drive.motorPosition); assertTrue(evaluation.getDiagnostics().isEmpty());
    }

    @Test public void eachMissingBridgePathDisablesEffortWithSpecificDiagnostic() {
        String[] ports = { "vcc", "gnd", "pwm", "direction", "out_a" };
        MobileElectricalDiagnostic.Code[] codes = {
                MobileElectricalDiagnostic.Code.HBRIDGE_VCC_OPEN,
                MobileElectricalDiagnostic.Code.HBRIDGE_GND_OPEN,
                MobileElectricalDiagnostic.Code.HBRIDGE_PWM_OPEN,
                MobileElectricalDiagnostic.Code.HBRIDGE_DIRECTION_OPEN,
                MobileElectricalDiagnostic.Code.HBRIDGE_OUTPUT_OPEN };
        for (int missing = 0; missing < ports.length; missing++) {
            MobileElectricalNetlist full = bridgeNetlist(true, "D5", "D8");
            List<MobileTerminal> changed = new ArrayList<MobileTerminal>(); int extraNet = full.getNetworkCount();
            for (MobileTerminal terminal : full.getTerminals()) {
                if (terminal.componentTypeId.equals(StandardComponentCatalog.H_BRIDGE)
                        && terminal.portId.equals(ports[missing]))
                    changed.add(new MobileTerminal(terminal.modulePosition, terminal.componentTypeId,
                            terminal.portId, terminal.face, terminal.role, extraNet));
                else changed.add(terminal);
            }
            MobileElectricalEvaluation evaluation = MobileElectricalEvaluator.evaluate(canonicalize(changed));
            assertFalse(evaluation.getDrives().get(p(0, 0, 0)).enabled);
            assertTrue(has(evaluation, codes[missing]));
        }
    }

    @Test public void netlistPersistsInsideManifestFingerprint() {
        List<ModularBlockSnapshot> modules = Arrays.asList(board(-1), wire(0, 0), roboPort(1, 6));
        MobileElectricalNetlist netlist = extract(modules);
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(4, 7), modules,
                Collections.<br.com.craftonica.robot.modular.assembly.AssemblyEdge>emptyList(), netlist);
        ModularRobotManifest restored = ModularManifestNbtCodec.read(ModularManifestNbtCodec.write(manifest));
        assertArrayEquals(manifest.getFingerprint(), restored.getFingerprint());
        assertEquals("D6", restored.getElectricalNetlist().terminal(p(1, 0, 0), "terminal").role);
    }

    @Test public void manifestChecksumRejectsTamperedElectricalBinding() {
        List<ModularBlockSnapshot> modules = Arrays.asList(board(-1), wire(0, 0), roboPort(1, 6));
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(8, 9), modules,
                Collections.<br.com.craftonica.robot.modular.assembly.AssemblyEdge>emptyList(), extract(modules));
        NBTTagCompound encoded = ModularManifestNbtCodec.write(manifest);
        encoded.getTagList("ElectricalTerminals", 10).getCompoundTagAt(0).setString("Role", "D12");
        try { ModularManifestNbtCodec.read(encoded); fail("tampered netlist"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void orphanRoboPortCannotInventABoardPin() {
        MobileElectricalNetlist netlist = extract(Arrays.asList(wire(0, 0), roboPort(1, 7)));
        assertEquals("UNBOUND", netlist.terminal(p(1, 0, 0), "terminal").role);
    }

    @Test public void portBoundToAnotherBoardCannotDriveCapturedBoard() {
        ModularBlockSnapshot foreign = roboPort(1, 7);
        NBTTagCompound tile = foreign.getTileData(); tile.setLong("BoardMost", 99);
        foreign = snapshot(StandardComponentCatalog.ROBO_PORT, 1, tile,
                new ComponentOrientation(Direction.WEST, Direction.UP));
        MobileElectricalNetlist netlist = extract(Arrays.asList(board(-1), wire(0, 0), foreign));
        assertEquals("UNBOUND", netlist.terminal(p(1, 0, 0), "terminal").role);
    }

    @Test public void duplicateDigitalRoboPortRoleRejectsAmbiguousBinding() {
        try {
            extract(Arrays.asList(board(-2), roboPort(0, 5), roboPort(1, 5)));
            fail("duplicate role");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate RoboPort"));
        }
    }

    @Test public void sensorRequiresPhysicalPowerGroundTriggerAndEchoNets() {
        GridVector sensor = p(4, 0, 0); List<MobileTerminal> terminals = new ArrayList<MobileTerminal>();
        terminals.add(t(sensor, StandardComponentCatalog.HC_SR04, "vcc", 0, ""));
        terminals.add(t(p(5, 0, 0), StandardComponentCatalog.ROBO_PORT, "terminal", 0, "POWER_5V"));
        terminals.add(t(sensor, StandardComponentCatalog.HC_SR04, "gnd", 1, ""));
        terminals.add(t(p(6, 0, 0), StandardComponentCatalog.ROBO_PORT, "terminal", 1, "GROUND"));
        terminals.add(t(sensor, StandardComponentCatalog.HC_SR04, "trig", 2, ""));
        terminals.add(t(p(7, 0, 0), StandardComponentCatalog.ROBO_PORT, "terminal", 2, "D2"));
        terminals.add(t(sensor, StandardComponentCatalog.HC_SR04, "echo", 3, ""));
        terminals.add(t(p(8, 0, 0), StandardComponentCatalog.ROBO_PORT, "terminal", 3, "D3"));
        MobileElectricalEvaluation valid = MobileElectricalEvaluator.evaluate(new MobileElectricalNetlist(terminals));
        assertTrue(valid.getDiagnostics().isEmpty());
        assertTrue(valid.getSensors().get(sensor).enabled);
        assertEquals("D2", valid.getSensors().get(sensor).triggerRole);
        assertEquals("D3", valid.getSensors().get(sensor).echoRole);
        terminals.set(4, t(sensor, StandardComponentCatalog.HC_SR04, "trig", 4, ""));
        MobileElectricalEvaluation invalid = MobileElectricalEvaluator.evaluate(new MobileElectricalNetlist(terminals));
        assertTrue(has(invalid, MobileElectricalDiagnostic.Code.SENSOR_TRIGGER_OPEN));
        assertFalse(invalid.getSensors().get(sensor).enabled);
    }

    @Test public void servoRequiresPowerGroundAndARealDigitalSignal() {
        GridVector servo = p(4, 0, 0); List<MobileTerminal> terminals = new ArrayList<MobileTerminal>();
        terminals.add(t(servo, StandardComponentCatalog.SERVO, "vcc", 0, ""));
        terminals.add(t(p(5,0,0), StandardComponentCatalog.ROBO_PORT, "terminal", 0, "POWER_5V"));
        terminals.add(t(servo, StandardComponentCatalog.SERVO, "gnd", 1, ""));
        terminals.add(t(p(6,0,0), StandardComponentCatalog.ROBO_PORT, "terminal", 1, "GROUND"));
        terminals.add(t(servo, StandardComponentCatalog.SERVO, "signal", 2, ""));
        terminals.add(t(p(7,0,0), StandardComponentCatalog.ROBO_PORT, "terminal", 2, "D9"));
        MobileElectricalEvaluation valid = MobileElectricalEvaluator.evaluate(new MobileElectricalNetlist(terminals));
        assertTrue(valid.getDiagnostics().isEmpty());
        assertTrue(valid.getServos().get(servo).enabled);
        assertEquals("D9", valid.getServos().get(servo).signalRole);

        terminals.set(4, t(servo, StandardComponentCatalog.SERVO, "signal", 3, ""));
        MobileElectricalEvaluation invalid = MobileElectricalEvaluator.evaluate(new MobileElectricalNetlist(terminals));
        assertFalse(invalid.getServos().get(servo).enabled);
        assertTrue(has(invalid, MobileElectricalDiagnostic.Code.SERVO_SIGNAL_OPEN));
    }

    private static MobileElectricalNetlist bridgeNetlist(boolean complete, String pwm, String direction) {
        List<MobileTerminal> values = new ArrayList<MobileTerminal>(); GridVector bridge = p(0, 0, 0), motor = p(3, 0, 0);
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "vcc", 0, ""));
        values.add(t(p(0, 1, 0), StandardComponentCatalog.POWER_SOURCE, "positive", 0, ""));
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "gnd", 1, ""));
        values.add(t(p(0, -1, 0), StandardComponentCatalog.GROUND, "ground", 1, ""));
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "pwm", 2, ""));
        values.add(t(p(0, 0, -2), StandardComponentCatalog.ROBO_PORT, "terminal", 2, pwm));
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "direction", 3, ""));
        values.add(t(p(0, 0, 2), StandardComponentCatalog.ROBO_PORT, "terminal", 3, direction));
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "out_a", 4, ""));
        values.add(t(motor, StandardComponentCatalog.DC_MOTOR, "motor_positive", 4, ""));
        values.add(t(bridge, StandardComponentCatalog.H_BRIDGE, "out_b", 5, ""));
        values.add(t(motor, StandardComponentCatalog.DC_MOTOR, "motor_negative", 5, ""));
        return new MobileElectricalNetlist(values);
    }

    private static List<ModularBlockSnapshot> bridgeModules() {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        modules.add(snapshot(StandardComponentCatalog.H_BRIDGE, 0, null, ComponentOrientation.NORTH_UP));
        for (Direction outward : Direction.values()) {
            Direction up = outward == Direction.UP || outward == Direction.DOWN ? Direction.NORTH : Direction.UP;
            modules.add(new ModularBlockSnapshot(StandardComponentCatalog.H_BRIDGE_TERMINAL, 1,
                    GridVector.ZERO.add(outward.vector), new ComponentOrientation(outward, up),
                    StandardComponentCatalog.H_BRIDGE_TERMINAL, 0, null));
        }
        return modules;
    }

    private static MobileElectricalNetlist canonicalize(List<MobileTerminal> source) {
        java.util.Map<Integer, Integer> ids = new java.util.LinkedHashMap<Integer, Integer>();
        List<MobileTerminal> result = new ArrayList<MobileTerminal>();
        for (MobileTerminal terminal : source) {
            Integer id = ids.get(Integer.valueOf(terminal.networkId));
            if (id == null) { id = Integer.valueOf(ids.size()); ids.put(Integer.valueOf(terminal.networkId), id); }
            result.add(new MobileTerminal(terminal.modulePosition, terminal.componentTypeId, terminal.portId,
                    terminal.face, terminal.role, id.intValue()));
        }
        return new MobileElectricalNetlist(result);
    }

    private static boolean has(MobileElectricalEvaluation evaluation, MobileElectricalDiagnostic.Code code) {
        for (MobileElectricalDiagnostic diagnostic : evaluation.getDiagnostics()) if (diagnostic.code == code) return true;
        return false;
    }
    private static MobileTerminal t(GridVector p, String type, String port, int net, String role) {
        return new MobileTerminal(p, type, port, Direction.NORTH, role, net);
    }
    private static MobileElectricalNetlist extract(List<ModularBlockSnapshot> modules) {
        return MobileNetlistExtractor.extract(modules, StandardComponentCatalog.create());
    }
    private static ModularBlockSnapshot wire(int x, int blocked) {
        return wireAt(p(x, 0, 0), blocked);
    }
    private static ModularBlockSnapshot wireAt(GridVector position, int blocked) {
        NBTTagCompound tile = new NBTTagCompound(); tile.setInteger("WireSchema", 1); tile.setByte("BlockedFaces", (byte) blocked);
        return new ModularBlockSnapshot(StandardComponentCatalog.WIRE, 1, position,
                ComponentOrientation.NORTH_UP, StandardComponentCatalog.WIRE, 0, tile);
    }
    private static ModularBlockSnapshot roboPort(int x, int role) {
        NBTTagCompound tile = new NBTTagCompound(); tile.setInteger("PortSchema", 1); tile.setByte("Role", (byte) role);
        tile.setLong("BoardMost", 1); tile.setLong("BoardLeast", 2);
        return snapshot(StandardComponentCatalog.ROBO_PORT, x, tile,
                new ComponentOrientation(Direction.WEST, Direction.UP));
    }
    private static ModularBlockSnapshot board(int x) {
        NBTTagCompound tile = new NBTTagCompound(); tile.setLong("BoardMost", 1); tile.setLong("BoardLeast", 2);
        return snapshot(StandardComponentCatalog.ROBO_BOARD, x, tile, ComponentOrientation.NORTH_UP);
    }
    private static ModularBlockSnapshot snapshot(String type, int x, NBTTagCompound tile, ComponentOrientation orientation) {
        return new ModularBlockSnapshot(type, 1, p(x, 0, 0), orientation, type, 0, tile);
    }
    private static GridVector p(int x, int y, int z) { return new GridVector(x, y, z); }
}
