package br.com.craftonica.persistence;

import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.tile.TileEntityElectricalLever;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityPotentiometer;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.nbt.NBTTagCompound;
import cpw.mods.fml.common.registry.GameRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public final class PersistentTileMigrationTest {
    @BeforeClass
    public static void registerTestTiles() {
        GameRegistry.registerTileEntity(TileEntityLed.class, "craftonica_test_led");
        GameRegistry.registerTileEntity(TileEntityCircuitBreaker.class, "craftonica_test_breaker");
        GameRegistry.registerTileEntity(TileEntityElectricalLever.class, "craftonica_test_lever");
        GameRegistry.registerTileEntity(TileEntityPotentiometer.class, "craftonica_test_potentiometer");
        GameRegistry.registerTileEntity(TileEntityRoboBoard.class, "craftonica_test_board");
    }

    @Test
    public void simpleLegacyTileStatesSurviveAndGainExplicitVersion() {
        NBTTagCompound ledLegacy = new NBTTagCompound(); ledLegacy.setBoolean("Burned", true);
        TileEntityLed led = new TileEntityLed(); led.readFromNBT(ledLegacy);
        NBTTagCompound ledCurrent = new NBTTagCompound(); led.writeToNBT(ledCurrent);
        assertTrue(ledCurrent.getBoolean("Burned"));
        assertEquals(1, ledCurrent.getInteger("CraftonicaDataVersion"));

        NBTTagCompound breakerLegacy = new NBTTagCompound();
        breakerLegacy.setBoolean("Tripped", false); breakerLegacy.setBoolean("PendingTrip", true);
        TileEntityCircuitBreaker breaker = new TileEntityCircuitBreaker(); breaker.readFromNBT(breakerLegacy);
        NBTTagCompound breakerCurrent = new NBTTagCompound(); breaker.writeToNBT(breakerCurrent);
        assertFalse(breakerCurrent.getBoolean("Tripped"));
        assertTrue(breakerCurrent.getBoolean("PendingTrip"));

        NBTTagCompound leverLegacy = new NBTTagCompound(); leverLegacy.setBoolean("Closed", true);
        TileEntityElectricalLever lever = new TileEntityElectricalLever(); lever.readFromNBT(leverLegacy);
        NBTTagCompound leverCurrent = new NBTTagCompound(); lever.writeToNBT(leverCurrent);
        assertTrue(leverCurrent.getBoolean("Closed"));
        assertEquals(1, leverCurrent.getInteger("CraftonicaDataVersion"));

        NBTTagCompound potentiometerLegacy = new NBTTagCompound();
        potentiometerLegacy.setInteger("StateVersion", 8); potentiometerLegacy.setInteger("CursorStep", 61);
        TileEntityPotentiometer potentiometer = new TileEntityPotentiometer();
        potentiometer.readFromNBT(potentiometerLegacy);
        NBTTagCompound potentiometerCurrent = new NBTTagCompound(); potentiometer.writeToNBT(potentiometerCurrent);
        assertEquals(8, potentiometerCurrent.getInteger("StateVersion"));
        assertEquals(61, potentiometerCurrent.getInteger("CursorStep"));
        assertEquals(1, potentiometerCurrent.getInteger("CraftonicaDataVersion"));
    }

    @Test
    public void ledColorSurvivesRoundTripAndLegacyDefaultsToRed() {
        TileEntityLed colored = new TileEntityLed();
        colored.setColor(12);
        NBTTagCompound saved = new NBTTagCompound();
        colored.writeToNBT(saved);
        TileEntityLed restored = new TileEntityLed();
        restored.readFromNBT(saved);
        assertEquals(12, restored.getColor());

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setBoolean("Burned", false);
        TileEntityLed legacyLed = new TileEntityLed();
        legacyLed.readFromNBT(legacy);
        assertEquals(1, legacyLed.getColor());
    }

    @Test
    public void roboBoardCanonicalizesReleasedSchemaAndPreservesUnsupportedData() {
        TileEntityRoboBoard original = new TileEntityRoboBoard();
        NBTTagCompound legacy = new NBTTagCompound(); original.writeToNBT(legacy);
        legacy.setInteger("Schema", 1);
        legacy.removeTag("StableInputMask"); legacy.removeTag("IndeterminateInputMask");
        legacy.removeTag("SerialHistory"); legacy.removeTag("SerialStart"); legacy.removeTag("SerialEnd");
        legacy.removeTag("SerialTruncated"); legacy.removeTag("SketchSource"); legacy.removeTag("HasSketchSource");
        legacy.setByteArray("LastTx", new byte[0]);

        TileEntityRoboBoard migrated = new TileEntityRoboBoard(); migrated.readFromNBT(legacy);
        NBTTagCompound canonical = new NBTTagCompound(); migrated.writeToNBT(canonical);
        assertEquals(3, canonical.getInteger("Schema"));
        assertTrue(canonical.hasKey("SerialHistory"));
        assertTrue(canonical.hasKey("SketchSource"));

        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("Schema", 99); future.setString("OpaqueFutureData", "keep");
        TileEntityRoboBoard unsupported = new TileEntityRoboBoard(); unsupported.readFromNBT(future);
        NBTTagCompound rewritten = new NBTTagCompound(); unsupported.writeToNBT(rewritten);
        assertEquals(99, rewritten.getInteger("Schema"));
        assertEquals("keep", rewritten.getString("OpaqueFutureData"));
    }

    @Test
    public void roboBoardOwnerSurvivesRoundTrip() {
        UUID owner = UUID.randomUUID();
        TileEntityRoboBoard original = new TileEntityRoboBoard();
        assertTrue(original.claimOwner(owner));
        assertFalse(original.claimOwner(UUID.randomUUID()));

        NBTTagCompound saved = new NBTTagCompound();
        original.writeToNBT(saved);
        TileEntityRoboBoard restored = new TileEntityRoboBoard();
        restored.readFromNBT(saved);

        assertEquals(1, saved.getInteger("AccessSchema"));
        assertEquals(owner, restored.getOwnerId());
    }

    @Test
    public void roboBoardShowcaseTemplateSurvivesRoundTripWithoutFirmware() {
        TileEntityRoboBoard original = new TileEntityRoboBoard();
        NBTTagCompound saved = new NBTTagCompound();
        original.writeToNBT(saved);
        saved.setByteArray("TemplateSketch", "void setup(){}\nvoid loop(){}\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TileEntityRoboBoard restored = new TileEntityRoboBoard();
        restored.readFromNBT(saved);
        assertEquals("void setup(){}\nvoid loop(){}\n",
                new String(restored.getEditorSketchSource(), java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(restored.hasInstalledSketchSource());
    }

    @Test
    public void simpleFutureTileStateIsPreservedOpaque() {
        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("CraftonicaDataVersion", 7);
        future.setBoolean("Burned", true);
        future.setString("FutureLedState", "keep");
        TileEntityLed led = new TileEntityLed();
        led.readFromNBT(future);
        NBTTagCompound rewritten = new NBTTagCompound();
        led.writeToNBT(rewritten);
        assertEquals(7, rewritten.getInteger("CraftonicaDataVersion"));
        assertEquals("keep", rewritten.getString("FutureLedState"));
    }
}
