package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.GridVector;

public final class MobileElectricalDiagnostic implements Comparable<MobileElectricalDiagnostic> {
    public enum Code {
        HBRIDGE_VCC_OPEN, HBRIDGE_GND_OPEN, HBRIDGE_PWM_OPEN, HBRIDGE_DIRECTION_OPEN,
        HBRIDGE_OUTPUT_OPEN, SENSOR_VCC_OPEN, SENSOR_GND_OPEN, SENSOR_TRIGGER_OPEN, SENSOR_ECHO_OPEN
    }
    public final Code code;
    public final GridVector componentPosition;

    public MobileElectricalDiagnostic(Code code, GridVector componentPosition) {
        if (code == null || componentPosition == null) throw new IllegalArgumentException("diagnostic");
        this.code = code; this.componentPosition = componentPosition;
    }

    @Override public int compareTo(MobileElectricalDiagnostic other) {
        int codeOrder = code.compareTo(other.code); return codeOrder != 0 ? codeOrder
                : componentPosition.toString().compareTo(other.componentPosition.toString());
    }
}
