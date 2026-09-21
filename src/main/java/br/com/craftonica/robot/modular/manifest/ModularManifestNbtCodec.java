package br.com.craftonica.robot.modular.manifest;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModularManifestNbtCodec {
    private ModularManifestNbtCodec() { }

    public static NBTTagCompound write(ModularRobotManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("manifest");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Schema", ModularRobotManifest.SCHEMA_VERSION);
        tag.setLong("ManifestMost", manifest.getManifestId().getMostSignificantBits());
        tag.setLong("ManifestLeast", manifest.getManifestId().getLeastSignificantBits());
        NBTTagList modules = new NBTTagList();
        for (ModularBlockSnapshot module : manifest.getModules()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Type", module.componentTypeId); value.setInteger("TypeSchema", module.componentSchema);
            position(value, module.localPosition); value.setByte("Forward", (byte) module.localOrientation.getForward().ordinal());
            value.setByte("Up", (byte) module.localOrientation.getUp().ordinal());
            value.setString("Block", module.blockRegistryName); value.setByte("Metadata", (byte) module.metadata);
            NBTTagCompound tile = module.getTileData(); if (tile != null) value.setTag("Tile", tile);
            modules.appendTag(value);
        }
        tag.setTag("Modules", modules);
        NBTTagList edges = new NBTTagList();
        for (AssemblyEdge edge : manifest.getEdges()) {
            NBTTagCompound value = new NBTTagCompound(); value.setByte("Kind", (byte) edge.kind.ordinal());
            value.setInteger("AX", edge.firstPosition.x); value.setInteger("AY", edge.firstPosition.y); value.setInteger("AZ", edge.firstPosition.z);
            value.setString("APort", edge.firstPort); value.setInteger("BX", edge.secondPosition.x);
            value.setInteger("BY", edge.secondPosition.y); value.setInteger("BZ", edge.secondPosition.z); value.setString("BPort", edge.secondPort);
            edges.appendTag(value);
        }
        tag.setTag("Edges", edges); tag.setByteArray("Fingerprint", manifest.getFingerprint());
        return tag;
    }

    public static ModularRobotManifest read(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("Schema") != ModularRobotManifest.SCHEMA_VERSION)
            throw new IllegalArgumentException("manifest schema");
        NBTTagList moduleTags = tag.getTagList("Modules", 10);
        if (moduleTags.tagCount() < 1 || moduleTags.tagCount() > ModularRobotManifest.MAX_MODULES)
            throw new IllegalArgumentException("module count");
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        for (int i = 0; i < moduleTags.tagCount(); i++) {
            NBTTagCompound value = moduleTags.getCompoundTagAt(i);
            modules.add(new ModularBlockSnapshot(value.getString("Type"), value.getInteger("TypeSchema"),
                    readPosition(value), orientation(value), value.getString("Block"), value.getByte("Metadata") & 15,
                    value.hasKey("Tile") ? value.getCompoundTag("Tile") : null));
        }
        NBTTagList edgeTags = tag.getTagList("Edges", 10);
        if (edgeTags.tagCount() > ModularRobotManifest.MAX_EDGES) throw new IllegalArgumentException("edge count");
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        for (int i = 0; i < edgeTags.tagCount(); i++) {
            NBTTagCompound value = edgeTags.getCompoundTagAt(i); int kind = value.getByte("Kind");
            if (kind < 0 || kind >= AssemblyEdge.Kind.values().length) throw new IllegalArgumentException("edge kind");
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.values()[kind],
                    new GridVector(value.getInteger("AX"), value.getInteger("AY"), value.getInteger("AZ")), value.getString("APort"),
                    new GridVector(value.getInteger("BX"), value.getInteger("BY"), value.getInteger("BZ")), value.getString("BPort")));
        }
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(tag.getLong("ManifestMost"), tag.getLong("ManifestLeast")), modules, edges);
        if (!manifest.hasFingerprint(tag.getByteArray("Fingerprint"))) throw new IllegalArgumentException("manifest fingerprint");
        return manifest;
    }

    private static void position(NBTTagCompound tag, GridVector value) { tag.setInteger("X", value.x); tag.setInteger("Y", value.y); tag.setInteger("Z", value.z); }
    private static GridVector readPosition(NBTTagCompound tag) { return new GridVector(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")); }
    private static ComponentOrientation orientation(NBTTagCompound tag) {
        int forward = tag.getByte("Forward"), up = tag.getByte("Up");
        if (forward < 0 || forward >= Direction.values().length || up < 0 || up >= Direction.values().length)
            throw new IllegalArgumentException("orientation");
        return new ComponentOrientation(Direction.values()[forward], Direction.values()[up]);
    }
}
