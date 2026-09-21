package br.com.craftonica.robot.modular.drive;

public final class DriveStep {
    public enum Diagnostic { NONE, OPEN_CIRCUIT, INVALID_SUPPLY, INVALID_PWM, OVER_TEMPERATURE }
    public final DriveState state;
    public final DriveInput.Mode appliedMode;
    public final double terminalVoltageVolts, currentAmps, electromagneticTorqueNm;
    public final double shaftTorqueNm, sourcePowerWatts, motorCopperLossWatts;
    public final double bridgeLossWatts, shaftMechanicalPowerWatts;
    public final Diagnostic diagnostic;

    DriveStep(DriveState state, DriveInput.Mode appliedMode, double terminalVoltageVolts,
            double currentAmps, double electromagneticTorqueNm, double shaftTorqueNm,
            double sourcePowerWatts, double motorCopperLossWatts, double bridgeLossWatts,
            double shaftMechanicalPowerWatts, Diagnostic diagnostic) {
        this.state = state; this.appliedMode = appliedMode; this.terminalVoltageVolts = terminalVoltageVolts;
        this.currentAmps = currentAmps; this.electromagneticTorqueNm = electromagneticTorqueNm;
        this.shaftTorqueNm = shaftTorqueNm; this.sourcePowerWatts = sourcePowerWatts;
        this.motorCopperLossWatts = motorCopperLossWatts; this.bridgeLossWatts = bridgeLossWatts;
        this.shaftMechanicalPowerWatts = shaftMechanicalPowerWatts; this.diagnostic = diagnostic;
    }
}
