package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import br.com.craftonica.persistence.NbtMigrations;

/** Keeps the lever state explicit in world saves; metadata remains the sync/render state. */
public final class TileEntityElectricalLever extends TileEntity {
    private boolean closed;
    private boolean migrationPending;
    private boolean closedMissing;
    private NBTTagCompound preservedFutureState;
    public boolean isClosed() { return closed; }
    public void setClosed(boolean value) { closed = value; markDirty(); }
    @Override public void writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); if (preservedFutureState != null) { NbtMigrations.copyInto(preservedFutureState, tag); return; } tag.setInteger("CraftonicaDataVersion", NbtMigrations.SIMPLE_DATA_VERSION); tag.setBoolean("Closed", closed); }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); preservedFutureState = tag.hasKey("CraftonicaDataVersion") && tag.getInteger("CraftonicaDataVersion") != NbtMigrations.SIMPLE_DATA_VERSION ? NbtMigrations.copy(tag) : null; migrationPending = !tag.hasKey("CraftonicaDataVersion"); closedMissing = !tag.hasKey("Closed"); closed = tag.getBoolean("Closed"); }
    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || !migrationPending) return;
        int metadata = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        if (closedMissing) closed = (metadata & 2) != 0;
        int canonical = closed ? metadata | 2 : metadata & ~2;
        if (canonical != metadata) worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, canonical, 2);
        migrationPending = false;
        closedMissing = false;
        markDirty();
    }
}
