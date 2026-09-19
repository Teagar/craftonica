package br.com.craftonica.tile;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TileEntityRoboPortTest {
    @BeforeClass
    public static void registerTestTile() {
        TileEntity.addMapping(TileEntityRoboPort.class, "craftonica_robo_port_test");
    }

    @Test
    public void validRoleAndRevisionRoundTripWithoutElectricalClientState() {
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
    public void analogNamesPreserveLegacyAvrOrdinals() {
        assertEquals(14, TileEntityRoboPort.Role.A0.ordinal());
        assertEquals(19, TileEntityRoboPort.Role.A5.ordinal());
        assertEquals(14, TileEntityRoboPort.Role.A0.getPin());
        assertEquals(19, TileEntityRoboPort.Role.A5.getPin());

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("PortSchema", TileEntityRoboPort.SCHEMA_VERSION);
        legacy.setByte("Role", (byte) 14);
        TileEntityRoboPort port = new TileEntityRoboPort();
        port.readFromNBT(legacy);
        assertEquals(TileEntityRoboPort.Role.A0, port.getRole());
    }

    @Test
    public void remoteBindingDataAndAllUnoRolesSurviveNbt() {
        assertEquals(22, TileEntityRoboPort.Role.values().length);
        UUID boardId = UUID.randomUUID();
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setInteger("PortSchema", TileEntityRoboPort.SCHEMA_VERSION);
        encoded.setByte("Role", (byte) TileEntityRoboPort.Role.A5.ordinal());
        encoded.setInteger("BoardX", 10);
        encoded.setInteger("BoardY", 64);
        encoded.setInteger("BoardZ", -20);
        encoded.setLong("BoardMost", boardId.getMostSignificantBits());
        encoded.setLong("BoardLeast", boardId.getLeastSignificantBits());
        encoded.setByte("Outward", (byte) 5);

        TileEntityRoboPort port = new TileEntityRoboPort();
        port.readFromNBT(encoded);
        NBTTagCompound roundTrip = new NBTTagCompound();
        port.writeToNBT(roundTrip);

        assertEquals(boardId, port.getOwnerBoardId());
        assertEquals(10, port.getOwnerBoardX());
        assertEquals(64, port.getOwnerBoardY());
        assertEquals(-20, port.getOwnerBoardZ());
        assertEquals(5, roundTrip.getByte("Outward"));
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
