package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Keeps the lever state explicit in world saves; metadata remains the sync/render state. */
public final class TileEntityElectricalLever extends TileEntity {
    private boolean closed;
    public boolean isClosed() { return closed; }
    public void setClosed(boolean value) { closed = value; markDirty(); }
    @Override public void writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setBoolean("Closed", closed); }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); closed = tag.getBoolean("Closed"); }
}
