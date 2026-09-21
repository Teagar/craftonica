package br.com.craftonica.robot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.UUID;
import net.minecraft.util.MathHelper;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.runtime.server.RoboBoardRuntimeHost;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.persistence.NbtMigrations;

/** Server-authoritative persistent shell for the differential chassis. */
public final class EntityMobileRobot extends Entity {
    public static final float COLLISION_WIDTH = 1.8F;
    public static final float COLLISION_HEIGHT = 1.2F;
    private static final int WATCH_STATUS = 20;
    private static final int WATCH_MANIFEST = 21;
    private MobileRobotState state;
    private int loadGuardTicks = 1;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private int interpolationTicks;
    private HBridgeModel.Output leftDrive = HBridgeModel.evaluate(false, true, false, false, 0);
    private HBridgeModel.Output rightDrive = HBridgeModel.evaluate(false, true, false, false, 0);
    private double leftWheelSpeed, rightWheelSpeed;
    private final RoboBoardRuntimeHost runtimeHost = new RoboBoardRuntimeHost();
    private AvrInputs pendingInputs;
    private NBTTagCompound preservedInvalidState;

    public EntityMobileRobot(World world) {
        super(world);
        setSize(COLLISION_WIDTH, COLLISION_HEIGHT);
        preventEntitySpawning = true;
        state = MobileRobotState.quarantined();
    }

    public static EntityMobileRobot create(World world, UUID ownerId) {
        if (world == null || world.isRemote || ownerId == null) throw new IllegalArgumentException("robot owner");
        EntityMobileRobot robot = new EntityMobileRobot(world);
        robot.state = MobileRobotState.minimal(UUID.randomUUID(), ownerId);
        robot.syncVisualState();
        return robot;
    }

    static EntityMobileRobot createFromAssembly(World world, UUID ownerId, int facing, RoboBoardState boardState,
                                                 byte[] templateSketch) {
        if (world == null || world.isRemote || ownerId == null || boardState == null)
            throw new IllegalArgumentException("robot assembly");
        EntityMobileRobot robot = new EntityMobileRobot(world);
        robot.state = MobileRobotState.assembled(UUID.randomUUID(), ownerId, facing, boardState, templateSketch);
        robot.syncVisualState();
        return robot;
    }

    @Override protected void entityInit() {
        dataWatcher.addObject(WATCH_STATUS, Integer.valueOf(MobileRobotState.Status.QUARANTINED.ordinal()));
        dataWatcher.addObject(WATCH_MANIFEST, Integer.valueOf(0));
    }

