package br.com.craftonica.robot.modular;

import br.com.craftonica.registry.ModItems;
import br.com.craftonica.robot.modular.transaction.forge.ForgeModularAssemblyService;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.physics.forge.ForgeRigidBodyWorld;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.drive.SimulationTickBudget;
import br.com.craftonica.robot.modular.drive.forge.ModularRobotTickBudget;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.runtime.server.RoboBoardRuntimeHost;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.RoboBoardStateNbtCodec;
import br.com.craftonica.robot.modular.sensor.MobileUltrasonicSystem;
import br.com.craftonica.robot.modular.sensor.MobileMotionSensorSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import br.com.craftonica.persistence.NbtMigrations;
import br.com.craftonica.robot.modular.visual.ModularRobotVisualState;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedMechanism;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedSolver;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedTickBudget;
import br.com.craftonica.robot.modular.servo.ServoJointLoop;
import br.com.craftonica.robot.modular.assembly.TrackAssembly;
import br.com.craftonica.robot.modular.assembly.TrackAssemblyAnalyzer;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** Server-authoritative persistent entity for an arbitrary captured rigid assembly. */
public final class EntityModularRobot extends Entity implements IEntityAdditionalSpawnData {
    private static final ComponentCatalog CATALOG = StandardComponentCatalog.create();
    private static final double SUBSTEP_SECONDS = 0.025;
    private static final int WATCH_STATUS = 20;
    private static final int WATCH_DIAGNOSTICS = 21;
    private static final int WATCH_MECHANICAL_PHASE = 22;
    private static final int WATCH_JOINT_STATES = 23;
    public static final int DIAGNOSTIC_ELECTRICAL = 1;
    public static final int DIAGNOSTIC_DRIVE = 2;
    public static final int DIAGNOSTIC_THERMAL = 4;
    public static final int DIAGNOSTIC_SENSOR = 8;
    public static final int DIAGNOSTIC_QUARANTINE = 16;
    public static final int DIAGNOSTIC_ARTICULATED = 32;
    private ModularRobotState state;
    private RigidBodyProperties body;
    private RigidBodyProperties rootBody;
    private TerrestrialRigidBodyModel.State dynamics;
    private CoupledDriveLoop coupledDrive;
    private CoupledDriveLoop.State coupledDriveState;
    private CoupledDriveLoop.ControlFrame pendingControlFrame;
    private RoboBoardState boardState;
    private MobileUltrasonicSystem ultrasonicSystem;
    private long sensorSampleCounter;
    private MobileUltrasonicSystem.Status lastSensorStatus = MobileUltrasonicSystem.Status.IDLE;
    private MobileMotionSensorSystem motionSensors;
    private MobileMotionSensorSystem.State motionSensorState;
    private MobileMotionSensorSystem.Status lastMotionSensorStatus = MobileMotionSensorSystem.Status.OK;
    private NBTTagCompound preservedInvalidEnvelope;
    private ModularRobotVisualState visualState;
    private float mechanicalPhaseDegrees;
    private JointStateSet jointStates = JointStateSet.EMPTY;
    private ArticulatedMechanism articulatedMechanism;
    private ServoJointLoop servoJointLoop;
    private ServoJointLoop.State servoJointState;
    private ArticulatedSolver.Status articulatedStatus = ArticulatedSolver.Status.ADVANCED;
    private TrackAssembly trackAssembly;
    private boolean visualElectricalFault;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private int interpolationTicks;
    private String lastClientJointPayload = "";
    private final RoboBoardRuntimeHost runtimeHost = new RoboBoardRuntimeHost();
    private final List<TerrestrialRigidBodyModel.AppliedForce> pendingForces =
            new ArrayList<TerrestrialRigidBodyModel.AppliedForce>();

    public EntityModularRobot(World world) {
        super(world); setSize(1.0F, 1.0F); preventEntitySpawning = true;
    }

