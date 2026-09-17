package br.com.craftonica.tile;

import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.LedState;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;

public final class TileEntityLed extends TileEntity {
    private final LedState state = new LedState();

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            return;
        }
        BlockPosition position = new BlockPosition(xCoord, yCoord, zCoord);
        CircuitResult result = ElectricalNetworkManager.forWorld(worldObj).getLocalResult(position);
        boolean wasBurned = state.isBurned();
        if (result != null && state.update(result)) {
            markDirty();
            refreshLightAndRender();
        }
        if (!wasBurned && state.isBurned()) {
            ElectricalNetworkManager.forWorld(worldObj).invalidateAround(position);
        }
    }

    public boolean isBurned() {
        return state.isBurned();
    }

    public int getBrightness() {
        return state.getBrightness();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Burned", state.isBurned());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        state.setBurned(tag.getBoolean("Burned"));
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Burned", state.isBurned());
        tag.setByte("Brightness", (byte) state.getBrightness());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        state.setClientState(tag.getBoolean("Burned"), tag.getByte("Brightness"));
        refreshLightAndRender();
    }

    private void refreshLightAndRender() {
        worldObj.updateLightByType(EnumSkyBlock.Block, xCoord, yCoord, zCoord);
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
