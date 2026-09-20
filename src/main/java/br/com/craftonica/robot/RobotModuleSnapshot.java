package br.com.craftonica.robot;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable, bounded module descriptor stored inside a mobile robot manifest. */
public final class RobotModuleSnapshot {
    public enum Type { CHASSIS_CORE, ROBO_BOARD, ULTRASONIC_SENSOR, H_BRIDGE, LEFT_MOTOR, RIGHT_MOTOR, POWER_5V, GROUND }

    public final int id;
    public final Type type;
    public final int x;
    public final int y;
    public final int z;
    public final int facing;

    public RobotModuleSnapshot(int id, Type type, int x, int y, int z, int facing) {
        if (id < 0 || id >= MobileRobotState.MAX_MODULES || type == null
                || x < -2 || x > 2 || y < 0 || y > 2 || z < -2 || z > 2
                || facing < 0 || facing > 3) throw new IllegalArgumentException("invalid robot module");
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Id", (byte) id);
        tag.setByte("Type", (byte) type.ordinal());
        tag.setByte("X", (byte) x);
        tag.setByte("Y", (byte) y);
        tag.setByte("Z", (byte) z);
        tag.setByte("Facing", (byte) facing);
        return tag;
    }

    static RobotModuleSnapshot read(NBTTagCompound tag) {
        int ordinal = tag.getByte("Type");
        if (ordinal < 0 || ordinal >= Type.values().length) throw new IllegalArgumentException("module type");
        return new RobotModuleSnapshot(tag.getByte("Id") & 0xff, Type.values()[ordinal],
                tag.getByte("X"), tag.getByte("Y"), tag.getByte("Z"), tag.getByte("Facing"));
    }
}
