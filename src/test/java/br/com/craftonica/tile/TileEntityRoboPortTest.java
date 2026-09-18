package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TileEntityRoboPortTest {
    @Test
    public void validRoleAndRevisionRoundTripWithoutElectricalClientState() {
        TileEntity.addMapping(TileEntityRoboPort.class, "craftonica_robo_port_test");
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setInteger("PortSchema", TileEntityRoboPort.SCHEMA_VERSION);
        encoded.setByte("Role", (byte) TileEntityRoboPort.Role.POWER_5V.ordinal());
        encoded.setLong("Revision", 7L);

        TileEntityRoboPort port = new TileEntityRoboPort();
        port.readFromNBT(encoded);
        NBTTagCompound roundTrip = new NBTTagCompound();
        port.writeToNBT(roundTrip);

        assertEquals(TileEntityRoboPort.Role.POWER_5V, port.getRole());
        assertEquals(7L, port.getRevision());
        assertEquals(TileEntityRoboPort.Role.POWER_5V.ordinal(), roundTrip.getByte("Role"));
    }

    @Test
    public void invalidRoleFailsClosedToUnboundD0() {
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setInteger("PortSchema", TileEntityRoboPort.SCHEMA_VERSION);
        encoded.setByte("Role", (byte) 100);
        encoded.setLong("Revision", 1L);

        TileEntityRoboPort port = new TileEntityRoboPort();
        port.readFromNBT(encoded);

        assertEquals(TileEntityRoboPort.Role.D0, port.getRole());
        assertEquals(-1, port.getOutwardSide());
    }

    @Test
    public void driveModelUsesFiniteFiveVoltSourcesAndPwmAverage() {
        assertNull(TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D3, false, false, false, 0));
        assertEquals(5.0, TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D3, false, true, false, 0), 0.0);
        assertEquals(0.0, TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D3, true, false, false, 0), 0.0);
        assertEquals(5.0, TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D3, true, true, false, 0), 0.0);
        assertEquals(5.0 * 127.0 / 255.0, TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D3, true, false, true, 127), 1e-12);
        assertNull(TileEntityRoboPort.calculateDriveVoltage(
                TileEntityRoboPort.Role.D9, true, false, true, 256));
    }
}