    @Override public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            interpolateClientPose();
            return;
        }
        motionX = motionY = motionZ = 0.0;
        if (state.isLegacyInert()) { stopDrive(); syncVisualState(); return; }
        if (loadGuardTicks > 0) {
            loadGuardTicks--;
            if (loadGuardTicks == 0) state.resumeAfterLoad();
            state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
            syncVisualState();
            return;
        }
        updateRuntime();
        integrateDrive();
        state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
        syncVisualState();
    }

    public void prepareForChunkUnload() {
        if (!worldObj.isRemote) {
            state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
            state.suspendForUnload();
            runtimeHost.cancel();
            stopDrive();
            syncVisualState();
        }
    }

    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        if (preservedInvalidState != null) {
            tag.setTag("CraftonicaRobot", NbtMigrations.copy(preservedInvalidState)); return;
        }
        state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
        tag.setTag("CraftonicaRobot", state.write());
    }

    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        state = tag.hasKey("CraftonicaRobot")
                ? MobileRobotState.read(tag.getCompoundTag("CraftonicaRobot"))
                : MobileRobotState.quarantined();
        preservedInvalidState = state.getStatus() == MobileRobotState.Status.QUARANTINED
                && tag.hasKey("CraftonicaRobot")
                ? NbtMigrations.copy(tag.getCompoundTag("CraftonicaRobot")) : null;
        if (state.getStatus() == MobileRobotState.Status.QUARANTINED) {
            try { state.setPose(posX, posY, posZ, rotationYaw, rotationPitch); }
            catch (IllegalArgumentException ignored) { setPositionAndRotation(0.0, 0.0, 0.0, 0.0F, 0.0F); }
        } else {
            setPositionAndRotation(state.getX(), state.getY(), state.getZ(), state.getYaw(), state.getPitch());
        }
        motionX = motionY = motionZ = 0.0;
        loadGuardTicks = 1;
        syncVisualState();
    }

    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch,
                                                   int increments) {
        if (!worldObj.isRemote) {
            setPositionAndRotation(x, y, z, yaw, pitch);
            return;
        }
        targetX = x; targetY = y; targetZ = z; targetYaw = yaw; targetPitch = pitch;
        interpolationTicks = Math.max(1, Math.min(5, increments));
    }

    private void interpolateClientPose() {
        if (interpolationTicks <= 0) return;
        double divisor = interpolationTicks;
        double x = posX + (targetX - posX) / divisor;
        double y = posY + (targetY - posY) / divisor;
        double z = posZ + (targetZ - posZ) / divisor;
        float yawDelta = wrapAngle(targetYaw - rotationYaw);
        rotationYaw += yawDelta / divisor;
        rotationPitch += (targetPitch - rotationPitch) / divisor;
        setPosition(x, y, z);
        interpolationTicks--;
    }

    private void syncVisualState() {
        if (dataWatcher == null || state == null) return;
        dataWatcher.updateObject(WATCH_STATUS, Integer.valueOf(state.getStatus().ordinal()));
        byte[] hash = state.getManifestFingerprint();
        int visualHash = hash.length < 4 ? 0 : (hash[0] & 0xff) << 24 | (hash[1] & 0xff) << 16
                | (hash[2] & 0xff) << 8 | hash[3] & 0xff;
        dataWatcher.updateObject(WATCH_MANIFEST, Integer.valueOf(visualHash));
    }

    public int getVisualStatus() { return dataWatcher.getWatchableObjectInt(WATCH_STATUS); }
    public int getVisualManifestHash() { return dataWatcher.getWatchableObjectInt(WATCH_MANIFEST); }
    public MobileRobotState getRobotState() { return state; }
    public NBTTagCompound getPreservedInvalidState() {
        return preservedInvalidState == null ? null : NbtMigrations.copy(preservedInvalidState);
    }

    public void commandTestDrive(String command) {
        if (worldObj.isRemote || state.isLegacyInert()) return;
        if (state.getBoardState().hasFirmware()) return;
        if ("forward".equals(command)) setDrive(true, false, true, false, 255);
        else if ("reverse".equals(command)) setDrive(false, true, false, true, 255);
        else if ("left".equals(command)) setDrive(false, true, true, false, 220);
        else if ("right".equals(command)) setDrive(true, false, false, true, 220);
        else stopDrive();
    }

    public void installBoardCopy(RoboBoardState board) {
        if (worldObj.isRemote || board == null) return;
        runtimeHost.cancel(); stopDrive(); state.replaceBoardState(board);
    }

    public void startBoard() {
        if (!state.isLegacyInert()) state.getBoardState().start(state.getBoardState().getRevision());
    }
    public void stopBoard() { runtimeHost.cancel(); state.getBoardState().stop(state.getBoardState().getRevision()); stopDrive(); }

    public boolean removeTemporaryTestChassis() {
        if (worldObj.isRemote || !state.isTemporaryTestChassisRemovable()) return false;
        runtimeHost.cancel(); pendingInputs = null; stopDrive(); setDead();
        return true;
    }

    boolean isParkedForDisassembly() {
        return state.getStatus() == MobileRobotState.Status.STOPPED && !state.getBoardState().isRunning()
                && StrictMath.abs(leftWheelSpeed) < 1.0e-6
                && StrictMath.abs(rightWheelSpeed) < 1.0e-6
                && StrictMath.abs(motionX) < 1.0e-6 && StrictMath.abs(motionZ) < 1.0e-6;
    }

    void prepareForDisassembly() { runtimeHost.cancel(); pendingInputs = null; stopDrive(); }

    private void updateRuntime() {
        final RoboBoardState board = state.getBoardState();
        if (!board.isRunning()) {
            if (board.hasFirmware()) stopDrive();
            return;
        }
        if (runtimeHost.needsInput()) {
            pendingInputs = MobileRobotIoBridge.sample(this, state.consumeMeasurementCounter());
        }
        if (pendingInputs == null) return;
        RuntimeProtocol.Identity identity = RuntimeProtocol.Identity.mobile(worldObj.provider.dimensionId,
                state.getRobotId(), board.getGeneration());
        runtimeHost.tick(identity, worldObj.getTotalWorldTime(), board, pendingInputs,
                new RoboBoardRuntimeHost.OutputListener() {
                    @Override public void committed(RoboBoardState committed, RuntimeProtocol.Result result) {
                        MobileRobotIoBridge.DriveCommand command = MobileRobotIoBridge.drive(committed);
                        leftDrive = command.left; rightDrive = command.right;
                        state.commitSimulationFrame();
                        pendingInputs = null;
                    }
                });
    }

    private void setDrive(boolean l1, boolean l2, boolean r1, boolean r2, int pwm) {
        leftDrive = HBridgeModel.evaluate(true, true, l1, l2, pwm);
        rightDrive = HBridgeModel.evaluate(true, true, r1, r2, pwm);
    }

    private void stopDrive() {
        leftDrive = HBridgeModel.evaluate(false, true, false, false, 0);
        rightDrive = HBridgeModel.evaluate(false, true, false, false, 0);
        leftWheelSpeed = rightWheelSpeed = 0.0;
    }

    private void integrateDrive() {
        integrateDriveSubstep(0.025);
        integrateDriveSubstep(0.025);
    }

    private void integrateDriveSubstep(double dt) {
        DifferentialDriveModel.Step step = DifferentialDriveModel.step(leftWheelSpeed, rightWheelSpeed,
                leftDrive, rightDrive, rotationYaw, dt);
        leftWheelSpeed = step.leftSpeed; rightWheelSpeed = step.rightSpeed;
        if (StrictMath.abs(step.deltaX) < 1.0e-12 && StrictMath.abs(step.deltaZ) < 1.0e-12) return;
        double nextX = posX + step.deltaX, nextZ = posZ + step.deltaZ;
        int minChunkX = MathHelper.floor_double(nextX - width * 0.5) >> 4;
        int maxChunkX = MathHelper.floor_double(nextX + width * 0.5) >> 4;
        int minChunkZ = MathHelper.floor_double(nextZ - width * 0.5) >> 4;
        int maxChunkZ = MathHelper.floor_double(nextZ + width * 0.5) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            if (!worldObj.getChunkProvider().chunkExists(cx, cz)) { stopDrive(); return; }
        }
        if (worldObj.getCollidingBoundingBoxes(this, boundingBox.offset(0.0, -0.11, 0.0)).isEmpty()) {
            stopDrive(); return;
        }
        moveEntity(step.deltaX, 0.0, step.deltaZ);
        rotationYaw = wrapAngle(rotationYaw + (float) step.deltaYawDegrees);
        if (isCollidedHorizontally) stopDrive();
    }

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote) {
            if (player.getCurrentEquippedItem() != null
                    && player.getCurrentEquippedItem().getItem() == ModItems.WRENCH) {
                RobotAssemblyService.disassemble(this, player);
            } else player.addChatMessage(new ChatComponentTranslation(
                    "message.craftonica.robot.state", state.getRobotId().toString(), state.getStatus().name()));
        }
        return true;
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }
}
