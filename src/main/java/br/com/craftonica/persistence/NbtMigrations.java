package br.com.craftonica.persistence;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Set;

/** Pure migrations for released world formats. Input compounds are never mutated. */
public final class NbtMigrations {
    public static final int SIMPLE_DATA_VERSION = 1;
    public static final int ROBO_BOARD_SCHEMA_VERSION = 3;

    private NbtMigrations() {}

    public static Result migrateRoboBoard(NBTTagCompound source) {
        if (source == null) return Result.unsupported(null);
        NBTTagCompound migrated = copy(source);
        int declared = migrated.hasKey("Schema") ? migrated.getInteger("Schema") : -1;
        if (declared != 1 && declared != 2 && declared != ROBO_BOARD_SCHEMA_VERSION)
            return Result.unsupported(source);

        int effective = declared;
        if (declared == 1) {
            if (migrated.hasKey("SerialHistory") || migrated.hasKey("SketchSource")
                    || migrated.hasKey("HasSketchSource")) effective = 3;
            else if (migrated.hasKey("StableInputMask") || migrated.hasKey("IndeterminateInputMask")) effective = 2;
        }
        if (effective < 2) {
            migrated.setInteger("StableInputMask", 0);
            migrated.setInteger("IndeterminateInputMask", 0);
            effective = 2;
        }
        if (effective < 3) {
            byte[] serial = migrated.getByteArray("LastTx");
            migrated.setByteArray("SerialHistory", serial);
            migrated.setLong("SerialStart", 0L);
            migrated.setLong("SerialEnd", serial.length);
            migrated.setBoolean("SerialTruncated", false);
            migrated.setByteArray("SketchSource", new byte[0]);
            migrated.setBoolean("HasSketchSource", false);
        }
        migrated.setInteger("Schema", ROBO_BOARD_SCHEMA_VERSION);
        return Result.supported(migrated, declared != ROBO_BOARD_SCHEMA_VERSION);
    }

    public static NBTTagCompound copy(NBTTagCompound source) {
        return source == null ? new NBTTagCompound() : (NBTTagCompound) source.copy();
    }

    @SuppressWarnings("unchecked")
    public static void copyInto(NBTTagCompound source, NBTTagCompound destination) {
        Set<String> keys = source.func_150296_c();
        for (String key : keys) {
            NBTBase value = source.getTag(key);
            destination.setTag(key, value.copy());
        }
    }

    public static final class Result {
        private final boolean supported;
        private final boolean changed;
        private final NBTTagCompound value;

        private Result(boolean supported, boolean changed, NBTTagCompound value) {
            this.supported = supported;
            this.changed = changed;
            this.value = value;
        }

        private static Result supported(NBTTagCompound value, boolean changed) {
            return new Result(true, changed, value);
        }

        private static Result unsupported(NBTTagCompound source) {
            return new Result(false, false, copy(source));
        }

        public boolean isSupported() { return supported; }
        public boolean isChanged() { return changed; }
        public NBTTagCompound getValue() { return copy(value); }
    }
}
