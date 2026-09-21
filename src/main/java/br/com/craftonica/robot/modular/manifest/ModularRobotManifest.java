package br.com.craftonica.robot.modular.manifest;

import br.com.craftonica.robot.modular.assembly.AssemblyEdge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Canonical immutable modular_robot:1 manifest. */
public final class ModularRobotManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MODULES = 256;
    public static final int MAX_EDGES = 1024;

    private final UUID manifestId;
    private final List<ModularBlockSnapshot> modules;
    private final List<AssemblyEdge> edges;
    private final byte[] fingerprint;

    public ModularRobotManifest(UUID manifestId, List<ModularBlockSnapshot> modules,
                                List<AssemblyEdge> edges) {
        if (manifestId == null || modules == null || modules.isEmpty() || modules.size() > MAX_MODULES
                || edges == null || edges.size() > MAX_EDGES) throw new IllegalArgumentException("manifest");
        this.manifestId = manifestId;
        this.modules = canonicalModules(modules);
        this.edges = canonicalEdges(edges);
        fingerprint = fingerprint(this.manifestId, this.modules, this.edges);
    }

    public UUID getManifestId() { return manifestId; }
    public List<ModularBlockSnapshot> getModules() { return modules; }
    public List<AssemblyEdge> getEdges() { return edges; }
    public byte[] getFingerprint() { return fingerprint.clone(); }

    public boolean hasFingerprint(byte[] expected) {
        return expected != null && MessageDigest.isEqual(fingerprint, expected);
    }

    private static List<ModularBlockSnapshot> canonicalModules(List<ModularBlockSnapshot> source) {
        List<ModularBlockSnapshot> values = new ArrayList<ModularBlockSnapshot>(source);
        Collections.sort(values, new Comparator<ModularBlockSnapshot>() {
            @Override public int compare(ModularBlockSnapshot a, ModularBlockSnapshot b) {
                if (a.localPosition.x != b.localPosition.x) return a.localPosition.x < b.localPosition.x ? -1 : 1;
                if (a.localPosition.y != b.localPosition.y) return a.localPosition.y < b.localPosition.y ? -1 : 1;
                if (a.localPosition.z != b.localPosition.z) return a.localPosition.z < b.localPosition.z ? -1 : 1;
                return a.componentTypeId.compareTo(b.componentTypeId);
            }
        });
        Set<String> positions = new HashSet<String>();
        for (ModularBlockSnapshot value : values)
            if (value == null || !positions.add(value.localPosition.toString()))
                throw new IllegalArgumentException("duplicate module position");
        return Collections.unmodifiableList(values);
    }

    private static List<AssemblyEdge> canonicalEdges(List<AssemblyEdge> source) {
        List<AssemblyEdge> values = new ArrayList<AssemblyEdge>(source);
        Collections.sort(values, new Comparator<AssemblyEdge>() {
            @Override public int compare(AssemblyEdge a, AssemblyEdge b) {
                return key(a).compareTo(key(b));
            }
        });
        Set<String> keys = new HashSet<String>();
        for (AssemblyEdge value : values) if (value == null || !keys.add(key(value)))
            throw new IllegalArgumentException("duplicate edge");
        return Collections.unmodifiableList(values);
    }

    static String key(AssemblyEdge edge) {
        String a = edge.firstPosition + "/" + edge.firstPort;
        String b = edge.secondPosition + "/" + edge.secondPort;
        return edge.kind.name() + ":" + (a.compareTo(b) <= 0 ? a + "=" + b : b + "=" + a);
    }

    private static byte[] fingerprint(UUID id, List<ModularBlockSnapshot> modules, List<AssemblyEdge> edges) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SCHEMA_VERSION); out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
            out.writeInt(modules.size());
            for (ModularBlockSnapshot module : modules) {
                text(out, module.componentTypeId); out.writeInt(module.componentSchema);
                out.writeInt(module.localPosition.x); out.writeInt(module.localPosition.y); out.writeInt(module.localPosition.z);
                out.writeByte(module.localOrientation.getForward().ordinal());
                out.writeByte(module.localOrientation.getUp().ordinal());
                text(out, module.blockRegistryName); out.writeByte(module.metadata);
                NBTTagCompoundHash.writeCanonical(out, module.getTileData());
            }
            out.writeInt(edges.size());
            for (AssemblyEdge edge : edges) text(out, key(edge));
            out.close();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length); out.write(encoded);
    }
}
