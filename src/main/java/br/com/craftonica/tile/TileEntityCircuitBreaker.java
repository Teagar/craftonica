package br.com.craftonica.tile;

import br.com.craftonica.electrical.nodal.ProtectionState;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Server-authoritative breaker state with delayed trip and compact client sync. */
public final class TileEntityCircuitBreaker extends TileEntity {
    private final ProtectionState protection = new ProtectionState();

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (protection.applyPendingTrip()) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            ElectricalNetworkManager.forWorld(worldObj).invalidateAround(new BlockPosition(xCoord, yCoord, zCoord));
        }
    }

    public boolean isTripped() { return protection.isTripped(); }
    public boolean isPendingTrip() { return protection.isPendingTrip(); }
    public void observe(double current, double power) { protection.observe(current, power); }
    public boolean rearm() {
        boolean changed = protection.reset();
        if (changed && worldObj != null) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            ElectricalNetworkManager.forWorld(worldObj).invalidateAround(new BlockPosition(xCoord, yCoord, zCoord));
        }
        return changed;
    }
    public void setTripped(boolean value) { protection.setTripped(value); }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Tripped", protection.isTripped());
        tag.setBoolean("PendingTrip", protection.isPendingTrip());
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        protection.setTripped(tag.getBoolean("Tripped"));
        protection.setPendingTrip(tag.getBoolean("PendingTrip"));
    }
    @Override public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Tripped", protection.isTripped());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }
    @Override public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        setTripped(packet.func_148857_g().getBoolean("Tripped"));
    }
}
