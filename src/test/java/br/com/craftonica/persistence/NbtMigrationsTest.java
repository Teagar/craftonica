package br.com.craftonica.persistence;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public final class NbtMigrationsTest {
    @Test
    public void migratesCrl31BoardThroughEveryReleasedShapeWithoutMutatingInput() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("Schema", 1);
        legacy.setByteArray("LastTx", new byte[] {65, 66, 67});
        String before = legacy.toString();

        NbtMigrations.Result result = NbtMigrations.migrateRoboBoard(legacy);
        NBTTagCompound migrated = result.getValue();

        assertTrue(result.isSupported());
        assertTrue(result.isChanged());
        assertEquals(before, legacy.toString());
        assertEquals(3, migrated.getInteger("Schema"));
        assertEquals(0, migrated.getInteger("StableInputMask"));
        assertEquals(0, migrated.getInteger("IndeterminateInputMask"));
        assertArrayEquals(new byte[] {65, 66, 67}, migrated.getByteArray("SerialHistory"));
        assertEquals(0L, migrated.getLong("SerialStart"));
        assertEquals(3L, migrated.getLong("SerialEnd"));
        assertFalse(migrated.getBoolean("SerialTruncated"));
        assertFalse(migrated.getBoolean("HasSketchSource"));
    }

    @Test
    public void preservesCrl32InputsAndCrl35SerialAndSource() {
        NBTTagCompound crl32 = new NBTTagCompound();
        crl32.setInteger("Schema", 1);
        crl32.setInteger("StableInputMask", 3);
        crl32.setInteger("IndeterminateInputMask", 4);
        crl32.setByteArray("LastTx", new byte[] {9});
        NBTTagCompound migrated32 = NbtMigrations.migrateRoboBoard(crl32).getValue();
        assertEquals(3, migrated32.getInteger("StableInputMask"));
        assertEquals(4, migrated32.getInteger("IndeterminateInputMask"));
        assertArrayEquals(new byte[] {9}, migrated32.getByteArray("SerialHistory"));

        NBTTagCompound crl35 = new NBTTagCompound();
        crl35.setInteger("Schema", 1);
        crl35.setByteArray("SerialHistory", new byte[] {7, 8});
        crl35.setLong("SerialStart", 5);
        crl35.setLong("SerialEnd", 7);
        crl35.setBoolean("SerialTruncated", true);
        crl35.setByteArray("SketchSource", new byte[] {1, 2});
        crl35.setBoolean("HasSketchSource", true);
        NBTTagCompound migrated35 = NbtMigrations.migrateRoboBoard(crl35).getValue();
        assertArrayEquals(new byte[] {7, 8}, migrated35.getByteArray("SerialHistory"));
        assertEquals(5L, migrated35.getLong("SerialStart"));
        assertEquals(7L, migrated35.getLong("SerialEnd"));
        assertTrue(migrated35.getBoolean("SerialTruncated"));
        assertArrayEquals(new byte[] {1, 2}, migrated35.getByteArray("SketchSource"));
        assertTrue(migrated35.getBoolean("HasSketchSource"));
    }

    @Test
    public void currentMigrationIsIdempotentAndFutureSchemaIsOpaque() {
        NBTTagCompound current = new NBTTagCompound();
        current.setInteger("Schema", 3);
        current.setString("FutureSafe", "value");
        NbtMigrations.Result once = NbtMigrations.migrateRoboBoard(current);
        NbtMigrations.Result twice = NbtMigrations.migrateRoboBoard(once.getValue());
        assertFalse(once.isChanged());
        assertFalse(twice.isChanged());
        assertEquals(once.getValue().toString(), twice.getValue().toString());

        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("Schema", 99);
        future.setByteArray("Opaque", new byte[] {4, 5});
        NbtMigrations.Result unsupported = NbtMigrations.migrateRoboBoard(future);
        assertFalse(unsupported.isSupported());
        assertEquals(future.toString(), unsupported.getValue().toString());
    }
}
