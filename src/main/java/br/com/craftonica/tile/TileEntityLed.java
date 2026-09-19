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
import br.com.craftonica.persistence.NbtMigrations;

public final class TileEntityLed extends TileEntity {
    private final LedState state = new LedState();
    private int color = 1;
    private boolean migrationPending;
    private NBTTagCompound preservedFutureState;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            return;
        }
        if (migrationPending) { migrationPending = false; markDirty(); }
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

    public int getColor() { return color; }

    public void setColor(int color) {
        this.color = color & 15;
        markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (preservedFutureState != null) { NbtMigrations.copyInto(preservedFutureState, tag); return; }
        tag.setInteger("CraftonicaDataVersion", NbtMigrations.SIMPLE_DATA_VERSION);
        tag.setBoolean("Burned", state.isBurned());
        tag.setByte("LedColor", (byte) color);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        preservedFutureState = tag.hasKey("CraftonicaDataVersion")
                && tag.getInteger("CraftonicaDataVersion") != NbtMigrations.SIMPLE_DATA_VERSION
                ? NbtMigrations.copy(tag) : null;
        migrationPending = !tag.hasKey("CraftonicaDataVersion");
        state.setBurned(tag.getBoolean("Burned"));
        color = tag.hasKey("LedColor") ? tag.getByte("LedColor") & 15 : 1;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Burned", state.isBurned());
        tag.setByte("Brightness", (byte) state.getBrightness());
        tag.setByte("LedColor", (byte) color);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        state.setClientState(tag.getBoolean("Burned"), tag.getByte("Brightness"));
        color = tag.getByte("LedColor") & 15;
        refreshLightAndRender();
    }

    private void refreshLightAndRender() {
        worldObj.updateLightByType(EnumSkyBlock.Block, xCoord, yCoord, zCoord);
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
