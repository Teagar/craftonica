package br.com.craftonica.robot.modular.drive;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.MechanicalAssembly;
import br.com.craftonica.robot.modular.assembly.MechanicalAssemblyAnalyzer;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.tile.RoboBoardState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closes voltage/current/torque/contact velocity feedback for one confirmed AVR frame. */
public strictfp final class CoupledDriveLoop {
    public static final int MAX_CHANNELS = 16;
    private final List<Channel> channels;
    private final RigidBodyProperties body;
    private final DcMotorParameters motor = DcMotorParameters.educational();
    private final HBridgeParameters bridge = HBridgeParameters.educational();

    public CoupledDriveLoop(ModularRobotManifest manifest, ComponentCatalog catalog) {
        if (manifest == null || catalog == null) throw new IllegalArgumentException("manifest or catalog");
        body = RigidBodyProperties.derive(manifest, catalog);
        MechanicalAssembly mechanical = MechanicalAssemblyAnalyzer.analyze(manifest, catalog);
        MobileElectricalEvaluation electrical = MobileElectricalEvaluator.evaluate(manifest.getElectricalNetlist());
        List<GridVector> bridges = new ArrayList<GridVector>(electrical.getDrives().keySet());
        Collections.sort(bridges, POSITION_ORDER);
        List<Channel> values = new ArrayList<Channel>();
        for (GridVector bridgePosition : bridges) {
            MobileElectricalEvaluation.DriveBinding binding = electrical.getDrives().get(bridgePosition);
            MechanicalAssembly.DrivePath path = path(mechanical, binding.motorPosition);
            int contact = path == null ? -1 : contact(body, path.wheelPosition);
            values.add(new Channel(bridgePosition, binding, path, contact));
        }
        if (values.size() > MAX_CHANNELS) throw new IllegalArgumentException("maximum drive channels");
        channels = Collections.unmodifiableList(values);
    }

    public State initialState() {
        List<ChannelState> values = new ArrayList<ChannelState>();
        for (Channel ignored : channels) values.add(new ChannelState(
                DriveState.ambient(motor, bridge), 0.0, 0.0, DriveStep.Diagnostic.NONE));
        return new State(0L, values);
    }

    /** Converts a confirmed board snapshot through the physical RoboPort roles, never fixed drive pins. */
    public ControlFrame controlFrame(long sequence, RoboBoardState boardState, double supplyVolts) {
        if (boardState == null) throw new IllegalArgumentException("boardState");
        Map<GridVector, DriveInput> commands = new LinkedHashMap<GridVector, DriveInput>();
        int output = boardState.getOutputMask(), high = boardState.getHighMask();
        int pwmMask = boardState.getPwmMask(); int[] compare = boardState.getPwmCompare();
        for (Channel channel : channels) {
            int pwmPin = pin(channel.binding.pwmRole), directionPin = pin(channel.binding.directionRole);
            boolean configured = boardState.isRunning() && pwmPin >= 0 && directionPin >= 0
                    && (output & 1 << pwmPin) != 0 && (output & 1 << directionPin) != 0;
            int pwm = configured ? (pwmMask & 1 << pwmPin) != 0
                    ? StrictMath.min(255, compare[pwmPin]) : (high & 1 << pwmPin) != 0 ? 255 : 0 : 0;
            boolean direction = configured && (high & 1 << directionPin) != 0;
            commands.put(channel.bridgePosition, DriveInput.fromControlSignals(
                    configured && channel.binding.enabled, supplyVolts, pwm, direction, false, 0.0));
        }
        return new ControlFrame(sequence, commands);
    }

    public Result step(State previous, ControlFrame frame, TerrestrialRigidBodyModel.State rigidBody,
            List<Integer> supportedContacts, SimulationTickBudget budget, double seconds) {
        if (previous == null || rigidBody == null || supportedContacts == null || budget == null)
            throw new IllegalArgumentException("coupled step");
        if (previous.channels.size() != channels.size() || Double.isNaN(seconds)
                || Double.isInfinite(seconds) || seconds <= 0.0
                || seconds > ElectromechanicalDriveModel.MAX_STEP_SECONDS)
            throw new IllegalArgumentException("coupled state or step");
        if (frame == null || previous.nextSequence == Long.MAX_VALUE
                || frame.sequence != previous.nextSequence
                || !budget.reserveFrame(channels.size(), 2)) return Result.delayed(previous);
        List<ChannelState> next = new ArrayList<ChannelState>();
        List<TerrestrialRigidBodyModel.AppliedForce> forces =
                new ArrayList<TerrestrialRigidBodyModel.AppliedForce>();
        for (int index = 0; index < channels.size(); index++) {
            Channel channel = channels.get(index); ChannelState old = previous.channels.get(index);
            DriveInput command = frame.commands.get(channel.bridgePosition);
            if (command == null) command = new DriveInput(true, 0.0, 0, DriveInput.Mode.COAST, old.loadTorqueNm);
            else command = new DriveInput(command.wiringValid, command.supplyVolts, command.pwm,
                    command.mode, old.loadTorqueNm);
            double wheelVelocity = wheelVelocity(channel, rigidBody);
            DriveState feedback = new DriveState(wheelVelocity, old.drive.motorTemperatureCelsius,
                    old.drive.bridgeTemperatureCelsius, old.drive.thermalShutdown);
            DriveStep drive = MobileDriveSimulation.step(channel.binding, motor, bridge,
                    feedback, command, seconds);
            double load = 0.0;
            if (channel.contactIndex >= 0 && supportedContacts.contains(Integer.valueOf(channel.contactIndex))) {
                double force = drive.shaftTorqueNm / channel.path.wheelRadiusMetres;
                double pathLimit = channel.path.maximumTorqueNm / channel.path.wheelRadiusMetres;
                if (StrictMath.abs(force) > 0.0) forces.add(new TerrestrialRigidBodyModel.AppliedForce(
                        channel.contactIndex, force, StrictMath.min(StrictMath.abs(force), pathLimit)));
                double normal = body.massKg * TerrestrialRigidBodyModel.GRAVITY_METRES_PER_SECOND_SQUARED
                        / supportedContacts.size();
                double friction = body.getContacts().get(channel.contactIndex).longitudinalFriction * normal;
                load = StrictMath.copySign(StrictMath.min(StrictMath.min(StrictMath.abs(force), pathLimit), friction)
                        * channel.path.wheelRadiusMetres, drive.shaftTorqueNm);
            }
            next.add(new ChannelState(drive.state, load, drive.currentAmps, channel.path == null
                    ? DriveStep.Diagnostic.OPEN_CIRCUIT : drive.diagnostic));
        }
        return new Result(new State(previous.nextSequence + 1L, next), forces, false);
    }

    private double wheelVelocity(Channel channel, TerrestrialRigidBodyModel.State state) {
        if (channel.contactIndex < 0) return 0.0;
        RigidBodyProperties.Contact contact = body.getContacts().get(channel.contactIndex);
        double cosine = StrictMath.cos(state.yawRadians), sine = StrictMath.sin(state.yawRadians);
        double vx = cosine * state.velocityX - sine * state.velocityZ;
        double vz = sine * state.velocityX + cosine * state.velocityZ;
        double rx = contact.pointMetres.x - body.centerOfMassMetres.x;
        double rz = contact.pointMetres.z - body.centerOfMassMetres.z;
        double contactX = vx + state.angularVelocityRadiansPerSecond * rz;
        double contactZ = vz - state.angularVelocityRadiansPerSecond * rx;
        return (contactX * contact.rollingDirection.x + contactZ * contact.rollingDirection.z)
                / contact.radiusMetres;
    }

    private static MechanicalAssembly.DrivePath path(MechanicalAssembly mechanical, GridVector motor) {
        if (motor == null) return null;
        for (MechanicalAssembly.DrivePath path : mechanical.getDrives())
            if (motor.equals(path.motorPosition)) return path;
        return null;
    }
    private static int contact(RigidBodyProperties body, GridVector wheel) {
        for (int i = 0; i < body.getContacts().size(); i++)
            if (wheel.equals(body.getContacts().get(i).modulePosition)) return i;
        return -1;
    }
    private static int pin(String role) {
        if (role == null || !role.matches("D(?:[0-9]|1[0-3])")) return -1;
        return Integer.parseInt(role.substring(1));
    }

    private static final Comparator<GridVector> POSITION_ORDER = new Comparator<GridVector>() {
        @Override public int compare(GridVector a, GridVector b) {
            if (a.x != b.x) return a.x < b.x ? -1 : 1;
            if (a.y != b.y) return a.y < b.y ? -1 : 1;
            return a.z == b.z ? 0 : a.z < b.z ? -1 : 1;
        }
    };

    private static final class Channel {
        final GridVector bridgePosition; final MobileElectricalEvaluation.DriveBinding binding;
        final MechanicalAssembly.DrivePath path; final int contactIndex;
        Channel(GridVector bridgePosition, MobileElectricalEvaluation.DriveBinding binding,
                MechanicalAssembly.DrivePath path, int contactIndex) {
            this.bridgePosition = bridgePosition; this.binding = binding; this.path = path;
            this.contactIndex = contactIndex;
        }
    }
    public static final class ChannelState {
        public final DriveState drive; public final double loadTorqueNm, currentAmps;
        public final DriveStep.Diagnostic diagnostic;
        ChannelState(DriveState drive, double load, double current, DriveStep.Diagnostic diagnostic) {
            this.drive = drive; this.loadTorqueNm = load; this.currentAmps = current; this.diagnostic = diagnostic;
        }
    }
    public static final class State {
        public final long nextSequence; private final List<ChannelState> channels;
        State(long sequence, List<ChannelState> channels) {
            this.nextSequence = sequence;
            this.channels = Collections.unmodifiableList(new ArrayList<ChannelState>(channels));
        }
        public List<ChannelState> getChannels() { return channels; }
    }
    public static final class ControlFrame {
        public final long sequence; private final Map<GridVector, DriveInput> commands;
        public ControlFrame(long sequence, Map<GridVector, DriveInput> commands) {
            if (sequence < 0 || commands == null) throw new IllegalArgumentException("control frame");
            this.sequence = sequence;
            this.commands = Collections.unmodifiableMap(new LinkedHashMap<GridVector, DriveInput>(commands));
        }
    }
    public static final class Result {
        public final State state; public final List<TerrestrialRigidBodyModel.AppliedForce> forces;
        public final boolean delayed;
        Result(State state, List<TerrestrialRigidBodyModel.AppliedForce> forces, boolean delayed) {
            this.state = state; this.forces = Collections.unmodifiableList(
                    new ArrayList<TerrestrialRigidBodyModel.AppliedForce>(forces)); this.delayed = delayed;
        }
        static Result delayed(State state) {
            return new Result(state, Collections.<TerrestrialRigidBodyModel.AppliedForce>emptyList(), true);
        }
    }
}
