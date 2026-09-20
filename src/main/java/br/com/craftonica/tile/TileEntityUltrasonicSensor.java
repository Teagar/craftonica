package br.com.craftonica.tile;

import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import br.com.craftonica.sensor.UltrasonicRaycaster;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Server-authoritative HC-SR04 environment sample and bounded visual state. */
public final class TileEntityUltrasonicSensor extends TileEntity {
    public static final int DATA_VERSION = 1;
    private boolean registered;
    private int visualDistanceCm;
    private boolean visualEcho;

    @Override public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote && !registered) {
            registered = true;
            UltrasonicSensorRegistry.register(this);
        }
    }

    public UltrasonicMeasurementModel.Measurement sample() {
        if (worldObj == null || worldObj.isRemote) return UltrasonicMeasurementModel.Measurement.noEcho();
        int front = worldObj.getBlockMetadata(xCoord, yCoord, zCoord) & 7;
        UltrasonicRaycaster.Hit hit = UltrasonicRaycaster.trace(worldObj, xCoord, yCoord, zCoord, front);
        long seed = worldObj.getSeed() ^ (xCoord * 341873128712L) ^ (zCoord * 132897987541L)
                ^ (yCoord * 42317861L) ^ (worldObj.getTotalWorldTime() / 2L);
        UltrasonicMeasurementModel.Measurement measurement = hit == null
                ? UltrasonicMeasurementModel.Measurement.noEcho()
                : UltrasonicMeasurementModel.measure(hit.centimeters, hit.incidenceDegrees, hit.material,
                        hit.apparentCoverage, seed);
        int nextDistance = measurement.echo ? (int) Math.round(measurement.measuredCentimeters) : 0;
        if (visualEcho != measurement.echo || visualDistanceCm != nextDistance) {
            visualEcho = measurement.echo;
            visualDistanceCm = Math.max(0, Math.min(400, nextDistance));
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return measurement;
    }

    public boolean hasVisualEcho() { return visualEcho; }
    public int getVisualDistanceCm() { return visualDistanceCm; }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("DataVersion", DATA_VERSION);
        tag.setShort("LastDistanceCm", (short) Math.max(0, Math.min(400, visualDistanceCm)));
        tag.setBoolean("LastEcho", visualEcho);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.getInteger("DataVersion") == DATA_VERSION) {
            visualDistanceCm = Math.max(0, Math.min(400, tag.getShort("LastDistanceCm")));
            visualEcho = tag.getBoolean("LastEcho") && visualDistanceCm > 0;
        } else { visualDistanceCm = 0; visualEcho = false; }
        registered = false;
    }

    @Override public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setShort("Distance", (short) visualDistanceCm); tag.setBoolean("Echo", visualEcho);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        visualDistanceCm = Math.max(0, Math.min(400, tag.getShort("Distance")));
        visualEcho = tag.getBoolean("Echo") && visualDistanceCm > 0;
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override public void invalidate() { UltrasonicSensorRegistry.unregister(this); registered = false; super.invalidate(); }
    @Override public void onChunkUnload() { UltrasonicSensorRegistry.unregister(this); registered = false; super.onChunkUnload(); }
}
