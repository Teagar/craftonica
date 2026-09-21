package br.com.craftonica.robot.modular;

import br.com.craftonica.robot.modular.manifest.ModularManifestNbtCodec;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;

/** Persistent identity, manifest and safety status of a modular robot. */
public final class ModularRobotState {
    public static final int SCHEMA_VERSION = 1;
    /** ACTIVE is appended so ordinals written by the pre-physics development format remain safe. */
    public enum Status { INERT, RECOVERY_REQUIRED, QUARANTINED, ACTIVE }

    private final UUID robotId;
    private final UUID ownerId;
    private final GridVector anchor;
    private final ComponentOrientation anchorOrientation;
    private final ModularRobotManifest manifest;
    private final Status status;
    private final String diagnostic;

    public ModularRobotState(UUID robotId, UUID ownerId, GridVector anchor,
            ComponentOrientation anchorOrientation, ModularRobotManifest manifest) {
        this(robotId, ownerId, anchor, anchorOrientation, manifest, Status.ACTIVE, "");
    }

    private ModularRobotState(UUID robotId, UUID ownerId, GridVector anchor,
            ComponentOrientation anchorOrientation, ModularRobotManifest manifest, Status status,
            String diagnostic) {
        if (robotId == null || ownerId == null || anchor == null || anchorOrientation == null
                || manifest == null || status == null) throw new IllegalArgumentException("modular robot state");
        if (diagnostic == null || diagnostic.getBytes(StandardCharsets.UTF_8).length > 96
                || diagnostic.indexOf('\0') >= 0) throw new IllegalArgumentException("diagnostic");
        this.robotId = robotId; this.ownerId = ownerId; this.anchor = anchor;
        this.anchorOrientation = anchorOrientation; this.manifest = manifest; this.status = status;
        this.diagnostic = diagnostic;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Schema", SCHEMA_VERSION);
        uuid(tag, "Robot", robotId); uuid(tag, "Owner", ownerId);
        tag.setInteger("AnchorX", anchor.x); tag.setInteger("AnchorY", anchor.y); tag.setInteger("AnchorZ", anchor.z);
        tag.setByte("AnchorForward", (byte) anchorOrientation.getForward().ordinal());
        tag.setByte("AnchorUp", (byte) anchorOrientation.getUp().ordinal());
        tag.setByte("Status", (byte) status.ordinal()); tag.setString("Diagnostic", diagnostic);
        tag.setTag("Manifest", ModularManifestNbtCodec.write(manifest));
        return tag;
    }

    public static ModularRobotState read(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("Schema") != SCHEMA_VERSION) throw new IllegalArgumentException("state schema");
        int forward = tag.getByte("AnchorForward"), up = tag.getByte("AnchorUp"), status = tag.getByte("Status");
        if (forward < 0 || forward >= Direction.values().length || up < 0 || up >= Direction.values().length
                || status < 0 || status >= Status.values().length) throw new IllegalArgumentException("state enum");
        return new ModularRobotState(uuid(tag, "Robot"), uuid(tag, "Owner"),
                new GridVector(tag.getInteger("AnchorX"), tag.getInteger("AnchorY"), tag.getInteger("AnchorZ")),
                new ComponentOrientation(Direction.values()[forward], Direction.values()[up]),
                ModularManifestNbtCodec.read(tag.getCompoundTag("Manifest")), Status.values()[status],
                tag.getString("Diagnostic"));
    }

    public static ModularRobotState quarantined(String diagnostic) {
        ModularBlockSnapshot chassis = new ModularBlockSnapshot(StandardComponentCatalog.CHASSIS, 1,
                new GridVector(0, 0, 0), ComponentOrientation.NORTH_UP,
                StandardComponentCatalog.CHASSIS, 0, null);
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(0L, 0L),
                Collections.singletonList(chassis), Collections.<AssemblyEdge>emptyList());
        return new ModularRobotState(new UUID(0L, 0L), new UUID(0L, 0L), new GridVector(0, 0, 0),
                ComponentOrientation.NORTH_UP, manifest, Status.QUARANTINED,
                diagnostic == null ? "INVALID_PERSISTED_STATE" : diagnostic);
    }

    private static void uuid(NBTTagCompound tag, String prefix, UUID value) {
        tag.setLong(prefix + "Most", value.getMostSignificantBits()); tag.setLong(prefix + "Least", value.getLeastSignificantBits());
    }
    private static UUID uuid(NBTTagCompound tag, String prefix) {
        if (!tag.hasKey(prefix + "Most") || !tag.hasKey(prefix + "Least")) throw new IllegalArgumentException("uuid");
        return new UUID(tag.getLong(prefix + "Most"), tag.getLong(prefix + "Least"));
    }

    public UUID getRobotId() { return robotId; }
    public UUID getOwnerId() { return ownerId; }
    public GridVector getAnchor() { return anchor; }
    public ComponentOrientation getAnchorOrientation() { return anchorOrientation; }
    public ModularRobotManifest getManifest() { return manifest; }
    public Status getStatus() { return status; }
    public String getDiagnostic() { return diagnostic; }
    public boolean canSimulate() { return status == Status.ACTIVE; }
    public boolean canDisassemble() { return status != Status.QUARANTINED && status != Status.RECOVERY_REQUIRED; }
}
