package br.com.craftonica.robot;

import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.UltrasonicPeripheral;
import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import br.com.craftonica.sensor.UltrasonicRaycaster;
import br.com.craftonica.sensor.UltrasonicSensorPose;
import br.com.craftonica.tile.RoboBoardState;

/** Server-thread adapter between a confirmed robot pose and one immutable AVR input snapshot. */
public final class MobileRobotIoBridge {
    public static final int ECHO_PIN = 6, TRIGGER_PIN = 7;
    private MobileRobotIoBridge() { }

    public static AvrInputs sample(EntityMobileRobot robot, long measurementCounter) {
        if (robot == null || robot.worldObj == null || robot.worldObj.isRemote) throw new IllegalArgumentException("robot");
        double yaw = StrictMath.toRadians(robot.rotationYaw);
        UltrasonicSensorPose pose = new UltrasonicSensorPose(
                robot.posX - StrictMath.sin(yaw) * 0.91,
                robot.posY + 0.72,
                robot.posZ + StrictMath.cos(yaw) * 0.91,
                robot.rotationYaw, 0.0);
        UltrasonicRaycaster.Hit hit = UltrasonicRaycaster.trace(robot.worldObj, pose, robot);
        long seed = robot.worldObj.getSeed() ^ robot.getRobotState().getRobotId().getMostSignificantBits()
                ^ robot.getRobotState().getRobotId().getLeastSignificantBits() ^ measurementCounter;
        UltrasonicMeasurementModel.Measurement measurement = hit == null
                ? UltrasonicMeasurementModel.Measurement.noEcho()
                : UltrasonicMeasurementModel.measure(hit.centimeters, hit.incidenceDegrees,
                        hit.material, hit.apparentCoverage, seed);
        UltrasonicPeripheral ultrasonic = new UltrasonicPeripheral(true, TRIGGER_PIN, ECHO_PIN,
                measurement.echo ? measurement.echoCycles : 0L);
        return new AvrInputs(new boolean[AvrInputs.DIGITAL_PIN_COUNT],
                new int[AvrInputs.ANALOG_CHANNEL_COUNT], ultrasonic);
    }

    public static DriveCommand drive(RoboBoardState board) {
        if (board == null || !board.isRunning()) return DriveCommand.off("BOARD_STOPPED");
        HBridgeModel.Output left = channel(board, 2, 4, 5);
        HBridgeModel.Output right = channel(board, 8, 10, 9);
        return new DriveCommand(left, right);
    }

    private static HBridgeModel.Output channel(RoboBoardState board, int in1, int in2, int enable) {
        int output = board.getOutputMask(), high = board.getHighMask(), pwmMask = board.getPwmMask();
        boolean valid = (output & (1 << in1)) != 0 && (output & (1 << in2)) != 0
                && (output & (1 << enable)) != 0;
        int pwm = (pwmMask & (1 << enable)) != 0 ? board.getPwmCompare()[enable]
                : (high & (1 << enable)) != 0 ? 255 : 0;
        return HBridgeModel.evaluate(true, valid, (high & (1 << in1)) != 0,
                (high & (1 << in2)) != 0, pwm);
    }

    public static final class DriveCommand {
        public final HBridgeModel.Output left, right;
        DriveCommand(HBridgeModel.Output left, HBridgeModel.Output right) { this.left = left; this.right = right; }
        static DriveCommand off(String code) {
            HBridgeModel.Output off = HBridgeModel.evaluate(false, true, false, false, 0);
            return new DriveCommand(off, off);
        }
    }
}
