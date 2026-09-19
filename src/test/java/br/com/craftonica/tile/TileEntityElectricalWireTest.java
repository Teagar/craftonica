package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TileEntityElectricalWireTest {
    @BeforeClass
    public static void registerTestTile() {
        TileEntity.addMapping(TileEntityElectricalWire.class, "craftonica_wire_test");
    }

    @Test
    public void blockedFacesToggleAndSurviveRoundTrip() {
        TileEntityElectricalWire wire = new TileEntityElectricalWire();
        assertTrue(wire.canConnect(4));
        assertTrue(wire.toggleFace(4));
        assertFalse(wire.canConnect(4));
        assertFalse(wire.toggleFace(4));
        assertTrue(wire.canConnect(4));
        wire.toggleFace(2);
        wire.toggleFace(5);

        NBTTagCompound saved = new NBTTagCompound();
        wire.writeToNBT(saved);
        TileEntityElectricalWire restored = new TileEntityElectricalWire();
        restored.readFromNBT(saved);

        assertEquals((1 << 2) | (1 << 5), restored.getBlockedFaces());
        assertFalse(restored.canConnect(2));
        assertFalse(restored.canConnect(5));
        assertTrue(restored.canConnect(3));
    }

    @Test
    public void invalidOrLegacyDataFailsOpen() {
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("WireSchema", 99);
        invalid.setByte("BlockedFaces", (byte) 63);
        TileEntityElectricalWire wire = new TileEntityElectricalWire();
        wire.readFromNBT(invalid);
        assertEquals(0, wire.getBlockedFaces());
    }
}
