package br.com.craftonica.tile;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.runtime.server.RuntimeServer;
import br.com.craftonica.runtime.server.RuntimeSupervisor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;
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

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || unloaded) return;
        if (!state.isRunning()) {
            cancelInFlight();
            return;
        }
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
            inFlight = supervisor.submit(new RuntimeProtocol.Request(identity, target, state.getFirmware(), checkpoint,
                    AvrInputs.allLow()), batchTarget);
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
        publishMutation(oldVisual);
        return revision;
    }

    public long startFirmware(long expectedRevision) {
        requireServer();
        int oldVisual = visualFlags();
        long revision = state.start(expectedRevision);
        publishMutation(oldVisual);
        return revision;
    }

    public long stopFirmware(long expectedRevision) {
        requireServer();
        cancelInFlight();
        int oldVisual = visualFlags();
        long revision = state.stop(expectedRevision);
        publishMutation(oldVisual);
        return revision;
    }

    public void commitRuntimeCheckpoint(long expectedGeneration, long expectedRevision, byte[] checkpoint,
                                        RoboBoardState.Status status, String fault, boolean running, boolean d13High) {
        requireServer();
        int oldVisual = visualFlags();
        state.commitRuntimeCheckpoint(expectedGeneration, expectedRevision, checkpoint, status, fault, running, d13High);
        publishMutation(oldVisual);
    }

    public long unloadAndIncrementGeneration() {
        requireServer();
        if (unloaded) return state.getGeneration();
        cancelInFlight();
        unloaded = true;
        long generation = state.unloadAndIncrementGeneration();
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
        RoboBoardState.Persisted persisted = state.snapshot();
        tag.setInteger("Schema", persisted.schema);
        tag.setLong("BoardMost", persisted.boardId.getMostSignificantBits());
        tag.setLong("BoardLeast", persisted.boardId.getLeastSignificantBits());
        tag.setLong("Generation", persisted.generation);
        tag.setLong("Revision", persisted.revision);
        tag.setByteArray("Firmware", persisted.firmware);
        tag.setByteArray("FirmwareHash", persisted.firmwareHash);
        tag.setByteArray("Checkpoint", persisted.checkpoint);
        tag.setByteArray("CheckpointHash", persisted.checkpointHash);
        tag.setByte("Status", (byte) persisted.statusOrdinal);
        tag.setString("Fault", persisted.fault);
        tag.setBoolean("Running", persisted.running);
        tag.setBoolean("D13", persisted.d13High);
        tag.setInteger("OutputMask", persisted.outputMask);
        tag.setInteger("HighMask", persisted.highMask);
        tag.setInteger("PwmMask", persisted.pwmMask);
        tag.setIntArray("PwmMode", persisted.pwmMode);
        tag.setIntArray("PwmPrescaler", persisted.pwmPrescaler);
        tag.setIntArray("PwmCompare", persisted.pwmCompare);
        tag.setByteArray("LastTx", persisted.lastTx);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        UUID boardId = tag.hasKey("BoardMost") && tag.hasKey("BoardLeast")
                ? new UUID(tag.getLong("BoardMost"), tag.getLong("BoardLeast")) : null;
        state = RoboBoardState.restore(new RoboBoardState.Persisted(
                tag.hasKey("Schema") ? tag.getInteger("Schema") : -1,
                boardId,
                tag.getLong("Generation"),
                tag.getLong("Revision"),
                tag.getByteArray("Firmware"),
                tag.getByteArray("FirmwareHash"),
                tag.getByteArray("Checkpoint"),
                tag.getByteArray("CheckpointHash"),
                tag.hasKey("Status") ? tag.getByte("Status") : -1,
                tag.getString("Fault"),
                tag.getBoolean("Running"),
                tag.getBoolean("D13"),
                tag.getInteger("OutputMask"),
                tag.getInteger("HighMask"),
                tag.getInteger("PwmMask"),
                intArray(tag, "PwmMode"),
                intArray(tag, "PwmPrescaler"),
                intArray(tag, "PwmCompare"),
                tag.getByteArray("LastTx")));
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
                    || !validOutputs(result, machine, metadata)) {
                commitRuntimeFault(metadata, "RUNTIME_PROTOCOL");
                return;
            }
            String fault = result.fault == null ? "" : "AVR_FAULT_" + result.fault.code;
            RoboBoardState.Status status = result.fault == null
                    ? RoboBoardState.Status.RUNNING : RoboBoardState.Status.FAULT;
            int oldVisual = visualFlags();
            state.commitRuntimeCheckpoint(metadata.generation, metadata.revision, result.checkpoint, status, fault,
                    status == RoboBoardState.Status.RUNNING, result.d13High,
                    result.gpio, result.pwm, result.tx);
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
        return identity != null && metadata.dimension == identity.dimension && metadata.x == identity.x
                && metadata.y == identity.y && metadata.z == identity.z && metadata.generation == identity.generation;
    }

    private static String failureCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) return "RUNTIME_TIMEOUT";
            if (current instanceof RuntimeProtocol.ProtocolException) return "RUNTIME_PROTOCOL";
        }
        return "RUNTIME_WORKER_FAILED";
    }

    private static boolean validOutputs(RuntimeProtocol.Result result, AvrMachineState machine,
                                        RequestMetadata metadata) {
        long firstCycle;
        try {
            firstCycle = AvrCheckpointCodec.decode(metadata.checkpoint).getCycles();
        } catch (AvrFault invalid) {
            return false;
        }
        if (result.gpio.size() + result.pwm.size() > 1024) return false;
        for (RuntimeProtocol.Gpio value : result.gpio) {
            if (value.pin < 0 || value.pin >= RoboBoardState.OUTPUT_PIN_COUNT
                    || value.cycle < firstCycle || value.cycle > result.completedAtCycle) return false;
        }
        for (RuntimeProtocol.Pwm value : result.pwm) {
            int expectedTimer = timerForPwmPin(value.pin);
            if (value.pin < 0 || value.pin >= RoboBoardState.OUTPUT_PIN_COUNT
                    || value.timer != expectedTimer || value.mode < 0 || value.mode > 15
                    || value.compare < 0 || value.compare > (value.timer == 1 ? 65535 : 255)
                    || !validPrescaler(value.timer, value.prescaler)
                    || value.phaseCorrect != (value.mode == 1 || value.mode == 5)
                    || value.cycle < firstCycle || value.cycle > result.completedAtCycle) return false;
        }
        boolean checkpointD13 = (machine.getMmio(0x24) & 0x20) != 0 && (machine.getMmio(0x25) & 0x20) != 0;
        return result.d13High == checkpointD13;
    }

    private static boolean validPrescaler(int timer, int value) {
        if (value == 0) return true;
        if (timer == 2) return value == 1 || value == 8 || value == 32 || value == 64
                || value == 128 || value == 256 || value == 1024;
        return value == 1 || value == 8 || value == 64 || value == 256 || value == 1024;
    }

    private static int timerForPwmPin(int pin) {
        if (pin == 5 || pin == 6) return 0;
        if (pin == 9 || pin == 10) return 1;
        if (pin == 3 || pin == 11) return 2;
        return -1;
    }

    private void cancelInFlight() {
        RuntimeSupervisor.Submission submission = inFlight;
        inFlight = null;
        requestMetadata = null;
        if (submission != null) submission.cancel();
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

    private static int[] intArray(NBTTagCompound tag, String key) {
        return tag.hasKey(key) ? tag.getIntArray(key) : new int[0];
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
    public boolean isRunning() { return useClientVisual() ? clientRunning : state.isRunning(); }
    public boolean hasFault() { return useClientVisual() ? clientFault : state.hasFault(); }
    public boolean hasFirmware() { return useClientVisual() ? clientHasFirmware : state.hasFirmware(); }
    public boolean isD13High() { return useClientVisual() ? clientD13High : state.isD13High(); }

    private boolean useClientVisual() {
        return clientVisualReceived && worldObj != null && worldObj.isRemote;
    }
}
