package br.com.craftonica.tile;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.runtime.server.RuntimeServer;
import br.com.craftonica.runtime.server.RuntimeSupervisor;
import br.com.craftonica.runtime.server.RoboBoardIoBridge;
import br.com.craftonica.runtime.server.RuntimeResultValidator;
import br.com.craftonica.persistence.NbtMigrations;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/** Server-authoritative Forge shell for bounded RoboBoard state. */
public final class TileEntityRoboBoard extends TileEntity {
    private static final int VISUAL_RUNNING = 1;
    private static final int VISUAL_FAULT = 2;
    private static final int VISUAL_FIRMWARE = 4;
    private static final int VISUAL_D13 = 8;

    private RoboBoardState state = new RoboBoardState();
    private boolean unloaded;
    private boolean clientVisualReceived;
    private boolean clientRunning;
    private boolean clientFault;
    private boolean clientHasFirmware;
    private boolean clientD13High;
    private RuntimeSupervisor.Submission inFlight;
    private RequestMetadata requestMetadata;
    private long resumeAfterWorldTick = Long.MIN_VALUE;
    private boolean portsInitialized;
    private UUID ownerId;
    private byte[] templateSketchSource = new byte[0];
    private boolean migrationPending;
    private NBTTagCompound preservedInvalidState;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || unloaded) return;
        if (migrationPending) {
            migrationPending = false;
            markDirty();
        }
        if (!portsInitialized) {
            portsInitialized = true;
            RoboBoardIoBridge.invalidateAdjacentPorts(this);
        }
        if (!state.isRunning()) {
            cancelInFlight();
            return;
        }
        if (worldObj.getTotalWorldTime() <= resumeAfterWorldTick) return;
        if (inFlight != null) {
            RuntimeSupervisor.Completion completion = inFlight.poll();
            if (completion == null) return;
            RequestMetadata metadata = requestMetadata;
            inFlight = null;
            requestMetadata = null;
            if (!matchesCurrent(metadata)) return;
            if (completion.failure != null) {
                commitRuntimeFault(metadata, failureCode(completion.failure));
            } else {
                commitRuntimeResult(metadata, completion.result);
            }
            if (!state.isRunning()) return;
            if (worldObj.getTotalWorldTime() <= resumeAfterWorldTick) return;
        }

        RuntimeSupervisor supervisor = RuntimeServer.get();
        if (supervisor == null) {
            RuntimeServer.start();
            supervisor = RuntimeServer.get();
        }
        long generation = state.getGeneration();
        long revision = state.getRevision();
        byte[] checkpoint;
        long cycles;
        try {
            checkpoint = state.prepareRuntimeCheckpoint(generation, revision);
            cycles = AvrCheckpointCodec.decode(checkpoint).getCycles();
            markDirty();
        } catch (RuntimeException invalid) {
            commitRuntimeFault(new RequestMetadata(dimension(), xCoord, yCoord, zCoord, generation, revision,
                    state.getCheckpoint(), 0), "RUNTIME_CHECKPOINT");
            return;
        } catch (AvrFault invalid) {
            commitRuntimeFault(new RequestMetadata(dimension(), xCoord, yCoord, zCoord, generation, revision,
                    state.getCheckpoint(), 0), "RUNTIME_CHECKPOINT");
            return;
        }
        if (supervisor == null) {
            commitRuntimeFault(new RequestMetadata(dimension(), xCoord, yCoord, zCoord, generation, revision,
                    checkpoint, cycles), RuntimeServer.getUnavailableReason());
            return;
        }

        long batchTarget = nextAbsoluteTarget(cycles, worldObj.getTotalWorldTime());
        if (batchTarget <= cycles) return;
        long target = Math.min(cycles + 50000L, batchTarget);
        RuntimeProtocol.Identity identity = new RuntimeProtocol.Identity(dimension(), xCoord, yCoord, zCoord, generation);
        RequestMetadata metadata = new RequestMetadata(dimension(), xCoord, yCoord, zCoord, generation, revision,
                checkpoint, batchTarget);
        try {
            RoboBoardIoBridge.Snapshot io = RoboBoardIoBridge.sample(this, state.getStableInputMask());
            state.recordInputDiagnostics(io.stableMask, io.indeterminateMask);
            markDirty();
            inFlight = supervisor.submit(new RuntimeProtocol.Request(identity, target, state.getFirmware(), checkpoint,
                    io.inputs), batchTarget);
            requestMetadata = metadata;
        } catch (RejectedExecutionException rejected) {
            commitRuntimeFault(metadata, "RUNTIME_BUSY");
        }
    }

    public long installVerifiedFirmware(CRLFirmware firmware, long expectedRevision) {
        requireServer();
        cancelInFlight();
        int oldVisual = visualFlags();
        long revision = state.installVerifiedFirmware(firmware, expectedRevision);
        preservedInvalidState = null;
        invalidatePortsAndDefer();
        publishMutation(oldVisual);
        return revision;
    }

    public long installCompiledSketch(SourceBundle sources, CRLFirmware firmware, long expectedRevision) {
        requireServer();
        int oldVisual = visualFlags();
        long revision = state.installCompiledSketch(sources, firmware, expectedRevision);
        preservedInvalidState = null;
        cancelInFlight();
        invalidatePortsAndDefer();
        publishMutation(oldVisual);
        return revision;
    }

    public long startFirmware(long expectedRevision) {
        requireServer();
        int oldVisual = visualFlags();
        long revision = state.start(expectedRevision);
        invalidatePortsAndDefer();
        publishMutation(oldVisual);
        return revision;
    }

    public long stopFirmware(long expectedRevision) {
        requireServer();
        cancelInFlight();
        int oldVisual = visualFlags();
        long revision = state.stop(expectedRevision);
        invalidatePortsAndDefer();
        publishMutation(oldVisual);
        return revision;
    }

    public void commitRuntimeCheckpoint(long expectedGeneration, long expectedRevision, byte[] checkpoint,
                                        RoboBoardState.Status status, String fault, boolean running, boolean d13High) {
        requireServer();
        int oldVisual = visualFlags();
        state.commitRuntimeCheckpoint(expectedGeneration, expectedRevision, checkpoint, status, fault, running, d13High);
        invalidatePortsAndDefer();
        publishMutation(oldVisual);
    }

    public long unloadAndIncrementGeneration() {
        requireServer();
        if (unloaded) return state.getGeneration();
        cancelInFlight();
        unloaded = true;
        long generation = state.unloadAndIncrementGeneration();
        RoboBoardIoBridge.invalidateAdjacentPorts(this);
        markDirty();
        return generation;
    }

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) unloadAndIncrementGeneration();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote && !unloaded) unloadAndIncrementGeneration();
        super.invalidate();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (preservedInvalidState != null) {
            NbtMigrations.copyInto(preservedInvalidState, tag);
            return;
        }
        tag.setInteger("AccessSchema", 1);
        if (ownerId != null) {
            tag.setLong("OwnerMost", ownerId.getMostSignificantBits());
            tag.setLong("OwnerLeast", ownerId.getLeastSignificantBits());
        }
        if (templateSketchSource.length > 0) tag.setByteArray("TemplateSketch", templateSketchSource);
        RoboBoardStateNbtCodec.write(state, tag);
        tag.setByteArray("LastTx", state.getSerialHistorySnapshot().getBytes());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NbtMigrations.Result migration = NbtMigrations.migrateRoboBoard(tag);
        NBTTagCompound persisted = migration.getValue();
        ownerId = persisted.hasKey("OwnerMost") && persisted.hasKey("OwnerLeast")
                ? new UUID(persisted.getLong("OwnerMost"), persisted.getLong("OwnerLeast")) : null;
        byte[] template = persisted.hasKey("TemplateSketch") ? persisted.getByteArray("TemplateSketch") : new byte[0];
        templateSketchSource = template.length <= SourceBundle.MAX_FILE_BYTES ? template : new byte[0];
        boolean hasSerialHistory = persisted.hasKey("SerialHistory");
        byte[] serialHistory = hasSerialHistory ? persisted.getByteArray("SerialHistory") : persisted.getByteArray("LastTx");
        if (!hasSerialHistory) {
            persisted.setByteArray("SerialHistory", serialHistory); persisted.setLong("SerialStart", 0);
            persisted.setLong("SerialEnd", serialHistory.length); persisted.setBoolean("SerialTruncated", false);
        }
        state = RoboBoardStateNbtCodec.read(persisted);
        boolean invalid = "UNKNOWN_SCHEMA".equals(state.getFault()) || "INVALID_IDENTITY".equals(state.getFault())
                || "INVALID_PERSISTED_STATE".equals(state.getFault());
        preservedInvalidState = !migration.isSupported() || invalid ? NbtMigrations.copy(tag) : null;
        migrationPending = migration.isSupported() && migration.isChanged() && !invalid;
        unloaded = false;
        inFlight = null;
        requestMetadata = null;
        clientVisualReceived = false;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound visual = new NBTTagCompound();
        visual.setByte("Visual", (byte) visualFlags());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, visual);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        int flags = packet.func_148857_g().getByte("Visual") & 0xff;
        clientRunning = (flags & VISUAL_RUNNING) != 0;
        clientFault = (flags & VISUAL_FAULT) != 0;
        clientHasFirmware = (flags & VISUAL_FIRMWARE) != 0;
        clientD13High = (flags & VISUAL_D13) != 0;
        clientVisualReceived = true;
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private int visualFlags() {
        int flags = 0;
        if (state.isRunning()) flags |= VISUAL_RUNNING;
        if (state.hasFault()) flags |= VISUAL_FAULT;
        if (state.hasFirmware()) flags |= VISUAL_FIRMWARE;
        if (state.isD13High()) flags |= VISUAL_D13;
        return flags;
    }

    private void publishMutation(int oldVisual) {
        markDirty();
        if (worldObj != null && oldVisual != visualFlags()) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    static long nextAbsoluteTarget(long cycles, long worldTick) {
        if (cycles < 0 || worldTick < 0) throw new IllegalArgumentException("Negative logical time");
        long quantumTarget = cycles > Long.MAX_VALUE - 800000L ? Long.MAX_VALUE : cycles + 800000L;
        long tickNumber = worldTick == Long.MAX_VALUE ? Long.MAX_VALUE : worldTick + 1L;
        long tickBudget = tickNumber > Long.MAX_VALUE / 800000L ? Long.MAX_VALUE : tickNumber * 800000L;
        return Math.min(quantumTarget, tickBudget);
    }

    private void commitRuntimeResult(RequestMetadata metadata, RuntimeProtocol.Result result) {
        if (result == null || !sameIdentity(metadata, result.identity)) return;
        try {
            AvrMachineState machine = AvrCheckpointCodec.decode(result.checkpoint);
            CRLFirmware firmware = CRLFirmware.decode(state.getFirmware());
            if (machine.getWordPc() >= firmware.getFlash().length / 2
                    || result.completedAtCycle < AvrCheckpointCodec.decode(metadata.checkpoint).getCycles()
                    || result.completedAtCycle > metadata.absoluteTarget + 7L
                    || !RuntimeResultValidator.valid(result, machine, metadata.checkpoint)) {
                commitRuntimeFault(metadata, "RUNTIME_PROTOCOL");
                return;
            }
            String fault = result.fault == null ? "" : "AVR_FAULT_" + result.fault.code;
            RoboBoardState.Status status = result.fault == null
                    ? RoboBoardState.Status.RUNNING : RoboBoardState.Status.FAULT;
            int oldVisual = visualFlags();
            int oldOutputMask = state.getOutputMask();
            int oldHighMask = state.getHighMask();
            int oldPwmMask = state.getPwmMask();
            int[] oldPwmMode = state.getPwmMode();
            int[] oldPwmCompare = state.getPwmCompare();
            state.commitRuntimeCheckpoint(metadata.generation, metadata.revision, result.checkpoint, status, fault,
                    status == RoboBoardState.Status.RUNNING, result.d13High,
                    result.gpio, result.pwm, result.tx);
            if (outputsChanged(oldOutputMask, oldHighMask, oldPwmMask, oldPwmMode, oldPwmCompare))
                invalidatePortsAndDefer();
            publishMutation(oldVisual);
        } catch (RuntimeException invalid) {
            commitRuntimeFault(metadata, "RUNTIME_PROTOCOL");
        } catch (AvrFault invalid) {
            commitRuntimeFault(metadata, "RUNTIME_PROTOCOL");
        }
    }

    private void commitRuntimeFault(RequestMetadata metadata, String code) {
        if (!matchesCurrent(metadata)) return;
        byte[] checkpoint = metadata.checkpoint;
        try {
            try {
                if (checkpoint.length == 0) checkpoint = state.prepareRuntimeCheckpoint(metadata.generation, metadata.revision);
                else AvrCheckpointCodec.decode(checkpoint);
            } catch (Exception invalidCheckpoint) {
                checkpoint = AvrCheckpointCodec.encode(new AvrMachineState());
            }
            int oldVisual = visualFlags();
            state.commitRuntimeCheckpoint(metadata.generation, metadata.revision, checkpoint,
                    RoboBoardState.Status.FAULT, boundedFault(code), false, false);
            invalidatePortsAndDefer();
            publishMutation(oldVisual);
        } catch (RuntimeException staleOrInvalid) {
            // A concurrent board mutation owns the newer state.
        }
    }

    private boolean matchesCurrent(RequestMetadata metadata) {
        return metadata != null && worldObj != null && !worldObj.isRemote
                && metadata.dimension == dimension() && metadata.x == xCoord && metadata.y == yCoord && metadata.z == zCoord
                && metadata.generation == state.getGeneration() && metadata.revision == state.getRevision();
    }

    private int dimension() { return worldObj.provider.dimensionId; }

    private static boolean sameIdentity(RequestMetadata metadata, RuntimeProtocol.Identity identity) {
        return identity != null && identity.kind == RuntimeProtocol.Identity.STATIC_BOARD
                && metadata.dimension == identity.dimension && metadata.x == identity.x
                && metadata.y == identity.y && metadata.z == identity.z && metadata.generation == identity.generation;
    }

    private static String failureCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) return "RUNTIME_TIMEOUT";
            if (current instanceof RuntimeProtocol.ProtocolException) return "RUNTIME_PROTOCOL";
        }
        return "RUNTIME_WORKER_FAILED";
    }

    private void cancelInFlight() {
        RuntimeSupervisor.Submission submission = inFlight;
        inFlight = null;
        requestMetadata = null;
        if (submission != null) submission.cancel();
    }

    private boolean outputsChanged(int outputMask, int highMask, int pwmMask,
                                   int[] pwmMode, int[] pwmCompare) {
        return outputMask != state.getOutputMask() || highMask != state.getHighMask()
                || pwmMask != state.getPwmMask() || !java.util.Arrays.equals(pwmMode, state.getPwmMode())
                || !java.util.Arrays.equals(pwmCompare, state.getPwmCompare());
    }

    private void invalidatePortsAndDefer() {
        RoboBoardIoBridge.invalidateAdjacentPorts(this);
        if (worldObj != null) resumeAfterWorldTick = worldObj.getTotalWorldTime();
    }

    private static String boundedFault(String value) {
        String safe = value == null || value.length() == 0 ? "RUNTIME_FAILED" : value;
        StringBuilder bounded = new StringBuilder();
        for (int i = 0; i < safe.length() && bounded.length() < RoboBoardState.MAX_FAULT_BYTES; i++) {
            char character = safe.charAt(i);
            bounded.append(character >= 0x20 && character <= 0x7e ? character : '_');
        }
        return bounded.toString();
    }

    private static final class RequestMetadata {
        final int dimension, x, y, z;
        final long generation, revision;
        final byte[] checkpoint;
        final long absoluteTarget;

        RequestMetadata(int dimension, int x, int y, int z, long generation, long revision,
                        byte[] checkpoint, long absoluteTarget) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.generation = generation;
            this.revision = revision;
            this.checkpoint = checkpoint.clone();
            this.absoluteTarget = absoluteTarget;
        }
    }

    private void requireServer() {
        if (worldObj == null || worldObj.isRemote) throw new IllegalStateException("RoboBoard mutations require the server world");
    }

    public UUID getBoardId() { return state.getBoardId(); }
    public long getGeneration() { return state.getGeneration(); }
    public long getRevision() { return state.getRevision(); }
    public byte[] getFirmware() { return state.getFirmware(); }
    public byte[] getCheckpoint() { return state.getCheckpoint(); }
    public RoboBoardState.Status getStatus() { return state.getStatus(); }
    public String getFault() { return state.getFault(); }
    public UUID getOwnerId() { return ownerId; }
    public boolean claimOwner(UUID playerId) {
        if (ownerId != null || playerId == null) return false;
        ownerId = playerId;
        markDirty();
        return true;
    }
    public boolean canAccess(EntityPlayer player) {
        return player != null && (ownerId != null && ownerId.equals(player.getUniqueID())
                || player.canCommandSenderUseCommand(2, "craftonica"));
    }
    public void setTemplateSketchSource(String source) {
        requireServer();
        byte[] encoded = source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > SourceBundle.MAX_FILE_BYTES) throw new IllegalArgumentException("Template exceeds source limit");
        templateSketchSource = encoded;
        markDirty();
    }
    public byte[] getEditorSketchSource() {
        return hasInstalledSketchSource() ? getInstalledSketchSource() : templateSketchSource.clone();
    }
    public boolean isRunning() { return useClientVisual() ? clientRunning : state.isRunning(); }
    public boolean hasFault() { return useClientVisual() ? clientFault : state.hasFault(); }
    public boolean hasFirmware() { return useClientVisual() ? clientHasFirmware : state.hasFirmware(); }
    public boolean isD13High() { return useClientVisual() ? clientD13High : state.isD13High(); }
    public boolean isPinOutput(int pin) { requirePin(pin); return (state.getOutputMask() & (1 << pin)) != 0; }
    public boolean isPinHigh(int pin) { requirePin(pin); return (state.getHighMask() & (1 << pin)) != 0; }
    public boolean isPinPwm(int pin) { requirePin(pin); return (state.getPwmMask() & (1 << pin)) != 0; }
    public boolean isPinPwmRecognized(int pin) {
        requirePin(pin);
        if (!isPinPwm(pin)) return false;
        int mode = state.getPwmMode()[pin];
        int compare = state.getPwmCompare()[pin];
        return (mode == 1 || mode == 3 || mode == 5) && compare >= 0 && compare <= 255;
    }
    public int getPinPwmCompare(int pin) { requirePin(pin); return state.getPwmCompare()[pin]; }
    public int getStableInputMask() { return state.getStableInputMask(); }
    public int getIndeterminateInputMask() { return state.getIndeterminateInputMask(); }
    public byte[] getInstalledSketchSource() { return state.getInstalledSketchSource(); }
    public boolean hasInstalledSketchSource() { return state.hasInstalledSketchSource(); }
    public RoboBoardState.SerialHistorySnapshot getSerialHistorySnapshot() {
        return state.getSerialHistorySnapshot();
    }
    public RoboBoardState copyBoardState() {
        NBTTagCompound tag = new NBTTagCompound();
        RoboBoardStateNbtCodec.write(state, tag);
        return RoboBoardStateNbtCodec.read(tag);
    }
    public byte[] getTemplateSketchSource() { return templateSketchSource.clone(); }
    public void restoreFromRobot(RoboBoardState value, byte[] templateSketch, UUID owner) {
        if (value == null || templateSketch == null || templateSketch.length > SourceBundle.MAX_FILE_BYTES
                || owner == null || worldObj == null || worldObj.isRemote)
            throw new IllegalArgumentException("mobile board state");
        NBTTagCompound tag = new NBTTagCompound();
        RoboBoardStateNbtCodec.write(value, tag);
        state = RoboBoardStateNbtCodec.read(tag);
        ownerId = owner; templateSketchSource = templateSketch.clone(); preservedInvalidState = null;
        unloaded = false; inFlight = null; requestMetadata = null;
        markDirty(); worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private static void requirePin(int pin) {
        if (pin < 0 || pin >= RoboBoardState.OUTPUT_PIN_COUNT) throw new IndexOutOfBoundsException("pin");
    }

    private boolean useClientVisual() {
        return clientVisualReceived && worldObj != null && worldObj.isRemote;
    }
}
