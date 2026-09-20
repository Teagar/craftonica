package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public final class TileEntityCalibrationTarget extends TileEntity {
    public static final int DATA_VERSION = 1;
    private int distanceCentimeters = 5;
    private int angleDegrees;

    public int getDistanceCentimeters() { return distanceCentimeters; }
    public int getAngleDegrees() { return angleDegrees; }

    public void cycleDistance() {
        distanceCentimeters = distanceCentimeters >= 50 ? 5 : distanceCentimeters + 5;
        changed();
    }

    public void cycleAngle() {
        angleDegrees = angleDegrees >= 60 ? 0 : angleDegrees + 15;
        changed();
    }

    private void changed() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); writeState(tag);
    }

    void writeState(NBTTagCompound tag) {
        tag.setInteger("DataVersion", DATA_VERSION);
        tag.setByte("DistanceCm", (byte) distanceCentimeters); tag.setByte("Angle", (byte) angleDegrees);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); readState(tag);
    }

    void readState(NBTTagCompound tag) {
        int distance = tag.getByte("DistanceCm") & 255, angle = tag.getByte("Angle") & 255;
        distanceCentimeters = tag.getInteger("DataVersion") == DATA_VERSION && distance >= 5 && distance <= 50
                && distance % 5 == 0 ? distance : 5;
        angleDegrees = tag.getInteger("DataVersion") == DATA_VERSION && angle >= 0 && angle <= 60
                && angle % 15 == 0 ? angle : 0;
    }
}
