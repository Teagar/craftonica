package br.com.craftonica.tile;

import br.com.craftonica.block.BlockEducationalActuator;
import br.com.craftonica.electrical.nodal.BranchResult;
import br.com.craftonica.electrical.nodal.ValueValidity;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Read-only visualization of solved load activity; it never changes electrical topology. */
public final class TileEntityEducationalActuator extends TileEntity {
    public static final double NOMINAL_VOLTS = 5.0;
    private int activityLevel;

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        Block block = worldObj.getBlock(xCoord, yCoord, zCoord);
        int next = 0;
        if (block instanceof BlockEducationalActuator) {
            BranchResult branch = ElectricalNetworkManager.forWorld(worldObj)
                    .getBranchResult(new BlockPosition(xCoord, yCoord, zCoord));
            if (branch != null && branch.getValidity() == ValueValidity.VALID) {
                next = activityLevel(branch.getVoltage(), branch.getAbsorbedPower(),
                        ((BlockEducationalActuator) block).getResistanceOhms());
            }
        }
        if (next != activityLevel) {
            activityLevel = next;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public int getActivityLevel() { return activityLevel; }
    public boolean isActive() { return activityLevel > 0; }

    public static int activityLevel(double voltage, double absorbedPower, double resistanceOhms) {
        if (!isFinite(voltage) || !isFinite(absorbedPower)
                || !isFinite(resistanceOhms) || resistanceOhms <= 0.0) return 0;
        double voltageMagnitude = Math.abs(voltage);
        double powerEquivalentVolts = Math.sqrt(Math.abs(absorbedPower) * resistanceOhms);
        double effectiveVolts = Math.max(voltageMagnitude, powerEquivalentVolts);
        return clampLevel((int) Math.floor(effectiveVolts * 15.0 / NOMINAL_VOLTS + 0.5));
    }

    @Override public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Level", (byte) activityLevel);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        activityLevel = clampLevel(packet.func_148857_g().getByte("Level"));
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int clampLevel(int value) { return Math.max(0, Math.min(15, value)); }
}
