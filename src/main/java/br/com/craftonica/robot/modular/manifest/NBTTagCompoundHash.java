package br.com.craftonica.robot.modular.manifest;

import net.minecraft.nbt.NBTTagCompound;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded stable representation used only for integrity; full NBT remains stored separately. */
final class NBTTagCompoundHash {
    private NBTTagCompoundHash() { }

    static void writeCanonical(DataOutputStream out, NBTTagCompound value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        byte[] text = value.toString().getBytes(StandardCharsets.UTF_8);
        if (text.length > 262144) throw new IllegalArgumentException("tile NBT too large");
        out.writeInt(text.length); out.write(text);
    }
}
