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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import br.com.craftonica.persistence.NbtMigrations;

/** Server-authoritative persistent entity for an arbitrary captured rigid assembly. */
public final class EntityModularRobot extends Entity {
    private static final ComponentCatalog CATALOG = StandardComponentCatalog.create();
    private static final double SUBSTEP_SECONDS = 0.025;
    private ModularRobotState state;
    private RigidBodyProperties body;
    private TerrestrialRigidBodyModel.State dynamics;
    private CoupledDriveLoop coupledDrive;
    private CoupledDriveLoop.State coupledDriveState;
    private CoupledDriveLoop.ControlFrame pendingControlFrame;
    private RoboBoardState boardState;
    private MobileUltrasonicSystem ultrasonicSystem;
    private long sensorSampleCounter;
    private MobileUltrasonicSystem.Status lastSensorStatus = MobileUltrasonicSystem.Status.IDLE;
    private NBTTagCompound preservedInvalidEnvelope;
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
        value.rotationYaw = yawDegrees(orientation.getForward());
        value.setPosition(anchor.x + 0.5, anchor.y, anchor.z + 0.5);
        value.dynamics = value.currentState(0.0, 0.0, 0.0, 0.0);
        value.configureBounds();
        return value;
    }

    @Override protected void entityInit() { }
    @Override public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote || state == null) return;
        if (!state.canSimulate()) { stopDynamics(); pendingForces.clear(); return; }
        if (!ModularRobotTickBudget.allows(this)) { motionX = motionY = motionZ = 0.0; return; }
        updateRuntime();
        ensureBody();
        ForgeRigidBodyWorld collisionWorld = new ForgeRigidBodyWorld(worldObj, this);
        List<Integer> supported = collisionWorld.supportedContacts(body, dynamics);
        if (supported == null) { stopDynamics(); pendingForces.clear(); return; }
        List<TerrestrialRigidBodyModel.AppliedForce> forces =
                new ArrayList<TerrestrialRigidBodyModel.AppliedForce>(pendingForces);
        pendingForces.clear();
        if (pendingControlFrame != null) {
            CoupledDriveLoop.Result drive = coupledDrive.step(coupledDriveState, pendingControlFrame,
                    dynamics, supported, new SimulationTickBudget(), 0.05);
            if (!drive.delayed) {
                coupledDriveState = drive.state; forces.addAll(drive.forces); pendingControlFrame = null;
            }
        }
        integrateSubstep(collisionWorld, supported, forces);
        integrateSubstep(collisionWorld, supported, forces);
        setPosition(dynamics.x, dynamics.y, dynamics.z);
        rotationYaw = (float) StrictMath.toDegrees(dynamics.yawRadians);
        motionX = dynamics.velocityX / 20.0; motionY = dynamics.velocityY / 20.0;
        motionZ = dynamics.velocityZ / 20.0;
    }

    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        if (preservedInvalidEnvelope != null) {
            tag.setTag("CraftonicaModularRobotV2", NbtMigrations.copy(preservedInvalidEnvelope)); return;
        }
        if (state == null) return;
        ensureBody();
        tag.setTag("CraftonicaModularRobotV2", ModularRobotPersistence.write(state, dynamics,
                coupledDrive, coupledDriveState, boardState, sensorSampleCounter));
    }

    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        try {
            if (tag.hasKey("CraftonicaModularRobotV2")) {
                NBTTagCompound envelope = tag.getCompoundTag("CraftonicaModularRobotV2");
                ModularRobotPersistence.Snapshot restored = ModularRobotPersistence.read(envelope, CATALOG);
                state = restored.robot; dynamics = restored.body; coupledDrive = restored.drive;
                coupledDriveState = restored.driveState; boardState = restored.board;
                sensorSampleCounter = restored.sensorCounter;
                setPositionAndRotation(dynamics.x, dynamics.y, dynamics.z,
                        (float) StrictMath.toDegrees(dynamics.yawRadians), 0.0F);
            } else {
                readDevelopmentSchemaOne(tag);
            }
            body = RigidBodyProperties.derive(state.getManifest(), CATALOG);
            ultrasonicSystem = new MobileUltrasonicSystem(state.getManifest());
            configureBounds();
        } catch (RuntimeException invalid) {
            NBTTagCompound source = tag.hasKey("CraftonicaModularRobotV2")
                    ? tag.getCompoundTag("CraftonicaModularRobotV2") : tag;
            preservedInvalidEnvelope = ModularRobotPersistence.preserve(source);
            state = ModularRobotState.quarantined("INVALID_PERSISTED_STATE");
            body = RigidBodyProperties.derive(state.getManifest(), CATALOG);
            coupledDrive = new CoupledDriveLoop(state.getManifest(), CATALOG);
            coupledDriveState = coupledDrive.initialState(); boardState = null;
            ultrasonicSystem = new MobileUltrasonicSystem(state.getManifest()); sensorSampleCounter = 0L;
            dynamics = currentState(0.0, 0.0, 0.0, 0.0); configureBounds();
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
                        + (state.getDiagnostic().length() == 0 ? "" : ", estado=" + state.getDiagnostic())));
            }
        }
        return true;
    }

    public ModularRobotState getRobotState() { return state; }
    public RigidBodyProperties getRigidBodyProperties() { ensureBody(); return body; }
    public MobileUltrasonicSystem.Status getLastSensorStatus() { return lastSensorStatus; }
    public NBTTagCompound getPreservedInvalidEnvelope() {
        return preservedInvalidEnvelope == null ? null : NbtMigrations.copy(preservedInvalidEnvelope);
    }
    /** Cancels process-local work without advancing or rewriting persisted simulation state. */
    public void prepareForChunkUnload() {
        if (worldObj != null && worldObj.isRemote) return;
        runtimeHost.cancel(); pendingControlFrame = null; pendingForces.clear();
        motionX = motionY = motionZ = 0.0;
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

    private void updateRuntime() {
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

    private CompoundCollisionProbe.Result collision(ForgeRigidBodyWorld world,
            TerrestrialRigidBodyModel.State from, TerrestrialRigidBodyModel.State candidate) {
        return CompoundCollisionProbe.sweep(body, world, from, candidate);
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