    public static EntityModularRobot create(World world, UUID robotId, UUID ownerId, GridVector anchor,
            ComponentOrientation orientation, br.com.craftonica.robot.modular.manifest.ModularRobotManifest manifest) {
        if (world == null || world.isRemote) throw new IllegalArgumentException("world");
        EntityModularRobot value = new EntityModularRobot(world);
        value.state = new ModularRobotState(robotId, ownerId, anchor, orientation, manifest);
        value.body = RigidBodyProperties.derive(manifest, CATALOG);
        value.coupledDrive = new CoupledDriveLoop(manifest, CATALOG);
        value.coupledDriveState = value.coupledDrive.initialState();
        value.boardState = board(manifest);
        value.ultrasonicSystem = new MobileUltrasonicSystem(manifest);
        value.visualState = ModularRobotVisualState.fromManifest(manifest);
        value.jointStates = JointStateSet.initial(KinematicAssemblyAnalyzer.analyze(manifest, CATALOG));
        value.initializeArticulated(manifest);
        value.visualElectricalFault = !MobileElectricalEvaluator.evaluate(
                manifest.getElectricalNetlist()).getDiagnostics().isEmpty();
        value.rotationYaw = yawDegrees(orientation.getForward());
        value.setPosition(anchor.x + 0.5, anchor.y, anchor.z + 0.5);
        value.dynamics = value.currentState(0.0, 0.0, 0.0, 0.0);
        value.configureBounds();
        value.syncVisualState();
        return value;
    }

