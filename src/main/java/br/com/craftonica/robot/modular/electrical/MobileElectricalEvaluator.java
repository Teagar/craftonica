package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates that actuators and sensors have real power, signal and load paths. */
public final class MobileElectricalEvaluator {
    private MobileElectricalEvaluator() { }

    public static MobileElectricalEvaluation evaluate(MobileElectricalNetlist netlist) {
        if (netlist == null) throw new IllegalArgumentException("netlist");
        Map<GridVector, MobileElectricalEvaluation.DriveBinding> drives =
                new LinkedHashMap<GridVector, MobileElectricalEvaluation.DriveBinding>();
        List<MobileElectricalDiagnostic> diagnostics = new ArrayList<MobileElectricalDiagnostic>();
        for (MobileTerminal terminal : netlist.getTerminals()) {
            if (StandardComponentCatalog.H_BRIDGE.equals(terminal.componentTypeId)
                    && "vcc".equals(terminal.portId)) evaluateBridge(netlist, terminal.modulePosition, drives, diagnostics);
            if (StandardComponentCatalog.HC_SR04.equals(terminal.componentTypeId)
                    && "vcc".equals(terminal.portId)) evaluateSensor(netlist, terminal.modulePosition, diagnostics);
        }
        return new MobileElectricalEvaluation(drives, diagnostics);
    }

    private static void evaluateBridge(MobileElectricalNetlist netlist, GridVector position,
            Map<GridVector, MobileElectricalEvaluation.DriveBinding> drives,
            List<MobileElectricalDiagnostic> diagnostics) {
        boolean power = powered(netlist, position, "vcc");
        boolean ground = grounded(netlist, position, "gnd");
        String pwm = connectedDigitalRole(netlist, position, "pwm");
        String direction = connectedDigitalRole(netlist, position, "direction");
        GridVector motor = connectedMotor(netlist, position);
        if (!power) diagnostic(diagnostics, MobileElectricalDiagnostic.Code.HBRIDGE_VCC_OPEN, position);
        if (!ground) diagnostic(diagnostics, MobileElectricalDiagnostic.Code.HBRIDGE_GND_OPEN, position);
        if (pwm == null) diagnostic(diagnostics, MobileElectricalDiagnostic.Code.HBRIDGE_PWM_OPEN, position);
        if (direction == null) diagnostic(diagnostics, MobileElectricalDiagnostic.Code.HBRIDGE_DIRECTION_OPEN, position);
        if (motor == null) diagnostic(diagnostics, MobileElectricalDiagnostic.Code.HBRIDGE_OUTPUT_OPEN, position);
        drives.put(position, new MobileElectricalEvaluation.DriveBinding(
                power && ground && pwm != null && direction != null && motor != null, pwm, direction, motor));
    }

    private static void evaluateSensor(MobileElectricalNetlist netlist, GridVector position,
                                       List<MobileElectricalDiagnostic> diagnostics) {
        if (!powered(netlist, position, "vcc"))
            diagnostic(diagnostics, MobileElectricalDiagnostic.Code.SENSOR_VCC_OPEN, position);
        if (!grounded(netlist, position, "gnd"))
            diagnostic(diagnostics, MobileElectricalDiagnostic.Code.SENSOR_GND_OPEN, position);
        if (connectedDigitalRole(netlist, position, "trig") == null)
            diagnostic(diagnostics, MobileElectricalDiagnostic.Code.SENSOR_TRIGGER_OPEN, position);
        if (connectedDigitalRole(netlist, position, "echo") == null)
            diagnostic(diagnostics, MobileElectricalDiagnostic.Code.SENSOR_ECHO_OPEN, position);
    }

    private static boolean connectedToType(MobileElectricalNetlist netlist, GridVector position,
                                            String port, String type) {
        Integer network = netlist.network(position, port); if (network == null) return false;
        for (MobileTerminal terminal : netlist.terminalsOn(network.intValue()))
            if (type.equals(terminal.componentTypeId) && !terminal.modulePosition.equals(position)) return true;
        return false;
    }

    private static boolean powered(MobileElectricalNetlist netlist, GridVector position, String port) {
        return connectedToType(netlist, position, port, StandardComponentCatalog.POWER_SOURCE)
                || connectedToRole(netlist, position, port, "POWER_5V");
    }

    private static boolean grounded(MobileElectricalNetlist netlist, GridVector position, String port) {
        return connectedToType(netlist, position, port, StandardComponentCatalog.GROUND)
                || connectedToRole(netlist, position, port, "GROUND");
    }

    private static boolean connectedToRole(MobileElectricalNetlist netlist, GridVector position,
                                            String port, String role) {
        Integer network = netlist.network(position, port); if (network == null) return false;
        for (MobileTerminal terminal : netlist.terminalsOn(network.intValue()))
            if (StandardComponentCatalog.ROBO_PORT.equals(terminal.componentTypeId)
                    && role.equals(terminal.role)) return true;
        return false;
    }

    private static String connectedDigitalRole(MobileElectricalNetlist netlist, GridVector position, String port) {
        Integer network = netlist.network(position, port); if (network == null) return null;
        String found = null;
        for (MobileTerminal terminal : netlist.terminalsOn(network.intValue())) {
            if (!StandardComponentCatalog.ROBO_PORT.equals(terminal.componentTypeId)
                    || !terminal.role.matches("D(?:[0-9]|1[0-3])")) continue;
            if (found != null && !found.equals(terminal.role)) return null;
            found = terminal.role;
        }
        return found;
    }

    private static GridVector connectedMotor(MobileElectricalNetlist netlist, GridVector bridge) {
        Integer a = netlist.network(bridge, "out_a"), b = netlist.network(bridge, "out_b");
        if (a == null || b == null || a.equals(b)) return null;
        for (MobileTerminal terminal : netlist.getTerminals()) {
            if (!StandardComponentCatalog.DC_MOTOR.equals(terminal.componentTypeId)
                    || !"motor_positive".equals(terminal.portId) || terminal.networkId != a.intValue()) continue;
            Integer negative = netlist.network(terminal.modulePosition, "motor_negative");
            if (negative != null && negative.intValue() == b.intValue()) return terminal.modulePosition;
        }
        return null;
    }

    private static void diagnostic(List<MobileElectricalDiagnostic> values,
                                   MobileElectricalDiagnostic.Code code, GridVector position) {
        values.add(new MobileElectricalDiagnostic(code, position));
    }
}
