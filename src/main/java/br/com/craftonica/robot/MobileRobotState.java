package br.com.craftonica.robot;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.RoboBoardStateNbtCodec;
import br.com.craftonica.firmware.SourceBundle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded persistent state for the mobile chassis. World adaptation belongs to the entity. */
public final class MobileRobotState {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MODULES = 32;
    public static final int MAX_DIAGNOSTIC_BYTES = 64;
    private static final double WORLD_LIMIT = 30000000.0;

    public enum Status { STOPPED, RUNNING, SUSPENDED, FAULT, QUARANTINED }

    private final UUID robotId;
    private final UUID ownerId;
    private final List<RobotModuleSnapshot> modules;
    private final byte[] manifestFingerprint;
    private RoboBoardState boardState;
    private byte[] boardTemplateSketch = new byte[0];
    private long generation;
    private long simulationFrame;
    private Status status;
    private String diagnostic;
    private boolean resumeRequested;
    private long measurementCounter;
    private double x, y, z;
    private float yaw, pitch;

    public MobileRobotState(UUID robotId, UUID ownerId, List<RobotModuleSnapshot> modules) {
        this(robotId, ownerId, modules, 0L, 0L, Status.STOPPED, "", false,
                0.0, 0.0, 0.0, 0.0F, 0.0F, null, new RoboBoardState(robotId));
    }

    private MobileRobotState(UUID robotId, UUID ownerId, List<RobotModuleSnapshot> modules,
                             long generation, long simulationFrame, Status status, String diagnostic,
                             boolean resumeRequested, double x, double y, double z, float yaw, float pitch,
                             byte[] expectedFingerprint, RoboBoardState boardState) {
        if (robotId == null || ownerId == null || generation < 0 || simulationFrame < 0 || status == null)
            throw new IllegalArgumentException("invalid robot identity");
        this.robotId = robotId;
        this.ownerId = ownerId;
        this.modules = canonicalModules(modules);
        this.manifestFingerprint = fingerprint(this.modules);
        if (expectedFingerprint != null && !MessageDigest.isEqual(manifestFingerprint, expectedFingerprint))
            throw new IllegalArgumentException("manifest fingerprint");
        this.generation = generation;
        if (boardState == null) throw new IllegalArgumentException("board state");
        this.boardState = boardState;
        this.simulationFrame = simulationFrame;
        this.status = status;
        setDiagnostic(diagnostic);
        this.resumeRequested = resumeRequested;
        setPose(x, y, z, yaw, pitch);
    }

    public static MobileRobotState minimal(UUID robotId, UUID ownerId) {
        return new MobileRobotState(robotId, ownerId, Collections.singletonList(
                new RobotModuleSnapshot(0, RobotModuleSnapshot.Type.CHASSIS_CORE, 0, 0, 0, 0)));
    }

    public static MobileRobotState assembled(UUID robotId, UUID ownerId, int facing, RoboBoardState boardState) {
        return assembled(robotId, ownerId, facing, boardState, new byte[0]);
    }

    public static MobileRobotState assembled(UUID robotId, UUID ownerId, int facing, RoboBoardState boardState,
                                              byte[] templateSketch) {
        MobileRobotState state = new MobileRobotState(robotId, ownerId,
                RobotAssemblyLayout.manifest(facing));
        state.replaceBoardState(boardState);
        state.setBoardTemplateSketch(templateSketch);
        return state;
    }

    public static MobileRobotState quarantined() {
        MobileRobotState state = minimal(new UUID(0L, 0L), new UUID(0L, 0L));
        state.status = Status.QUARANTINED;
        state.diagnostic = "INVALID_PERSISTED_STATE";
        return state;
    }

    public void setPose(double x, double y, double z, float yaw, float pitch) {
        if (!finite(x) || !finite(y) || !finite(z) || !finite(yaw) || !finite(pitch)
                || StrictMath.abs(x) > WORLD_LIMIT || StrictMath.abs(z) > WORLD_LIMIT
                || y < -4096.0 || y > 4096.0 || pitch < -90.0F || pitch > 90.0F)
            throw new IllegalArgumentException("invalid robot pose");
        this.x = x; this.y = y; this.z = z;
        this.yaw = normalizeYaw(yaw); this.pitch = pitch;
    }

