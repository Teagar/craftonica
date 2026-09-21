package br.com.craftonica.robot.modular.drive;

import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;

/** Applies the physical-netlist gate before the electromechanical equations. */
public final class MobileDriveSimulation {
    private MobileDriveSimulation() { }

    public static DriveStep step(MobileElectricalEvaluation.DriveBinding binding,
            DcMotorParameters motor, HBridgeParameters bridge, DriveState previous,
            DriveInput command, double seconds) {
        if (command == null) throw new IllegalArgumentException("command");
        boolean connected = binding != null && binding.enabled && command.wiringValid;
        DriveInput gated = new DriveInput(connected, command.supplyVolts, command.pwm,
                command.mode, command.loadTorqueNm);
        return ElectromechanicalDriveModel.step(motor, bridge, previous, gated, seconds);
    }
}
