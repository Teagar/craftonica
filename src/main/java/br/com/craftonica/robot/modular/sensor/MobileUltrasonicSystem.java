package br.com.craftonica.robot.modular.sensor;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.UltrasonicPeripheral;
import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import br.com.craftonica.sensor.UltrasonicRaycaster;
import br.com.craftonica.sensor.UltrasonicSensorPose;
import br.com.craftonica.tile.RoboBoardState;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Installed-pose HC-SR04 scheduler. Simultaneous triggers are serialized canonically. */
public strictfp final class MobileUltrasonicSystem {
    public static final int MAX_SENSORS = 32;
    public static final int MAX_ACTIVE_SENSORS = 8;
    private static final double EMITTER_FORWARD_OFFSET_METRES = 0.251;
    private final List<Sensor> sensors;

    public MobileUltrasonicSystem(ModularRobotManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("manifest");
        MobileElectricalEvaluation electrical = MobileElectricalEvaluator.evaluate(manifest.getElectricalNetlist());
        List<Sensor> values = new ArrayList<Sensor>();
        for (ModularBlockSnapshot module : manifest.getModules()) {
            if (!StandardComponentCatalog.HC_SR04.equals(module.componentTypeId)) continue;
            MobileElectricalEvaluation.SensorBinding binding = electrical.getSensors().get(module.localPosition);
            values.add(new Sensor(module.localPosition, module.localOrientation, binding));
        }
        if (values.size() > MAX_SENSORS) throw new IllegalArgumentException("maximum sensors");
        Collections.sort(values, SENSOR_ORDER); sensors = Collections.unmodifiableList(values);
    }

    public List<Sensor> getSensors() { return sensors; }

    public Sample sample(World world, Entity owner, RoboBoardState board, double robotX, double robotY,
            double robotZ, double robotYawRadians, long seed) {
        if (world == null || world.isRemote || board == null) throw new IllegalArgumentException("sensor sample");
        List<Sensor> enabled = enabled();
        if (enabled.isEmpty()) return new Sample(AvrInputs.allLow(), Status.IDLE, null);
        if (enabled.size() > MAX_ACTIVE_SENSORS)
            return new Sample(AvrInputs.allLow(), Status.ACTIVE_LIMIT_EXCEEDED, null);
        List<Sensor> triggered = triggered(board);
        Sensor selected = triggered.isEmpty() ? enabled.get(0) : triggered.get(0);
        UltrasonicSensorPose pose = selected.pose(robotX, robotY, robotZ, robotYawRadians);
        UltrasonicRaycaster.Hit hit = UltrasonicRaycaster.trace(world, pose, owner);
        UltrasonicMeasurementModel.Measurement measurement = hit == null
                ? UltrasonicMeasurementModel.Measurement.noEcho()
                : UltrasonicMeasurementModel.measure(hit.centimeters, hit.incidenceDegrees,
                        hit.material, hit.apparentCoverage, seed ^ positionSeed(selected.position));
        UltrasonicPeripheral peripheral = new UltrasonicPeripheral(true, selected.triggerPin,
                selected.echoPin, measurement.echo ? measurement.echoCycles : 0L);
        AvrInputs inputs = new AvrInputs(new boolean[AvrInputs.DIGITAL_PIN_COUNT],
                new int[AvrInputs.ANALOG_CHANNEL_COUNT], peripheral);
        return new Sample(inputs, triggered.size() > 1 ? Status.CROSSTALK_SERIALIZED : Status.SAMPLED, selected);
    }

    private List<Sensor> enabled() {
        List<Sensor> values = new ArrayList<Sensor>();
        for (Sensor sensor : sensors) if (sensor.enabled) values.add(sensor);
        return values;
    }

    private List<Sensor> triggered(RoboBoardState board) {
        return triggered(board.getOutputMask(), board.getHighMask(), board.isRunning());
    }

    List<Sensor> triggered(int output, int high, boolean running) {
        List<Sensor> values = new ArrayList<Sensor>();
        if (!running) return values;
        for (Sensor sensor : sensors) if (sensor.enabled
                && (output & 1 << sensor.triggerPin) != 0 && (high & 1 << sensor.triggerPin) != 0) values.add(sensor);
        return values;
    }

    private static long positionSeed(GridVector value) {
        return ((long) value.x * 73856093L) ^ ((long) value.y * 19349663L) ^ ((long) value.z * 83492791L);
    }

    public enum Status { IDLE, SAMPLED, CROSSTALK_SERIALIZED, ACTIVE_LIMIT_EXCEEDED }
    public static final class Sample {
        public final AvrInputs inputs; public final Status status; public final Sensor selected;
        Sample(AvrInputs inputs, Status status, Sensor selected) {
            this.inputs = inputs; this.status = status; this.selected = selected;
        }
    }

    public static final class Sensor {
        public final GridVector position; public final ComponentOrientation orientation;
        public final boolean enabled; public final int triggerPin, echoPin;
        Sensor(GridVector position, ComponentOrientation orientation,
                MobileElectricalEvaluation.SensorBinding binding) {
            this.position = position; this.orientation = orientation;
            triggerPin = binding == null ? -1 : pin(binding.triggerRole);
            echoPin = binding == null ? -1 : pin(binding.echoRole);
            enabled = binding != null && binding.enabled && triggerPin >= 0 && echoPin >= 0
                    && triggerPin != echoPin;
        }

        public UltrasonicSensorPose pose(double robotX, double robotY, double robotZ, double robotYawRadians) {
            Direction forward = orientation.getForward();
            double localX = position.x + 0.5 - 0.5 + forward.vector.x * EMITTER_FORWARD_OFFSET_METRES;
            double localY = position.y + 0.5 + forward.vector.y * EMITTER_FORWARD_OFFSET_METRES;
            double localZ = position.z + 0.5 - 0.5 + forward.vector.z * EMITTER_FORWARD_OFFSET_METRES;
            double cosine = StrictMath.cos(robotYawRadians), sine = StrictMath.sin(robotYawRadians);
            double x = robotX + cosine * localX + sine * localZ;
            double z = robotZ - sine * localX + cosine * localZ;
            double dx = cosine * forward.vector.x + sine * forward.vector.z;
            double dz = -sine * forward.vector.x + cosine * forward.vector.z;
            double horizontal = StrictMath.sqrt(dx * dx + dz * dz);
            double yaw = StrictMath.toDegrees(StrictMath.atan2(-dx, dz));
            double pitch = StrictMath.toDegrees(StrictMath.atan2(-forward.vector.y, horizontal));
            pitch = StrictMath.max(-89.0, StrictMath.min(89.0, pitch));
            return new UltrasonicSensorPose(x, robotY + localY, z, yaw, pitch);
        }
    }

    private static int pin(String role) {
        return role != null && role.matches("D(?:[0-9]|1[0-3])")
                ? Integer.parseInt(role.substring(1)) : -1;
    }
    private static final Comparator<Sensor> SENSOR_ORDER = new Comparator<Sensor>() {
        @Override public int compare(Sensor a, Sensor b) {
            if (a.position.x != b.position.x) return a.position.x < b.position.x ? -1 : 1;
            if (a.position.y != b.position.y) return a.position.y < b.position.y ? -1 : 1;
            return a.position.z == b.position.z ? 0 : a.position.z < b.position.z ? -1 : 1;
        }
    };
}
