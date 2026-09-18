package br.com.craftonica.tile;

import br.com.craftonica.block.BlockAnalogSensor;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;

/** Server-authoritative sensor sample. Environmental response is deliberately ideal and linear. */
public final class TileEntityAnalogSensor extends TileEntity {
    public static final int DATA_VERSION = 1;
    public static final int MIN_MICROVOLTS = 0;
    public static final int MAX_MICROVOLTS = 5000000;
    public static final int OUTPUT_RESISTANCE_OHMS = 1000;
    public static final double MIN_TEMPERATURE = -0.5;
    public static final double MAX_TEMPERATURE = 2.0;

    private int lastMicrovolts;
    private int visualLevel;
    private long lastSampleTick = Long.MIN_VALUE;

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        long tick = worldObj.getTotalWorldTime();
        if (tick == lastSampleTick) return;
        lastSampleTick = tick;

        Block block = worldObj.getBlock(xCoord, yCoord, zCoord);
        if (!(block instanceof BlockAnalogSensor)) return;
        BlockAnalogSensor.Type type = ((BlockAnalogSensor) block).getType();
        int sample = type == BlockAnalogSensor.Type.LIGHT
                ? lightToMicrovolts(worldObj.getSavedLightValue(EnumSkyBlock.Block, xCoord, yCoord, zCoord))
                : temperatureToMicrovolts(worldObj.getBiomeGenForCoords(xCoord, zCoord)
                        .getFloatTemperature(xCoord, yCoord, zCoord));
        applyServerSample(sample);
    }

    public int getOutputMicrovolts() { return lastMicrovolts; }
    public int getLastMicrovolts() { return lastMicrovolts; }
    public int getVisualLevel() { return visualLevel; }

    public static int lightToMicrovolts(int blockLight) {
        int bounded = Math.max(0, Math.min(15, blockLight));
        return (int) ((bounded * (long) MAX_MICROVOLTS + 7L) / 15L);
    }

    public static int temperatureToMicrovolts(double temperature) {
        if (Double.isNaN(temperature)) temperature = MIN_TEMPERATURE;
        double bounded = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, temperature));
        return clampMicrovolts((long) Math.floor(
                (bounded - MIN_TEMPERATURE) * MAX_MICROVOLTS
                        / (MAX_TEMPERATURE - MIN_TEMPERATURE) + 0.5));
    }

    public static int coarseLevel(int microvolts) {
        return (int) (clampMicrovolts(microvolts) * 15L / MAX_MICROVOLTS);
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("DataVersion", DATA_VERSION);
        tag.setInteger("LastMicrovolts", clampMicrovolts(lastMicrovolts));
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        lastMicrovolts = tag.getInteger("DataVersion") == DATA_VERSION && tag.hasKey("LastMicrovolts")
                ? clampMicrovolts(tag.getInteger("LastMicrovolts")) : MIN_MICROVOLTS;
        visualLevel = coarseLevel(lastMicrovolts);
    }

    @Override public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Level", (byte) visualLevel);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        visualLevel = clampLevel(packet.func_148857_g().getByte("Level"));
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private void applyServerSample(int sample) {
        int bounded = clampMicrovolts(sample);
        if (bounded == lastMicrovolts) return;
        int previousLevel = visualLevel;
        lastMicrovolts = bounded;
        visualLevel = coarseLevel(bounded);
        markDirty();
        ElectricalNetworkManager.forWorld(worldObj).invalidateAround(new BlockPosition(xCoord, yCoord, zCoord));
        if (visualLevel != previousLevel) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private static int clampMicrovolts(long value) {
        return (int) Math.max(MIN_MICROVOLTS, Math.min((long) MAX_MICROVOLTS, value));
    }

    private static int clampLevel(int value) { return Math.max(0, Math.min(15, value)); }
}