    @Override protected void entityInit() {
        dataWatcher.addObject(WATCH_STATUS, Integer.valueOf(ModularRobotState.Status.QUARANTINED.ordinal()));
        dataWatcher.addObject(WATCH_DIAGNOSTICS, Integer.valueOf(DIAGNOSTIC_QUARANTINE));
        dataWatcher.addObject(WATCH_MECHANICAL_PHASE, Float.valueOf(0.0F));
        dataWatcher.addObject(WATCH_JOINT_STATES, "");
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) { interpolateClientPose(); syncClientJointStates(); return; }
        if (state == null) return;
        if (!state.canSimulate()) { stopDynamics(); pendingForces.clear(); syncVisualState(); return; }
        if (!ModularRobotTickBudget.allows(this)) { motionX = motionY = motionZ = 0.0; return; }
        ensureBody();
        ForgeRigidBodyWorld collisionWorld = new ForgeRigidBodyWorld(worldObj, this);
        updateRuntime(collisionWorld);
        ArticulatedTickBudget articulatedBudget = new ArticulatedTickBudget();
        List<Integer> supported = collisionWorld.supportedContacts(rootBody == null ? body : rootBody, dynamics);
        if (supported == null) { stopDynamics(); pendingForces.clear(); return; }
        List<TerrestrialRigidBodyModel.AppliedForce> forces =
                new ArrayList<TerrestrialRigidBodyModel.AppliedForce>(pendingForces);
        pendingForces.clear();
        if (pendingControlFrame != null) {
            CoupledDriveLoop.Result drive = coupledDrive.step(coupledDriveState, pendingControlFrame,
                    dynamics, supported, collisionWorld.surfaceFrictionMultipliers(
                            rootBody == null ? body : rootBody, dynamics, supported),
                    new SimulationTickBudget(), 0.05);
            if (!drive.delayed) {
                coupledDriveState = drive.state; forces.addAll(drive.forces); pendingControlFrame = null;
            }
        }
        integrateSubstep(collisionWorld, supported, forces);
        integrateArticulated(collisionWorld, articulatedBudget);
        integrateSubstep(collisionWorld, supported, forces);
        integrateArticulated(collisionWorld, articulatedBudget);
        setPosition(dynamics.x, dynamics.y, dynamics.z);
        rotationYaw = (float) StrictMath.toDegrees(dynamics.yawRadians);
        motionX = dynamics.velocityX / 20.0; motionY = dynamics.velocityY / 20.0;
        motionZ = dynamics.velocityZ / 20.0;
        advanceMechanicalPhase(); syncVisualState();
    }

    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        if (preservedInvalidEnvelope != null) {
            tag.setTag("CraftonicaModularRobotV2", NbtMigrations.copy(preservedInvalidEnvelope)); return;
        }
        if (state == null) return;
        ensureBody();
        tag.setTag("CraftonicaModularRobotV2", ModularRobotPersistence.write(state, dynamics,
                coupledDrive, coupledDriveState, boardState, sensorSampleCounter, jointStates,
                servoJointLoop, servoJointState,motionSensors,motionSensorState));
    }

    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        try {
            if (tag.hasKey("CraftonicaModularRobotV2")) {
                NBTTagCompound envelope = tag.getCompoundTag("CraftonicaModularRobotV2");
                ModularRobotPersistence.Snapshot restored = ModularRobotPersistence.read(envelope, CATALOG);
                state = restored.robot; dynamics = restored.body; coupledDrive = restored.drive;
                coupledDriveState = restored.driveState; boardState = restored.board;
                sensorSampleCounter = restored.sensorCounter;
                jointStates = restored.joints;
                servoJointLoop = restored.servoLoop; servoJointState = restored.servoState;
                motionSensors=restored.motionSensors;motionSensorState=restored.motionSensorState;
                setPositionAndRotation(dynamics.x, dynamics.y, dynamics.z,
                        (float) StrictMath.toDegrees(dynamics.yawRadians), 0.0F);
            } else {
                readDevelopmentSchemaOne(tag);
            }
            body = RigidBodyProperties.derive(state.getManifest(), CATALOG);
            ultrasonicSystem = new MobileUltrasonicSystem(state.getManifest());
            visualState = ModularRobotVisualState.fromManifest(state.getManifest());
            visualElectricalFault = !MobileElectricalEvaluator.evaluate(
                    state.getManifest().getElectricalNetlist()).getDiagnostics().isEmpty();
            configureBounds(); syncVisualState();
        } catch (RuntimeException invalid) {
            NBTTagCompound source = tag.hasKey("CraftonicaModularRobotV2")
                    ? tag.getCompoundTag("CraftonicaModularRobotV2") : tag;
            preservedInvalidEnvelope = ModularRobotPersistence.preserve(source);
            state = ModularRobotState.quarantined("INVALID_PERSISTED_STATE");
            body = RigidBodyProperties.derive(state.getManifest(), CATALOG);
            coupledDrive = new CoupledDriveLoop(state.getManifest(), CATALOG);
            coupledDriveState = coupledDrive.initialState(); boardState = null;
            ultrasonicSystem = new MobileUltrasonicSystem(state.getManifest()); sensorSampleCounter = 0L;
            visualState = ModularRobotVisualState.fromManifest(state.getManifest());
            articulatedMechanism = null; servoJointLoop = null; servoJointState = null;
            motionSensors=null;motionSensorState=null;
            initializeArticulated(state.getManifest());
            visualElectricalFault = true;
            dynamics = currentState(0.0, 0.0, 0.0, 0.0); configureBounds(); syncVisualState();
        }
    }

    private void readDevelopmentSchemaOne(NBTTagCompound tag) {
        state = ModularRobotState.read(tag.getCompoundTag("CraftonicaModularRobot"));
        coupledDrive = new CoupledDriveLoop(state.getManifest(), CATALOG);
        coupledDriveState = coupledDrive.initialState();
        boardState = tag.hasKey("CraftonicaMobileBoard")
                ? RoboBoardStateNbtCodec.read(tag.getCompoundTag("CraftonicaMobileBoard"))
                : board(state.getManifest());
        if (boardState != null && ("UNKNOWN_SCHEMA".equals(boardState.getFault())
                || "INVALID_IDENTITY".equals(boardState.getFault())
                || "INVALID_PERSISTED_STATE".equals(boardState.getFault())))
            throw new IllegalArgumentException("board state");
        sensorSampleCounter = tag.getLong("CraftonicaSensorCounter");
        if (sensorSampleCounter < 0) throw new IllegalArgumentException("sensor counter");
        NBTTagCompound value = tag.getCompoundTag("CraftonicaRigidBody");
        double vx = value.getDouble("VelocityX"), vy = value.getDouble("VelocityY");
        double vz = value.getDouble("VelocityZ"), angular = value.getDouble("AngularVelocity");
        if (StrictMath.abs(vx) > 100.0 || StrictMath.abs(vy) > 100.0 || StrictMath.abs(vz) > 100.0
                || StrictMath.abs(angular) > 100.0) throw new IllegalArgumentException("rigid body velocity");
        dynamics = currentState(vx, vy, vz, angular);
        jointStates = JointStateSet.initial(KinematicAssemblyAnalyzer.analyze(state.getManifest(), CATALOG));
        initializeArticulated(state.getManifest());
    }

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote && state != null) {
            if (player.getCurrentEquippedItem() != null && player.getCurrentEquippedItem().getItem() == ModItems.WRENCH)
                ForgeModularAssemblyService.disassemble(this, player);
            else {
                MobileElectricalEvaluation electrical = getElectricalEvaluation();
                player.addChatMessage(new ChatComponentText("Robô modular " + state.getRobotId()
                        + " — " + state.getStatus().name() + ", redes="
                        + state.getManifest().getElectricalNetlist().getNetworkCount()
                        + ", diagnósticos=" + electrical.getDiagnostics().size()
                        + ", sensores=" + lastSensorStatus.name()
                        + ", movimento=" + lastMotionSensorStatus.name()
                        + ", esteiras=" + (trackAssembly == null ? 0 : trackAssembly.getUnits().size())
                        + ", falhasEsteira=" + (trackAssembly == null ? 0 : trackAssembly.getDiagnostics().size())
                        + (state.getDiagnostic().length() == 0 ? "" : ", estado=" + state.getDiagnostic())));
            }
        }
        return true;
    }

    public ModularRobotState getRobotState() { return state; }
    public RigidBodyProperties getRigidBodyProperties() { ensureBody(); return body; }
    public MobileUltrasonicSystem.Status getLastSensorStatus() { return lastSensorStatus; }
    public ModularRobotVisualState getVisualState() { return visualState; }
    public JointStateSet getJointStates() { return jointStates; }
    public int getVisualStatus() { return dataWatcher.getWatchableObjectInt(WATCH_STATUS); }
    public int getVisualDiagnostics() { return dataWatcher.getWatchableObjectInt(WATCH_DIAGNOSTICS); }
    public float getMechanicalPhaseDegrees() { return dataWatcher.getWatchableObjectFloat(WATCH_MECHANICAL_PHASE); }
    public NBTTagCompound getPreservedInvalidEnvelope() {
        return preservedInvalidEnvelope == null ? null : NbtMigrations.copy(preservedInvalidEnvelope);
    }
    /** Cancels process-local work without advancing or rewriting persisted simulation state. */
    public void prepareForChunkUnload() {
        if (worldObj != null && worldObj.isRemote) return;
        runtimeHost.cancel(); pendingControlFrame = null; pendingForces.clear();
        motionX = motionY = motionZ = 0.0;
    }

    @Override public void writeSpawnData(ByteBuf buffer) {
        if (visualState == null) throw new IllegalStateException("missing visual state");
        int lengthIndex = buffer.writerIndex(); buffer.writeShort(0); int start = buffer.writerIndex();
        visualState.write(buffer); int length = buffer.writerIndex() - start;
        if (length > ModularRobotVisualState.MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("visual payload");
        buffer.setShort(lengthIndex, length); jointStates.writeClient(buffer);
    }

    @Override public void readSpawnData(ByteBuf buffer) {
        try {
            if (buffer.readableBytes() < 2) throw new IllegalArgumentException("spawn payload");
            int visualBytes = buffer.readUnsignedShort();
            if (visualBytes < 1 || visualBytes > ModularRobotVisualState.MAX_PAYLOAD_BYTES
                    || buffer.readableBytes() < visualBytes + 2) throw new IllegalArgumentException("spawn bounds");
            visualState = ModularRobotVisualState.read(buffer.readSlice(visualBytes));
            jointStates = JointStateSet.readClient(buffer);
        } catch (RuntimeException invalid) { visualState = null; jointStates = JointStateSet.EMPTY; }
    }

    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch,
            int increments) {
        if (worldObj == null || !worldObj.isRemote) { setPositionAndRotation(x, y, z, yaw, pitch); return; }
        targetX = x; targetY = y; targetZ = z; targetYaw = yaw; targetPitch = pitch;
        interpolationTicks = StrictMath.max(1, StrictMath.min(5, increments));
    }

    private void interpolateClientPose() {
        if (interpolationTicks <= 0) return;
        double divisor = interpolationTicks;
        setPosition(posX + (targetX - posX) / divisor, posY + (targetY - posY) / divisor,
                posZ + (targetZ - posZ) / divisor);
        rotationYaw += wrapDegrees(targetYaw - rotationYaw) / divisor;
        rotationPitch += (targetPitch - rotationPitch) / divisor; interpolationTicks--;
    }

    private static float wrapDegrees(float value) {
        value %= 360.0F; return value < -180.0F ? value + 360.0F : value >= 180.0F ? value - 360.0F : value;
    }

    private void advanceMechanicalPhase() {
        if (coupledDriveState == null || coupledDriveState.getChannels().isEmpty()) return;
        double sum = 0.0;
        for (CoupledDriveLoop.ChannelState channel : coupledDriveState.getChannels())
            sum += StrictMath.abs(channel.drive.angularVelocityRadPerSecond);
        mechanicalPhaseDegrees += (float) StrictMath.toDegrees(sum / coupledDriveState.getChannels().size() * 0.05);
        mechanicalPhaseDegrees %= 360.0F;
    }

    private void syncVisualState() {
        if (dataWatcher == null || state == null) return;
        int diagnostics = 0;
        if (state.getStatus() == ModularRobotState.Status.QUARANTINED) diagnostics |= DIAGNOSTIC_QUARANTINE;
        if (visualElectricalFault) diagnostics |= DIAGNOSTIC_ELECTRICAL;
        if (lastSensorStatus == MobileUltrasonicSystem.Status.CROSSTALK_SERIALIZED
                || lastSensorStatus == MobileUltrasonicSystem.Status.ACTIVE_LIMIT_EXCEEDED)
            diagnostics |= DIAGNOSTIC_SENSOR;
        if(lastMotionSensorStatus!=MobileMotionSensorSystem.Status.OK)diagnostics|=DIAGNOSTIC_SENSOR;
        if (coupledDriveState != null) for (CoupledDriveLoop.ChannelState channel : coupledDriveState.getChannels()) {
            if (channel.diagnostic != br.com.craftonica.robot.modular.drive.DriveStep.Diagnostic.NONE)
                diagnostics |= DIAGNOSTIC_DRIVE;
            if (channel.drive.thermalShutdown) diagnostics |= DIAGNOSTIC_THERMAL;
        }
        if (articulatedStatus != ArticulatedSolver.Status.ADVANCED) diagnostics |= DIAGNOSTIC_ARTICULATED;
        if (trackAssembly != null && !trackAssembly.isValid()) diagnostics |= DIAGNOSTIC_DRIVE;
        if (servoJointState != null) for (ServoJointLoop.ChannelState channel : servoJointState.getChannels()) {
            if (channel.diagnostic != br.com.craftonica.robot.modular.servo.ServoStep.Diagnostic.NONE)
                diagnostics |= DIAGNOSTIC_DRIVE;
            if (channel.servo.thermalShutdown) diagnostics |= DIAGNOSTIC_THERMAL;
        }
        dataWatcher.updateObject(WATCH_STATUS, Integer.valueOf(state.getStatus().ordinal()));
        dataWatcher.updateObject(WATCH_DIAGNOSTICS, Integer.valueOf(diagnostics));
        dataWatcher.updateObject(WATCH_MECHANICAL_PHASE, Float.valueOf(mechanicalPhaseDegrees));
        dataWatcher.updateObject(WATCH_JOINT_STATES, jointStates.encodeClientString());
    }

    private void syncClientJointStates() {
        String payload = dataWatcher.getWatchableObjectString(WATCH_JOINT_STATES);
        if (payload == null || payload.length() == 0 || payload.equals(lastClientJointPayload)) return;
        try { jointStates = JointStateSet.decodeClientString(payload); lastClientJointPayload = payload; }
        catch (RuntimeException ignored) { }
    }
    public void applyForceForNextTick(TerrestrialRigidBodyModel.AppliedForce force) {
        if (worldObj.isRemote || force == null || pendingForces.size() >= TerrestrialRigidBodyModel.MAX_FORCES_PER_SUBSTEP)
            return;
        pendingForces.add(force);
    }
    /** Accepts at most one sequential, already-confirmed AVR frame; no client packet calls this API. */
    public boolean submitConfirmedControlFrame(CoupledDriveLoop.ControlFrame frame) {
        if (worldObj.isRemote || frame == null || pendingControlFrame != null || coupledDriveState == null
                || frame.sequence != coupledDriveState.nextSequence) return false;
        pendingControlFrame = frame; return true;
    }
    public MobileElectricalEvaluation getElectricalEvaluation() {
        return MobileElectricalEvaluator.evaluate(state == null
                ? br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist.EMPTY
                : state.getManifest().getElectricalNetlist());
    }

    private void updateRuntime(ForgeRigidBodyWorld sensorWorld) {
        if (boardState == null || !boardState.isRunning()) {
            runtimeHost.cancel(); lastSensorStatus = MobileUltrasonicSystem.Status.IDLE; return;
        }
        RuntimeProtocol.Identity identity = RuntimeProtocol.Identity.mobile(worldObj.provider.dimensionId,
                state.getRobotId(), boardState.getGeneration());
        AvrInputs inputs = AvrInputs.allLow();
        if (runtimeHost.needsInput() && ultrasonicSystem != null) {
            long seed = worldObj.getSeed() ^ state.getRobotId().getMostSignificantBits()
                    ^ state.getRobotId().getLeastSignificantBits() ^ sensorSampleCounter;
            sensorSampleCounter = sensorSampleCounter == Long.MAX_VALUE ? 0L : sensorSampleCounter + 1L;
            MobileUltrasonicSystem.Sample sample = ultrasonicSystem.sample(worldObj, this, boardState,
                    posX, posY, posZ, StrictMath.toRadians(rotationYaw), seed);
            inputs = sample.inputs; lastSensorStatus = sample.status;
            if(motionSensors!=null&&motionSensorState!=null){
                br.com.craftonica.robot.modular.physics.articulated.ArticulatedPose pose=articulatedMechanism.pose(
                        jointStates,ArticulatedSolver.rootTransform(dynamics.x,dynamics.y,dynamics.z,dynamics.yawRadians));
                MobileMotionSensorSystem.Sample motion=motionSensors.sample(motionSensorState,inputs,coupledDrive,
                        coupledDriveState,dynamics,pose,sensorWorld,seed^0x6d6f74696f6eL);
                motionSensorState=motion.state;inputs=motion.inputs;lastMotionSensorStatus=motion.status;
            }
        }
        runtimeHost.tick(identity, worldObj.getTotalWorldTime(), boardState, inputs,
                new RoboBoardRuntimeHost.OutputListener() {
                    @Override public void committed(RoboBoardState committed, RuntimeProtocol.Result result) {
                        if (pendingControlFrame == null)
                            submitConfirmedControlFrame(coupledDrive.controlFrame(
                                    coupledDriveState.nextSequence, committed, 5.0));
                    }
                });
    }

    private void integrateSubstep(ForgeRigidBodyWorld world, List<Integer> supported,
            List<TerrestrialRigidBodyModel.AppliedForce> forces) {
        world.beginSubstep();
        TerrestrialRigidBodyModel.State before = dynamics;
        TerrestrialRigidBodyModel.State proposed = TerrestrialRigidBodyModel.step(
                body, before, forces, supported, SUBSTEP_SECONDS);
        CompoundCollisionProbe.Result full = collision(world, before, proposed);
        if (full == CompoundCollisionProbe.Result.CLEAR) { dynamics = proposed; return; }
        if (full == CompoundCollisionProbe.Result.UNLOADED) { stopDynamics(); return; }
        TerrestrialRigidBodyModel.State vertical = new TerrestrialRigidBodyModel.State(
                before.x, proposed.y, before.z, before.yawRadians, before.velocityX,
                proposed.velocityY, before.velocityZ, before.angularVelocityRadiansPerSecond);
        if (collision(world, before, vertical) == CompoundCollisionProbe.Result.CLEAR) dynamics = vertical;
        else dynamics = before.stopVertical(before.y);
        TerrestrialRigidBodyModel.State planarStart = dynamics;
        TerrestrialRigidBodyModel.State planar = new TerrestrialRigidBodyModel.State(
                proposed.x, dynamics.y, proposed.z, proposed.yawRadians, proposed.velocityX,
                dynamics.velocityY, proposed.velocityZ, proposed.angularVelocityRadiansPerSecond);
        CompoundCollisionProbe.Result planarResult = collision(world, planarStart, planar);
        if (planarResult == CompoundCollisionProbe.Result.CLEAR) dynamics = planar;
        else dynamics = dynamics.stopPlanar();
    }

    private void integrateArticulated(ForgeRigidBodyWorld world, ArticulatedTickBudget budget) {
        if (articulatedMechanism == null || articulatedMechanism.getKinematic().getJoints().isEmpty()) return;
        ServoJointLoop.Evaluation servo = servoJointLoop.evaluate(servoJointState, jointStates, boardState, SUBSTEP_SECONDS);
        world.beginSubstep();
        ArticulatedSolver.Result result = ArticulatedSolver.step(articulatedMechanism, jointStates,
                servo.efforts, ArticulatedSolver.rootTransform(dynamics.x, dynamics.y, dynamics.z,
                        dynamics.yawRadians), world, budget, SUBSTEP_SECONDS);
        articulatedStatus = result.status;
        if (!result.delayed) jointStates = result.state;
        servoJointState = servoJointLoop.applyReactions(servo.state, result.reactions);
    }

    private CompoundCollisionProbe.Result collision(ForgeRigidBodyWorld world,
            TerrestrialRigidBodyModel.State from, TerrestrialRigidBodyModel.State candidate) {
        return CompoundCollisionProbe.sweep(rootBody == null ? body : rootBody, world, from, candidate);
    }

    private void stopDynamics() {
        dynamics = new TerrestrialRigidBodyModel.State(posX, posY, posZ,
                StrictMath.toRadians(rotationYaw), 0.0, 0.0, 0.0, 0.0);
        motionX = motionY = motionZ = 0.0;
    }

    private TerrestrialRigidBodyModel.State currentState(double vx, double vy, double vz, double angular) {
        return new TerrestrialRigidBodyModel.State(posX, posY, posZ,
                StrictMath.toRadians(rotationYaw), vx, vy, vz, angular);
    }

    private void ensureBody() {
        if (body == null && state != null) { body = RigidBodyProperties.derive(state.getManifest(), CATALOG); configureBounds(); }
        if (dynamics == null) dynamics = currentState(0.0, 0.0, 0.0, 0.0);
        if (coupledDrive == null && state != null) {
            coupledDrive = new CoupledDriveLoop(state.getManifest(), CATALOG);
            coupledDriveState = coupledDrive.initialState();
        }
        if (ultrasonicSystem == null && state != null)
            ultrasonicSystem = new MobileUltrasonicSystem(state.getManifest());
        if (articulatedMechanism == null && state != null) initializeArticulated(state.getManifest());
    }

    private void initializeArticulated(br.com.craftonica.robot.modular.manifest.ModularRobotManifest manifest) {
        articulatedMechanism = new ArticulatedMechanism(manifest, CATALOG);
        trackAssembly = TrackAssemblyAnalyzer.analyze(manifest, CATALOG);
        rootBody = articulatedMechanism.getBodies().get(0);
        if (servoJointLoop == null) servoJointLoop = new ServoJointLoop(manifest, CATALOG,
                articulatedMechanism.getKinematic());
        if (servoJointState == null) servoJointState = servoJointLoop.initialState();
        if(motionSensors==null)motionSensors=new MobileMotionSensorSystem(manifest,CATALOG,articulatedMechanism);
        if(motionSensorState==null)motionSensorState=motionSensors.initialState();
    }

    private void configureBounds() {
        if (body == null) return;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (br.com.craftonica.robot.modular.physics.AxisAlignedVolume volume : body.getCollisionVolumes()) {
            minX = StrictMath.min(minX, volume.minimum.x); minY = StrictMath.min(minY, volume.minimum.y);
            minZ = StrictMath.min(minZ, volume.minimum.z); maxX = StrictMath.max(maxX, volume.maximum.x);
            maxY = StrictMath.max(maxY, volume.maximum.y); maxZ = StrictMath.max(maxZ, volume.maximum.z);
        }
        setSize((float) StrictMath.max(maxX - minX, maxZ - minZ), (float) (maxY - minY));
    }

    private static float yawDegrees(Direction forward) {
        if (forward == Direction.EAST) return -90.0F;
        if (forward == Direction.SOUTH) return 180.0F;
        if (forward == Direction.WEST) return 90.0F;
        return 0.0F;
    }

    private static RoboBoardState board(br.com.craftonica.robot.modular.manifest.ModularRobotManifest manifest) {
        for (ModularBlockSnapshot module : manifest.getModules()) {
            if (!StandardComponentCatalog.ROBO_BOARD.equals(module.componentTypeId)) continue;
            NBTTagCompound data = module.getTileData();
            return data == null ? null : RoboBoardStateNbtCodec.read(data);
        }
        return null;
    }

    @Override public void setDead() { runtimeHost.cancel(); super.setDead(); }
}