    public void suspendForUnload() {
        if (status == Status.SUSPENDED) return;
        resumeRequested = status == Status.RUNNING || boardState.isRunning();
        if (boardState.isRunning()) boardState.unloadAndIncrementGeneration();
        if (status != Status.QUARANTINED && status != Status.FAULT) status = Status.SUSPENDED;
        if (generation == Long.MAX_VALUE) {
            status = Status.QUARANTINED;
            diagnostic = "GENERATION_EXHAUSTED";
            return;
        }
        generation++;
    }

    public void resumeAfterLoad() {
        if (status == Status.SUSPENDED) status = Status.STOPPED;
        if (resumeRequested && boardState.hasFirmware() && !boardState.isRunning()) {
            boardState.start(boardState.getRevision());
            resumeRequested = false;
        }
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Schema", SCHEMA_VERSION);
        putUuid(tag, "Robot", robotId); putUuid(tag, "Owner", ownerId);
        tag.setLong("Generation", generation); tag.setLong("SimulationFrame", simulationFrame);
        tag.setByte("Status", (byte) status.ordinal()); tag.setString("Diagnostic", diagnostic);
        tag.setBoolean("ResumeRequested", resumeRequested);
        tag.setLong("MeasurementCounter", measurementCounter);
        tag.setDouble("X", x); tag.setDouble("Y", y); tag.setDouble("Z", z);
        tag.setFloat("Yaw", yaw); tag.setFloat("Pitch", pitch);
        NBTTagList list = new NBTTagList();
        for (RobotModuleSnapshot module : modules) list.appendTag(module.write());
        tag.setTag("Modules", list); tag.setByteArray("ManifestFingerprint", manifestFingerprint);
        NBTTagCompound board = new NBTTagCompound(); RoboBoardStateNbtCodec.write(boardState, board);
        tag.setTag("BoardState", board);
        tag.setByteArray("BoardTemplateSketch", boardTemplateSketch);
        return tag;
    }

    public static MobileRobotState read(NBTTagCompound tag) {
        try {
            if (tag == null || tag.getInteger("Schema") != SCHEMA_VERSION) return quarantined();
            NBTTagList list = tag.getTagList("Modules", 10);
            if (list.tagCount() < 1 || list.tagCount() > MAX_MODULES) return quarantined();
            List<RobotModuleSnapshot> modules = new ArrayList<RobotModuleSnapshot>(list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) modules.add(RobotModuleSnapshot.read(list.getCompoundTagAt(i)));
            int statusOrdinal = tag.getByte("Status");
            if (statusOrdinal < 0 || statusOrdinal >= Status.values().length) return quarantined();
            long generation = tag.getLong("Generation");
            Status restoredStatus = Status.values()[statusOrdinal];
            boolean resumeRequested = tag.getBoolean("ResumeRequested");
            if (restoredStatus == Status.RUNNING) {
                if (generation == Long.MAX_VALUE) return quarantined();
                generation++;
                restoredStatus = Status.SUSPENDED;
                resumeRequested = true;
            }
            UUID robotId = getUuid(tag, "Robot");
            RoboBoardState boardState = tag.hasKey("BoardState")
                    ? RoboBoardStateNbtCodec.read(tag.getCompoundTag("BoardState")) : new RoboBoardState(robotId);
            MobileRobotState restored = new MobileRobotState(robotId, getUuid(tag, "Owner"), modules,
                    generation, tag.getLong("SimulationFrame"), restoredStatus,
                    tag.getString("Diagnostic"), resumeRequested,
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"), tag.getByteArray("ManifestFingerprint"), boardState);
            long measurementCounter = tag.getLong("MeasurementCounter");
            if (measurementCounter < 0) return quarantined();
            restored.measurementCounter = measurementCounter;
            restored.setBoardTemplateSketch(tag.hasKey("BoardTemplateSketch")
                    ? tag.getByteArray("BoardTemplateSketch") : new byte[0]);
            if (restored.boardState.isRunning()) {
                restored.boardState.unloadAndIncrementGeneration();
                restored.resumeRequested = true;
                restored.status = Status.SUSPENDED;
            }
            return restored;
        } catch (RuntimeException invalid) {
            return quarantined();
        }
    }

    private static List<RobotModuleSnapshot> canonicalModules(List<RobotModuleSnapshot> source) {
        if (source == null || source.isEmpty() || source.size() > MAX_MODULES)
            throw new IllegalArgumentException("module count");
        List<RobotModuleSnapshot> copy = new ArrayList<RobotModuleSnapshot>(source);
        Collections.sort(copy, new Comparator<RobotModuleSnapshot>() {
            @Override public int compare(RobotModuleSnapshot a, RobotModuleSnapshot b) { return a.id - b.id; }
        });
        Set<Integer> ids = new HashSet<Integer>();
        int chassis = 0;
        for (RobotModuleSnapshot module : copy) {
            if (module == null || !ids.add(module.id)) throw new IllegalArgumentException("duplicate module");
            if (module.type == RobotModuleSnapshot.Type.CHASSIS_CORE) chassis++;
        }
        if (chassis != 1) throw new IllegalArgumentException("chassis count");
        return Collections.unmodifiableList(copy);
    }

    private static byte[] fingerprint(List<RobotModuleSnapshot> modules) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SCHEMA_VERSION); out.writeInt(modules.size());
            for (RobotModuleSnapshot module : modules) {
                out.writeByte(module.id); out.writeByte(module.type.ordinal()); out.writeByte(module.x);
                out.writeByte(module.y); out.writeByte(module.z); out.writeByte(module.facing);
            }
            out.close();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void setDiagnostic(String value) {
        String clean = value == null ? "" : value;
        if (clean.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DIAGNOSTIC_BYTES
                || clean.indexOf('\0') >= 0) throw new IllegalArgumentException("diagnostic");
        diagnostic = clean;
    }

