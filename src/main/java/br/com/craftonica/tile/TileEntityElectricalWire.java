package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public final class TileEntityElectricalWire extends TileEntity {
    public static final int SCHEMA_VERSION = 1;
    private int blockedFaces;

    public boolean canConnect(int side) {
        return side >= 0 && side < 6 && (blockedFaces & 1 << side) == 0;
    }

    public boolean toggleFace(int side) {
        if (side < 0 || side >= 6) return false;
        blockedFaces ^= 1 << side;
        markDirty();
        return !canConnect(side);
    }

    public int getBlockedFaces() { return blockedFaces; }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("WireSchema", SCHEMA_VERSION);
        tag.setByte("BlockedFaces", (byte) blockedFaces);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        blockedFaces = tag.getInteger("WireSchema") == SCHEMA_VERSION
                ? tag.getByte("BlockedFaces") & 63 : 0;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("BlockedFaces", (byte) blockedFaces);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        blockedFaces = packet.func_148857_g().getByte("BlockedFaces") & 63;
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
