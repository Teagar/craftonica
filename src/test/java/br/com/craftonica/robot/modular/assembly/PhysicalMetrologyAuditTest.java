package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.drive.*;
import br.com.craftonica.robot.modular.servo.*;
import br.com.craftonica.sensor.AcousticMaterialProfile;
import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Equation-level audit against the public references listed in the generated report. */
public final class PhysicalMetrologyAuditTest {
    @Test public void publishedProtocolsRemainInsideDeclaredTolerance() throws Exception {
        List<Row> rows = new ArrayList<Row>();
        auditMotor(rows); auditTransmissionAndWheel(rows); auditServo(rows); auditUltrasonic(rows);
        for (Row row : rows) assertTrue(row.id + " error=" + row.relativeError,
                row.relativeError <= row.tolerance);
        assertEquals(12, rows.size());
        String csv = render(rows); writeReport(csv); verifyBaseline(csv);
    }

    private static void auditMotor(List<Row> rows) {
        DcMotorParameters motor = DcMotorParameters.educational();
        HBridgeParameters bridge = HBridgeParameters.educational();
        DriveStep stall = ElectromechanicalDriveModel.step(motor, bridge, DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0), 0.001);
        double expectedCurrent = Math.min(motor.maximumCurrentAmps,
                (5.0 - bridge.voltageDropVolts) / (motor.resistanceOhms + bridge.onResistanceOhms));
        rows.add(row("motor_stall_current", "A", expectedCurrent, stall.currentAmps, 1.0e-9,
                "dc_motor_equations", "valid at zero speed; ideal current clamp"));
        rows.add(row("motor_stall_torque", "N*m", motor.torqueConstantNmPerAmp * expectedCurrent,
                stall.electromagneticTorqueNm, 1.0e-9, "dc_motor_equations",
                "catalog constants, not a commercial motor guarantee"));
        DriveState state = DriveState.ambient(motor, bridge);
        DriveInput noLoad = new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0);
        for (int i = 0; i < 6000; i++)
            state = ElectromechanicalDriveModel.step(motor, bridge, state, noLoad, 0.001).state;
        double expectedSpeed = motor.torqueConstantNmPerAmp * (5.0 - bridge.voltageDropVolts)
                / (motor.torqueConstantNmPerAmp * motor.backEmfVoltSecondsPerRad
                + motor.viscousFrictionNmSecondsPerRad * (motor.resistanceOhms + bridge.onResistanceOhms));
        rows.add(row("motor_no_load_speed", "rad/s", expectedSpeed,
                state.angularVelocityRadPerSecond, 0.002, "dc_motor_equations",
                "steady state of averaged PWM model; inductance and brush ripple omitted"));
    }

    private static void auditTransmissionAndWheel(List<Row> rows) {
        MechanicalAssembly.DrivePath drive = gearedPath();
        rows.add(row("gear_12_36_speed", "rad/s", -10.0, drive.outputAngularVelocity(30.0),
                1.0e-9, "spur_gear_ratio", "external 3:1 mesh reverses direction"));
        rows.add(row("gear_12_36_torque", "N*m", -2.88, drive.outputTorque(1.0),
                1.0e-9, "spur_gear_ratio", "3:1 ideal ratio times declared 96% efficiency"));
        rows.add(row("wheel_100mm_linear_speed", "m/s", -0.5,
                drive.linearSpeedMetresPerSecond(30.0), 1.0e-9, "rolling_kinematics",
                "no-slip v=omega*r; tyre deformation omitted"));
        double normal = 9.80665, coefficient = 0.9;
        rows.add(row("wheel_static_friction_cap", "N", coefficient * normal,
                coefficient * normal, 1.0e-9, "coulomb_friction",
                "normalized 1 kg load; model shares normal force equally among supports"));
    }

    private static void auditServo(List<Row> rows) {
        ServoParameters p = ServoParameters.educational();
        ServoState initial = ServoState.ambient(p);
        ServoStep low = ServoModel.step(p, initial, input(1000, 0), 0.025);
        ServoStep center = ServoModel.step(p, initial, input(1500, 0), 0.025);
        ServoStep high = ServoModel.step(p, initial, input(2000, 0), 0.025);
        rows.add(row("servo_1000us_target", "deg", 0.0,
                StrictMath.toDegrees(low.state.targetRadians), 1.0e-9, "arduino_servo_pulse",
                "educational mapping; hardware endpoints require calibration"));
        rows.add(row("servo_1500us_target", "deg", 90.0,
                StrictMath.toDegrees(center.state.targetRadians), 1.0e-9, "arduino_servo_pulse",
                "educational midpoint; not a measured horn angle"));
        rows.add(row("servo_2000us_target", "deg", 180.0,
                StrictMath.toDegrees(high.state.targetRadians), 1.0e-9, "arduino_servo_pulse",
                "target only; torque, inertia and stops determine position"));
    }

    private static void auditUltrasonic(List<Row> rows) {
        double physicalMicrosPerCm = 2.0e4 / 343.0;
        rows.add(row("hc_sr04_time_of_flight", "us/cm", physicalMicrosPerCm,
                UltrasonicMeasurementModel.MICROSECONDS_PER_CM, 0.006, "hc_sr04_datasheet",
                "58 us/cm convention versus 343 m/s at nominal room conditions"));
        UltrasonicMeasurementModel.Measurement m = UltrasonicMeasurementModel.measure(
                100.0, 0.0, AcousticMaterialProfile.MDF, 12345L);
        double echoMicros = m.echoCycles / (double) UltrasonicMeasurementModel.AVR_CYCLES_PER_MICROSECOND;
        rows.add(row("hc_sr04_echo_quantization", "us", m.measuredCentimeters * 58.0,
                echoMicros, 0.00002, "hc_sr04_datasheet",
                "deterministic synthetic echo; environment and unit variation omitted"));
    }

    private static MechanicalAssembly.DrivePath gearedPath() {
        return new MechanicalAssembly.DrivePath(new GridVector(0,0,0), new GridVector(0,0,1),
                Direction.EAST, 0.05, 0.05, 0.03, 0.5, 3.0, -1, 0.96,
                0.00015, 1, java.util.Collections.<MechanicalAssembly.EncoderTap>emptyList());
    }

    private static ServoInput input(int pulse, double load) {
        return new ServoInput(true, true, 5.0, Integer.valueOf(pulse),
                ServoInput.SignalLossPolicy.HOLD, load);
    }

    private static Row row(String id, String unit, double reference, double measured, double tolerance,
            String source, String scope) {
        double denominator = Math.max(Math.abs(reference), 1.0e-12);
        return new Row(id, unit, reference, measured, Math.abs(measured - reference) / denominator,
                tolerance, source, scope);
    }

    private static String render(List<Row> rows) {
        StringBuilder csv = new StringBuilder("protocol,unit,reference,model,relative_error,tolerance,source,scope\n");
        for (Row row : rows) csv.append(row.csv());
        return csv.toString();
    }

    private static void writeReport(String csv) throws Exception {
        String path = System.getProperty("craftonica.physicsAudit.report");
        if (path == null || path.length() == 0) return;
        File report = new File(path), parent = report.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("report directory");
        FileWriter writer = new FileWriter(report);
        try { writer.write(csv); } finally { writer.close(); }
    }

    private static void verifyBaseline(String csv) throws Exception {
        String path = System.getProperty("craftonica.physicsAudit.baseline");
        if (path == null || path.length() == 0) return;
        String baseline = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.US_ASCII);
        assertEquals("versioned physics baseline", baseline, csv);
    }

    private static final class Row {
        final String id, unit, source, scope;
        final double reference, measured, relativeError, tolerance;
        Row(String id, String unit, double reference, double measured, double relativeError,
                double tolerance, String source, String scope) {
            this.id=id; this.unit=unit; this.reference=reference; this.measured=measured;
            this.relativeError=relativeError; this.tolerance=tolerance; this.source=source; this.scope=scope;
        }
        String csv() {
            return String.format(Locale.ROOT, "%s,%s,%.9f,%.9f,%.9f,%.9f,%s,\"%s\"%n",
                    id, unit, reference, measured, relativeError, tolerance, source, scope);
        }
    }
}
