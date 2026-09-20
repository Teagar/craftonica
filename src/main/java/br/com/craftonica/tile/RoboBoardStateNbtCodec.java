package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** Shared bounded NBT adapter for fixed and mobile RoboBoard hosts. */
public final class RoboBoardStateNbtCodec {
    private RoboBoardStateNbtCodec() { }

    public static void write(RoboBoardState state, NBTTagCompound tag) {
        if (state == null || tag == null) throw new IllegalArgumentException("board state");
        RoboBoardState.Persisted value = state.snapshot();
        tag.setInteger("Schema", value.schema);
        tag.setLong("BoardMost", value.boardId.getMostSignificantBits());
        tag.setLong("BoardLeast", value.boardId.getLeastSignificantBits());
        tag.setLong("Generation", value.generation); tag.setLong("Revision", value.revision);
        tag.setByteArray("Firmware", value.firmware); tag.setByteArray("FirmwareHash", value.firmwareHash);
        tag.setByteArray("Checkpoint", value.checkpoint); tag.setByteArray("CheckpointHash", value.checkpointHash);
        tag.setByte("Status", (byte) value.statusOrdinal); tag.setString("Fault", value.fault);
        tag.setBoolean("Running", value.running); tag.setBoolean("D13", value.d13High);
        tag.setInteger("OutputMask", value.outputMask); tag.setInteger("HighMask", value.highMask);
        tag.setInteger("PwmMask", value.pwmMask); tag.setIntArray("PwmMode", value.pwmMode);
        tag.setIntArray("PwmPrescaler", value.pwmPrescaler); tag.setIntArray("PwmCompare", value.pwmCompare);
        tag.setInteger("StableInputMask", value.stableInputMask);
        tag.setInteger("IndeterminateInputMask", value.indeterminateInputMask);
        tag.setByteArray("SerialHistory", value.serialHistory); tag.setLong("SerialStart", value.serialStartOffset);
        tag.setLong("SerialEnd", value.serialEndOffset); tag.setBoolean("SerialTruncated", value.serialTruncated);
        tag.setByteArray("SketchSource", value.installedSketchSource);
        tag.setBoolean("HasSketchSource", value.installedSketchSourcePresent);
    }

    public static RoboBoardState read(NBTTagCompound tag) {
        if (tag == null) return RoboBoardState.restore(invalid());
        UUID boardId = tag.hasKey("BoardMost") && tag.hasKey("BoardLeast")
                ? new UUID(tag.getLong("BoardMost"), tag.getLong("BoardLeast")) : null;
        byte[] serial = tag.getByteArray("SerialHistory");
        return RoboBoardState.restore(new RoboBoardState.Persisted(
                tag.hasKey("Schema") ? tag.getInteger("Schema") : -1, boardId,
                tag.getLong("Generation"), tag.getLong("Revision"), tag.getByteArray("Firmware"),
                tag.getByteArray("FirmwareHash"), tag.getByteArray("Checkpoint"),
                tag.getByteArray("CheckpointHash"), tag.hasKey("Status") ? tag.getByte("Status") : -1,
                tag.getString("Fault"), tag.getBoolean("Running"), tag.getBoolean("D13"),
                tag.getInteger("OutputMask"), tag.getInteger("HighMask"), tag.getInteger("PwmMask"),
                array(tag, "PwmMode"), array(tag, "PwmPrescaler"), array(tag, "PwmCompare"),
                tag.getInteger("StableInputMask"), tag.getInteger("IndeterminateInputMask"), serial,
                tag.getLong("SerialStart"), tag.hasKey("SerialEnd") ? tag.getLong("SerialEnd") : serial.length,
                tag.getBoolean("SerialTruncated"), tag.getByteArray("SketchSource"),
                tag.getBoolean("HasSketchSource")));
    }

    private static int[] array(NBTTagCompound tag, String key) {
        return tag.hasKey(key) ? tag.getIntArray(key) : new int[0];
    }

    private static RoboBoardState.Persisted invalid() {
        return new RoboBoardState.Persisted(-1, null, 0, 0, new byte[0], new byte[0], new byte[0],
                new byte[0], -1, "", false, false);
    }
}
