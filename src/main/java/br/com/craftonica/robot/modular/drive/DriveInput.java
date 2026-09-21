package br.com.craftonica.robot.modular.drive;

public final class DriveInput {
    public enum Mode { COAST, FORWARD, REVERSE, BRAKE }
    public final boolean wiringValid;
    public final double supplyVolts;
    public final int pwm;
    public final Mode mode;
    public final double loadTorqueNm;

    public DriveInput(boolean wiringValid, double supplyVolts, int pwm, Mode mode, double loadTorqueNm) {
        if (mode == null) throw new IllegalArgumentException("mode");
        this.wiringValid = wiringValid; this.supplyVolts = supplyVolts; this.pwm = pwm;
        this.mode = mode; this.loadTorqueNm = loadTorqueNm;
    }

    public static DriveInput fromControlSignals(boolean wiringValid, double supplyVolts, int pwm,
            boolean directionHigh, boolean brake, double loadTorqueNm) {
        Mode mode = brake ? Mode.BRAKE : pwm == 0 ? Mode.COAST
                : directionHigh ? Mode.FORWARD : Mode.REVERSE;
        return new DriveInput(wiringValid, supplyVolts, pwm, mode, loadTorqueNm);
    }
}
