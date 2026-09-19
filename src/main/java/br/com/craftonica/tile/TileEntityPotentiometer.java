package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import br.com.craftonica.electrical.nodal.PotentiometerModel;
import br.com.craftonica.persistence.NbtMigrations;

/** Server-authoritative, versioned cursor state. A step is one percent of the track. */
public final class TileEntityPotentiometer extends TileEntity {
    public static final int STATE_VERSION = 1;
    private int cursorStep = 50;
    private int stateVersion = STATE_VERSION;
    private boolean migrationPending;
    private NBTTagCompound preservedFutureState;

    public int getCursorStep() { return cursorStep; }
    public int getStateVersion() { return stateVersion; }
    public double getResistanceAOhms() { return PotentiometerModel.resistanceA(cursorStep); }
    public double getResistanceBOhms() { return PotentiometerModel.resistanceB(cursorStep); }

    public boolean adjust(int delta) {
        int next = PotentiometerModel.clampStep(cursorStep + delta);
        if (next == cursorStep) return false;
        cursorStep = next;
        stateVersion++;
        markDirty();
        return true;
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (preservedFutureState != null) { NbtMigrations.copyInto(preservedFutureState, tag); return; }
        tag.setInteger("CraftonicaDataVersion", NbtMigrations.SIMPLE_DATA_VERSION);
        tag.setInteger("StateVersion", stateVersion);
        tag.setInteger("CursorStep", cursorStep);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        preservedFutureState = tag.hasKey("CraftonicaDataVersion")
                && tag.getInteger("CraftonicaDataVersion") != NbtMigrations.SIMPLE_DATA_VERSION
                ? NbtMigrations.copy(tag) : null;
        migrationPending = !tag.hasKey("CraftonicaDataVersion");
        stateVersion = Math.max(STATE_VERSION, tag.getInteger("StateVersion"));
        cursorStep = tag.hasKey("CursorStep")
                ? PotentiometerModel.clampStep(tag.getInteger("CursorStep")) : 50;
    }

    @Override public void updateEntity() { if (worldObj != null && !worldObj.isRemote && migrationPending) { migrationPending = false; markDirty(); } }

    @Override public S35PacketUpdateTileEntity getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
