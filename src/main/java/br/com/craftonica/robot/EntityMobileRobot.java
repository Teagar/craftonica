package br.com.craftonica.robot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.UUID;

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
        if (loadGuardTicks > 0) {
            loadGuardTicks--;
            if (loadGuardTicks == 0) state.resumeAfterLoad();
        }
        state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
        syncVisualState();
    }

    public void prepareForChunkUnload() {
        if (!worldObj.isRemote) {
            state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
            state.suspendForUnload();
            syncVisualState();
        }
    }

    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        state.setPose(posX, posY, posZ, rotationYaw, rotationPitch);
        tag.setTag("CraftonicaRobot", state.write());
    }

    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        state = tag.hasKey("CraftonicaRobot")
                ? MobileRobotState.read(tag.getCompoundTag("CraftonicaRobot"))
                : MobileRobotState.quarantined();
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

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote) player.addChatMessage(new ChatComponentTranslation(
                "message.craftonica.robot.state", state.getRobotId().toString(), state.getStatus().name()));
        return true;
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }
}
