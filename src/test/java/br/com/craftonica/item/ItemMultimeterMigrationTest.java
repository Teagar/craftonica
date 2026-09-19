package br.com.craftonica.item;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public final class ItemMultimeterMigrationTest {
    @Test
    public void legacyModeSurvivesAndProbeWithoutSideIsCleared() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("CraftonicaMode", "current");
        tag.setBoolean("CraftonicaProbeSet", true);
        tag.setInteger("CraftonicaProbeX", 12);

        ItemMultimeter.migrateTag(tag);

        assertEquals("current", tag.getString("CraftonicaMode"));
        assertEquals(1, tag.getInteger("CraftonicaDataVersion"));
        assertFalse(tag.hasKey("CraftonicaProbeSet"));
        assertFalse(tag.hasKey("CraftonicaProbeX"));
    }

    @Test
    public void completeProbeAndCurrentVersionAreIdempotent() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("CraftonicaMode", "resistance");
        tag.setBoolean("CraftonicaProbeSet", true);
        tag.setInteger("CraftonicaProbeSide", 5);
        ItemMultimeter.migrateTag(tag);
        String once = tag.toString();
        ItemMultimeter.migrateTag(tag);
        assertEquals(once, tag.toString());
        assertTrue(tag.getBoolean("CraftonicaProbeSet"));
        assertEquals(5, tag.getInteger("CraftonicaProbeSide"));
    }
}
