package br.com.craftonica.robot.modular.servo;

public final class ServoStep {
    public enum Diagnostic { NONE, OPEN_WIRING, UNPOWERED, INVALID_SUPPLY, INVALID_PULSE, SIGNAL_LOST, OVER_TEMPERATURE }
    public final ServoState state;
    public final double appliedTorqueNm, currentAmps, targetErrorRadians;
    public final Diagnostic diagnostic;

    ServoStep(ServoState state, double torque, double current, double error, Diagnostic diagnostic) {
        this.state = state; this.appliedTorqueNm = torque; this.currentAmps = current;
        this.targetErrorRadians = error; this.diagnostic = diagnostic;
    }
}