    private static void putUuid(NBTTagCompound tag, String prefix, UUID value) {
        tag.setLong(prefix + "Most", value.getMostSignificantBits());
        tag.setLong(prefix + "Least", value.getLeastSignificantBits());
    }

    private static UUID getUuid(NBTTagCompound tag, String prefix) {
        if (!tag.hasKey(prefix + "Most") || !tag.hasKey(prefix + "Least"))
            throw new IllegalArgumentException("missing uuid");
        return new UUID(tag.getLong(prefix + "Most"), tag.getLong(prefix + "Least"));
    }

    private static float normalizeYaw(float value) {
        float result = value % 360.0F;
        return result < -180.0F ? result + 360.0F : result >= 180.0F ? result - 360.0F : result;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    public UUID getRobotId() { return robotId; }
    public UUID getOwnerId() { return ownerId; }
    public List<RobotModuleSnapshot> getModules() { return modules; }
    public byte[] getManifestFingerprint() { return manifestFingerprint.clone(); }
    public long getGeneration() { return generation; }
    public long getSimulationFrame() { return simulationFrame; }
    public Status getStatus() { return status; }
    public String getDiagnostic() { return diagnostic; }
    public boolean isResumeRequested() { return resumeRequested; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public RoboBoardState getBoardState() { return boardState; }
    public byte[] getBoardTemplateSketch() { return boardTemplateSketch.clone(); }
    private void setBoardTemplateSketch(byte[] value) {
        if (value == null || value.length > SourceBundle.MAX_FILE_BYTES)
            throw new IllegalArgumentException("board template sketch");
        boardTemplateSketch = value.clone();
    }
    public boolean hasPhysicalAssemblyManifest() {
        List<RobotModuleSnapshot> expected = RobotAssemblyLayout.manifest(2);
        if (modules.size() != expected.size()) return false;
        for (int i = 0; i < modules.size(); i++) {
            RobotModuleSnapshot actual = modules.get(i), template = expected.get(i);
            if (actual.id != template.id || actual.type != template.type || actual.x != template.x
                    || actual.y != template.y || actual.z != template.z) return false;
        }
        return true;
    }
    public void replaceBoardState(RoboBoardState value) {
        if (value == null) throw new IllegalArgumentException("board state");
        boardState = value;
    }
    public long consumeMeasurementCounter() {
        if (measurementCounter == Long.MAX_VALUE) throw new IllegalStateException("measurement counter exhausted");
        return measurementCounter++;
    }
    public void commitSimulationFrame() {
        if (simulationFrame == Long.MAX_VALUE) throw new IllegalStateException("simulation frame exhausted");
        simulationFrame++;
    }
}
