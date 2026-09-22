package br.com.craftonica.tile;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.persistence.NbtMigrations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Pure, bounded persistent state for a RoboBoard. Forge adaptation lives in the tile entity. */
public final class RoboBoardState {
    public static final int SCHEMA_VERSION = NbtMigrations.ROBO_BOARD_SCHEMA_VERSION;
    public static final int MAX_CHECKPOINT_BYTES = 16384;
    public static final int MAX_FAULT_BYTES = 96;
    public static final int OUTPUT_PIN_COUNT = 20;
    public static final int MAX_SERIAL_HISTORY_BYTES = 8192;
    public static final int MAX_TX_BYTES = MAX_SERIAL_HISTORY_BYTES;
    public static final String SKETCH_NAME = "Sketch";
    public static final String SKETCH_FILE = "Sketch.ino";
    private static final int HASH_BYTES = 32;

    public enum Status { DISABLED, STOPPED, RUNNING, SUSPENDED, FAULT }

    private UUID boardId;
    private long generation;
    private long revision;
    private byte[] firmware = new byte[0];
    private byte[] firmwareHash = new byte[0];
    private byte[] checkpoint = new byte[0];
    private byte[] checkpointHash = new byte[0];
    private Status status = Status.DISABLED;
    private String fault = "";
    private boolean running;
    private boolean d13High;
    private int outputMask;
    private int highMask;
    private int pwmMask;
    private int[] pwmMode = new int[OUTPUT_PIN_COUNT];
    private int[] pwmPrescaler = new int[OUTPUT_PIN_COUNT];
    private int[] pwmCompare = new int[OUTPUT_PIN_COUNT];
    private int stableInputMask;
    private int indeterminateInputMask;
    private byte[] serialHistory = new byte[0];
    private long serialStartOffset;
    private long serialEndOffset;
    private byte[] installedSketchSource = new byte[0];
    private boolean installedSketchSourcePresent;

    public RoboBoardState() {
        this(UUID.randomUUID());
    }

    public RoboBoardState(UUID boardId) {
        if (boardId == null) throw new IllegalArgumentException("Board UUID is required");
        this.boardId = boardId;
    }

    public long installVerifiedFirmware(CRLFirmware verifiedFirmware, long expectedRevision) {
        if (verifiedFirmware == null) throw new IllegalArgumentException("Verified firmware is required");
        requireRevision(expectedRevision);
        byte[] encoded = verifiedFirmware.getBytes();
        CRLFirmware decoded = CRLFirmware.decode(encoded);
        return installFirmware(encoded, decoded, new byte[0], false);
    }

    public long installCompiledSketch(SourceBundle sources, CRLFirmware verifiedFirmware, long expectedRevision) {
        if (sources == null || verifiedFirmware == null)
            throw new IllegalArgumentException("Sources and verified firmware are required");
        requireRevision(expectedRevision);
        if (!SKETCH_NAME.equals(sources.getMainName()) || !SKETCH_FILE.equals(sources.getMainPath())
                || sources.size() != 1 || !SKETCH_FILE.equals(sources.getFileNames().get(0)))
            throw new IllegalArgumentException("Installed sketch must be the single fixed Sketch.ino file");
        byte[] source = sources.getFile(SKETCH_FILE);
        if (source == null || source.length > SourceBundle.MAX_FILE_BYTES)
            throw new IllegalArgumentException("Installed sketch exceeds its bound");
        byte[] encoded = verifiedFirmware.getBytes();
        CRLFirmware decoded = CRLFirmware.decode(encoded);
        if (!Arrays.equals(sources.getSourceHash(), decoded.getSourceHash()))
            throw new IllegalArgumentException("Sketch source hash does not match firmware");
        return installFirmware(encoded, decoded, source, true);
    }

    private long installFirmware(byte[] encoded, CRLFirmware decoded, byte[] source, boolean sourcePresent) {
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted");
        incrementGeneration();
        revision++;
        firmware = encoded;
        firmwareHash = decoded.getFirmwareHash();
        checkpoint = new byte[0];
        checkpointHash = sha256(checkpoint);
        status = Status.STOPPED;
        fault = "";
        running = false;
        d13High = false;
        clearAppliedOutputs();
        clearSerialHistory();
        installedSketchSource = source.clone();
        installedSketchSourcePresent = sourcePresent;
        return revision;
    }

    public long start(long expectedRevision) {
        if (!hasFirmware()) throw new IllegalStateException("No verified firmware is installed");
        requireRevision(expectedRevision);
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted");
        revision++;
        status = Status.RUNNING;
        fault = "";
        running = true;
        return revision;
    }

    public long stop(long expectedRevision) {
        if (!hasFirmware()) throw new IllegalStateException("No verified firmware is installed");
        requireRevision(expectedRevision);
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted");
        revision++;
        status = Status.STOPPED;
        fault = "";
        running = false;
        d13High = false;
        clearAppliedOutputs();
        return revision;
    }

    public void commitRuntimeCheckpoint(long expectedGeneration, long expectedRevision, byte[] nextCheckpoint,
                                         Status nextStatus, String nextFault, boolean nextRunning, boolean nextD13High) {
        commitRuntimeCheckpoint(expectedGeneration, expectedRevision, nextCheckpoint, nextStatus, nextFault,
                nextRunning, nextD13High, Collections.<RuntimeProtocol.Gpio>emptyList(),
                Collections.<RuntimeProtocol.Pwm>emptyList(), new byte[0]);
    }

    public void commitRuntimeCheckpoint(long expectedGeneration, long expectedRevision, byte[] nextCheckpoint,
                                         Status nextStatus, String nextFault, boolean nextRunning, boolean nextD13High,
                                         List<RuntimeProtocol.Gpio> gpio, List<RuntimeProtocol.Pwm> pwm, byte[] tx) {
        if (!hasFirmware()) throw new IllegalStateException("No verified firmware is installed");
        if (expectedGeneration != generation) throw new IllegalStateException("Stale board generation");
        requireRevision(expectedRevision);
        validateCheckpoint(nextCheckpoint);
        validateVisualState(nextStatus, nextFault, nextRunning);
        if ((nextStatus == Status.RUNNING || nextStatus == Status.SUSPENDED)
                && nextD13High != checkpointD13(nextCheckpoint))
            throw new IllegalArgumentException("D13 state disagrees with checkpoint");
        if (gpio == null || pwm == null || gpio.size() + pwm.size() > 1024
                || tx == null || tx.length > MAX_TX_BYTES)
            throw new IllegalArgumentException("Applied runtime output exceeds its bound");
        if (serialEndOffset > Long.MAX_VALUE - tx.length)
            throw new IllegalStateException("Serial history offset exhausted");

        int nextOutputMask = outputMask;
        int nextHighMask = highMask;
        int nextPwmMask = pwmMask;
        int[] nextPwmMode = pwmMode.clone();
        int[] nextPwmPrescaler = pwmPrescaler.clone();
        int[] nextPwmCompare = pwmCompare.clone();
        for (RuntimeProtocol.Gpio value : gpio) {
            requirePin(value.pin);
            int bit = 1 << value.pin;
            nextOutputMask = value.output ? nextOutputMask | bit : nextOutputMask & ~bit;
            nextHighMask = value.high ? nextHighMask | bit : nextHighMask & ~bit;
            nextPwmMask &= ~bit;
        }
        for (RuntimeProtocol.Pwm value : pwm) {
            requirePin(value.pin);
            int bit = 1 << value.pin;
            if (value.prescaler <= 0) nextPwmMask &= ~bit;
            else nextPwmMask |= bit;
            nextPwmMode[value.pin] = value.mode;
            nextPwmPrescaler[value.pin] = value.prescaler;
            nextPwmCompare[value.pin] = value.compare;
        }

        checkpoint = nextCheckpoint.clone();
        checkpointHash = sha256(checkpoint);
        status = nextStatus;
        fault = nextFault;
        running = nextRunning;
        if (nextStatus != Status.RUNNING && nextStatus != Status.SUSPENDED) {
            clearAppliedOutputs();
            d13High = false;
        } else {
            outputMask = nextOutputMask;
            highMask = nextHighMask;
            pwmMask = nextPwmMask;
            pwmMode = nextPwmMode;
            pwmPrescaler = nextPwmPrescaler;
            pwmCompare = nextPwmCompare;
            d13High = nextD13High;
        }
        appendSerial(tx);
    }

    public byte[] prepareRuntimeCheckpoint(long expectedGeneration, long expectedRevision) {
        if (!hasFirmware()) throw new IllegalStateException("No verified firmware is installed");
        if (expectedGeneration != generation) throw new IllegalStateException("Stale board generation");
        requireRevision(expectedRevision);
        if (checkpoint.length == 0) {
            checkpoint = AvrCheckpointCodec.encode(new AvrMachineState());
            checkpointHash = sha256(checkpoint);
        }
        validateCheckpoint(checkpoint);
        return checkpoint.clone();
    }

    public long unloadAndIncrementGeneration() {
        incrementGeneration();
        running = false;
        d13High = false;
        clearAppliedOutputs();
        if (hasFirmware() && status == Status.RUNNING) status = Status.STOPPED;
        return generation;
    }

    public void recordInputDiagnostics(int stableMask, int indeterminateMask) {
        int validPins = (1 << OUTPUT_PIN_COUNT) - 1;
        if (((stableMask | indeterminateMask) & ~validPins) != 0)
            throw new IllegalArgumentException("Input diagnostic mask is invalid");
        stableInputMask = stableMask;
        indeterminateInputMask = indeterminateMask;
    }

    private void incrementGeneration() {
        if (generation == Long.MAX_VALUE) {
            failClosed("GENERATION_EXHAUSTED");
            throw new IllegalStateException("Generation exhausted");
        }
        generation++;
    }

    private void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) throw new IllegalStateException("Stale board revision");
    }

    static RoboBoardState restore(Persisted persisted) {
        RoboBoardState state = new RoboBoardState();
        if (persisted == null || persisted.schema != SCHEMA_VERSION) {
            state.failClosed("UNKNOWN_SCHEMA");
            return state;
        }
        if (persisted.boardId == null || persisted.generation < 0 || persisted.revision < 0) {
            state.failClosed("INVALID_IDENTITY");
            return state;
        }
        state.boardId = persisted.boardId;
        state.generation = persisted.generation;
        state.revision = persisted.revision;
        try {
            if (persisted.statusOrdinal < 0 || persisted.statusOrdinal >= Status.values().length)
                throw new IllegalArgumentException("Unknown status");
            Status restoredStatus = Status.values()[persisted.statusOrdinal];
            byte[] restoredFirmware = requiredBytes(persisted.firmware);
            byte[] restoredFirmwareHash = requiredBytes(persisted.firmwareHash);
            byte[] restoredCheckpoint = requiredBytes(persisted.checkpoint);
            byte[] restoredCheckpointHash = requiredBytes(persisted.checkpointHash);
            byte[] restoredTx = requiredBytes(persisted.serialHistory);
            byte[] restoredSource = requiredBytes(persisted.installedSketchSource);
            String restoredFault = persisted.fault == null ? "" : persisted.fault;
            validateVisualState(restoredStatus, restoredFault, persisted.running);

            if (restoredFirmware.length == 0) {
                if (restoredFirmwareHash.length != 0 || restoredCheckpoint.length != 0
                        || restoredCheckpointHash.length != 0 || restoredStatus != Status.DISABLED
                        || persisted.running || persisted.d13High || persisted.outputMask != 0
                         || persisted.highMask != 0 || persisted.pwmMask != 0 || restoredTx.length != 0
                         || restoredSource.length != 0 || persisted.installedSketchSourcePresent
                         || persisted.serialStartOffset != 0
                         || persisted.serialEndOffset != 0)
                    throw new IllegalArgumentException("State exists without firmware");
            } else {
                CRLFirmware decoded = CRLFirmware.decode(restoredFirmware);
                if (restoredFirmwareHash.length != HASH_BYTES
                        || !Arrays.equals(restoredFirmwareHash, decoded.getFirmwareHash()))
                    throw new IllegalArgumentException("Firmware hash mismatch");
                if (restoredCheckpoint.length > MAX_CHECKPOINT_BYTES || restoredCheckpointHash.length != HASH_BYTES
                        || !Arrays.equals(restoredCheckpointHash, sha256(restoredCheckpoint)))
                    throw new IllegalArgumentException("Checkpoint hash mismatch");
                if (restoredCheckpoint.length != 0) validateCheckpoint(restoredCheckpoint, decoded);
                validateInstalledSource(restoredSource, persisted.installedSketchSourcePresent, decoded);
            }
            int validPins = (1 << OUTPUT_PIN_COUNT) - 1;
            int[] restoredPwmMode = requiredInts(persisted.pwmMode);
            int[] restoredPwmPrescaler = requiredInts(persisted.pwmPrescaler);
            int[] restoredPwmCompare = requiredInts(persisted.pwmCompare);
            if (((persisted.outputMask | persisted.highMask | persisted.pwmMask) & ~validPins) != 0
                    || ((persisted.stableInputMask | persisted.indeterminateInputMask) & ~validPins) != 0
                    || restoredPwmMode.length != OUTPUT_PIN_COUNT
                    || restoredPwmPrescaler.length != OUTPUT_PIN_COUNT
                     || restoredPwmCompare.length != OUTPUT_PIN_COUNT || restoredTx.length > MAX_TX_BYTES
                     || persisted.serialStartOffset < 0 || persisted.serialEndOffset < persisted.serialStartOffset
                     || persisted.serialEndOffset - persisted.serialStartOffset != restoredTx.length
                     || persisted.serialTruncated != (persisted.serialStartOffset > 0))
                throw new IllegalArgumentException("Applied output state is invalid");
            validatePersistedOutputs(restoredStatus, persisted.outputMask, persisted.highMask, persisted.pwmMask,
                    restoredPwmMode, restoredPwmPrescaler, restoredPwmCompare, restoredTx);
            if ((restoredStatus == Status.RUNNING || restoredStatus == Status.SUSPENDED)
                    && restoredCheckpoint.length != 0 && persisted.d13High != checkpointD13(restoredCheckpoint))
                throw new IllegalArgumentException("D13 state disagrees with checkpoint");

            state.firmware = restoredFirmware.clone();
            state.firmwareHash = restoredFirmwareHash.clone();
            state.checkpoint = restoredCheckpoint.clone();
            state.checkpointHash = restoredCheckpointHash.clone();
            state.status = restoredStatus;
            state.fault = restoredFault;
            state.running = persisted.running;
            state.d13High = persisted.d13High;
            state.outputMask = persisted.outputMask;
            state.highMask = persisted.highMask;
            state.pwmMask = persisted.pwmMask;
            state.pwmMode = restoredPwmMode.clone();
            state.pwmPrescaler = restoredPwmPrescaler.clone();
            state.pwmCompare = restoredPwmCompare.clone();
            state.stableInputMask = persisted.stableInputMask;
            state.indeterminateInputMask = persisted.indeterminateInputMask;
            state.serialHistory = restoredTx.clone();
            state.serialStartOffset = persisted.serialStartOffset;
            state.serialEndOffset = persisted.serialEndOffset;
            state.installedSketchSource = restoredSource.clone();
            state.installedSketchSourcePresent = persisted.installedSketchSourcePresent;
        } catch (IllegalArgumentException exception) {
            state.failClosed("INVALID_PERSISTED_STATE");
        }
        return state;
    }

    Persisted snapshot() {
        return new Persisted(SCHEMA_VERSION, boardId, generation, revision, firmware, firmwareHash,
                checkpoint, checkpointHash, status.ordinal(), fault, running, d13High,
                outputMask, highMask, pwmMask, pwmMode, pwmPrescaler, pwmCompare,
                stableInputMask, indeterminateInputMask, serialHistory, serialStartOffset,
                serialEndOffset, serialStartOffset > 0, installedSketchSource,
                installedSketchSourcePresent);
    }

    private void failClosed(String reason) {
        firmware = new byte[0];
        firmwareHash = new byte[0];
        checkpoint = new byte[0];
        checkpointHash = new byte[0];
        installedSketchSource = new byte[0];
        installedSketchSourcePresent = false;
        clearSerialHistory();
        status = Status.DISABLED;
        fault = reason;
        running = false;
        d13High = false;
        clearAppliedOutputs();
    }

    private void clearAppliedOutputs() {
        outputMask = 0;
        highMask = 0;
        pwmMask = 0;
        Arrays.fill(pwmMode, 0);
        Arrays.fill(pwmPrescaler, 0);
        Arrays.fill(pwmCompare, 0);
    }

    private void appendSerial(byte[] tx) {
        if (tx.length == 0) return;
        int keepOld = Math.min(serialHistory.length, MAX_TX_BYTES - Math.min(tx.length, MAX_TX_BYTES));
        int keepNew = Math.min(tx.length, MAX_TX_BYTES);
        byte[] appended = new byte[keepOld + keepNew];
        System.arraycopy(serialHistory, serialHistory.length - keepOld, appended, 0, keepOld);
        System.arraycopy(tx, tx.length - keepNew, appended, keepOld, keepNew);
        serialEndOffset += tx.length;
        serialStartOffset = serialEndOffset - appended.length;
        serialHistory = appended;
    }

    private void clearSerialHistory() {
        serialHistory = new byte[0];
        serialStartOffset = 0;
        serialEndOffset = 0;
    }

    private void validateCheckpoint(byte[] value) {
        validateCheckpoint(value, CRLFirmware.decode(firmware));
    }

    private static void validateCheckpoint(byte[] value, CRLFirmware firmware) {
        if (value == null || !AvrCheckpointCodec.isSupportedLength(value.length) || value.length > MAX_CHECKPOINT_BYTES)
            throw new IllegalArgumentException("Checkpoint is not canonical");
        try {
            AvrMachineState machine = AvrCheckpointCodec.decode(value);
            if (machine.getWordPc() < 0 || machine.getWordPc() >= firmware.getFlash().length / 2)
                throw new IllegalArgumentException("Checkpoint PC is outside firmware FLASH");
        } catch (AvrFault invalid) {
            throw new IllegalArgumentException("Checkpoint is not canonical", invalid);
        }
    }

    private static void requirePin(int pin) {
        if (pin < 0 || pin >= OUTPUT_PIN_COUNT) throw new IllegalArgumentException("Runtime output pin is invalid");
    }

    private static void validatePersistedOutputs(Status status, int outputMask, int highMask, int pwmMask,
                                                  int[] modes, int[] prescalers, int[] compares, byte[] tx) {
        if (status != Status.RUNNING && status != Status.SUSPENDED
                && (outputMask != 0 || highMask != 0 || pwmMask != 0))
            throw new IllegalArgumentException("Inactive board has applied outputs");
        int supportedPwmPins = (1 << 3) | (1 << 5) | (1 << 6) | (1 << 9) | (1 << 10) | (1 << 11);
        if ((pwmMask & ~supportedPwmPins) != 0) throw new IllegalArgumentException("PWM pin is not mapped");
        for (int pin = 0; pin < OUTPUT_PIN_COUNT; pin++) {
            if ((pwmMask & (1 << pin)) == 0) continue;
            int timer = timerForPin(pin);
            if (modes[pin] < 0 || modes[pin] > 15 || !validPrescaler(timer, prescalers[pin])
                    || compares[pin] < 0 || compares[pin] > (timer == 1 ? 65535 : 255))
                throw new IllegalArgumentException("PWM descriptor is invalid");
        }
    }

    private static int timerForPin(int pin) {
        if (pin == 5 || pin == 6) return 0;
        if (pin == 9 || pin == 10) return 1;
        if (pin == 3 || pin == 11) return 2;
        return -1;
    }

    private static boolean validPrescaler(int timer, int value) {
        if (value <= 0) return false;
        if (timer == 2) return value == 1 || value == 8 || value == 32 || value == 64
                || value == 128 || value == 256 || value == 1024;
        return value == 1 || value == 8 || value == 64 || value == 256 || value == 1024;
    }

    private static boolean checkpointD13(byte[] checkpoint) {
        try {
            AvrMachineState machine = AvrCheckpointCodec.decode(checkpoint);
            return (machine.getMmio(0x24) & 0x20) != 0 && (machine.getMmio(0x25) & 0x20) != 0;
        } catch (AvrFault invalid) {
            throw new IllegalArgumentException("Checkpoint is not canonical", invalid);
        }
    }

    private static void validateVisualState(Status status, String fault, boolean running) {
        if (status == null || fault == null) throw new IllegalArgumentException("Status and fault are required");
        int faultBytes = fault.getBytes(StandardCharsets.UTF_8).length;
        if (faultBytes > MAX_FAULT_BYTES) throw new IllegalArgumentException("Fault exceeds the bounded size");
        for (int i = 0; i < fault.length(); i++) {
            char value = fault.charAt(i);
            if (value < 0x20 || value > 0x7e) throw new IllegalArgumentException("Fault must be printable ASCII");
        }
        if (running != (status == Status.RUNNING)) throw new IllegalArgumentException("Running flag disagrees with status");
        if (status == Status.FAULT && fault.length() == 0) throw new IllegalArgumentException("Fault status needs a code");
        if (status != Status.FAULT && status != Status.DISABLED && fault.length() != 0)
            throw new IllegalArgumentException("Fault code is not valid for this status");
    }

    private static byte[] requiredBytes(byte[] value) {
        if (value == null) throw new IllegalArgumentException("Missing byte array");
        return value;
    }

    private static int[] requiredInts(int[] value) {
        if (value == null) throw new IllegalArgumentException("Missing integer array");
        return value;
    }

    private static void validateInstalledSource(byte[] source, boolean present, CRLFirmware firmware) {
        if (!present) {
            if (source.length != 0) throw new IllegalArgumentException("Sketch source presence is inconsistent");
            return;
        }
        if (source.length > SourceBundle.MAX_FILE_BYTES)
            throw new IllegalArgumentException("Installed sketch exceeds its bound");
        SourceBundle bundle = new SourceBundle(SKETCH_NAME,
                Collections.singletonMap(SKETCH_FILE, source));
        if (!Arrays.equals(bundle.getSourceHash(), firmware.getSourceHash()))
            throw new IllegalArgumentException("Installed sketch hash mismatch");
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public UUID getBoardId() { return boardId; }
    public long getGeneration() { return generation; }
    public long getRevision() { return revision; }
    public byte[] getFirmware() { return firmware.clone(); }
    public byte[] getCheckpoint() { return checkpoint.clone(); }
    public Status getStatus() { return status; }
    public String getFault() { return fault; }
    public boolean isRunning() { return running; }
    public boolean isD13High() { return d13High; }
    public boolean hasFirmware() { return firmware.length != 0; }
    public boolean hasFault() { return fault.length() != 0 || status == Status.FAULT; }
    public int getOutputMask() { return outputMask; }
    public int getHighMask() { return highMask; }
    public int getPwmMask() { return pwmMask; }
    public int[] getPwmMode() { return pwmMode.clone(); }
    public int[] getPwmPrescaler() { return pwmPrescaler.clone(); }
    public int[] getPwmCompare() { return pwmCompare.clone(); }

    /** Decodes the published Servo profile: Timer1 fast PWM, /8, TOP 39999, D9 or D10. */
    public Integer getServoPulseWidthMicros(int pin) {
        if (!running || (pin != 9 && pin != 10) || (outputMask & 1 << pin) == 0
                || (pwmMask & 1 << pin) == 0 || pwmMode[pin] != 14
                || pwmPrescaler[pin] != 8 || pwmCompare[pin] < 2000 || pwmCompare[pin] > 4000
                || checkpoint.length == 0) return null;
        try {
            AvrMachineState machine = AvrCheckpointCodec.decode(checkpoint);
            int top = machine.getMmio(0x86) | machine.getMmio(0x87) << 8;
            return top == 39999 ? Integer.valueOf(pwmCompare[pin] / 2) : null;
        } catch (AvrFault invalid) {
            return null;
        }
    }
    public int getStableInputMask() { return stableInputMask; }
    public int getIndeterminateInputMask() { return indeterminateInputMask; }
    public byte[] getLastTx() { return serialHistory.clone(); }
    public byte[] getInstalledSketchSource() { return installedSketchSource.clone(); }
    public boolean hasInstalledSketchSource() { return installedSketchSourcePresent; }
    public SerialHistorySnapshot getSerialHistorySnapshot() {
        return new SerialHistorySnapshot(serialHistory, serialStartOffset, serialEndOffset);
    }

    public static final class SerialHistorySnapshot {
        private final byte[] bytes;
        private final long startOffset;
        private final long endOffset;

        private SerialHistorySnapshot(byte[] bytes, long startOffset, long endOffset) {
            this.bytes = bytes.clone();
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public byte[] getBytes() { return bytes.clone(); }
        public long getStartOffset() { return startOffset; }
        public long getEndOffset() { return endOffset; }
        public boolean isTruncated() { return startOffset > 0; }
    }

    static final class Persisted {
        final int schema;
        final UUID boardId;
        final long generation;
        final long revision;
        final byte[] firmware;
        final byte[] firmwareHash;
        final byte[] checkpoint;
        final byte[] checkpointHash;
        final int statusOrdinal;
        final String fault;
        final boolean running;
        final boolean d13High;
        final int outputMask;
        final int highMask;
        final int pwmMask;
        final int[] pwmMode;
        final int[] pwmPrescaler;
        final int[] pwmCompare;
        final int stableInputMask;
        final int indeterminateInputMask;
        final byte[] serialHistory;
        final long serialStartOffset;
        final long serialEndOffset;
        final boolean serialTruncated;
        final byte[] installedSketchSource;
        final boolean installedSketchSourcePresent;

        Persisted(int schema, UUID boardId, long generation, long revision, byte[] firmware, byte[] firmwareHash,
                   byte[] checkpoint, byte[] checkpointHash, int statusOrdinal, String fault,
                   boolean running, boolean d13High) {
            this(schema, boardId, generation, revision, firmware, firmwareHash, checkpoint, checkpointHash,
                    statusOrdinal, fault, running, d13High, 0, 0, 0, new int[OUTPUT_PIN_COUNT],
                    new int[OUTPUT_PIN_COUNT], new int[OUTPUT_PIN_COUNT], 0, 0, new byte[0]);
        }

        Persisted(int schema, UUID boardId, long generation, long revision, byte[] firmware, byte[] firmwareHash,
                  byte[] checkpoint, byte[] checkpointHash, int statusOrdinal, String fault,
                   boolean running, boolean d13High, int outputMask, int highMask, int pwmMask,
                   int[] pwmMode, int[] pwmPrescaler, int[] pwmCompare, byte[] lastTx) {
            this(schema, boardId, generation, revision, firmware, firmwareHash, checkpoint, checkpointHash,
                    statusOrdinal, fault, running, d13High, outputMask, highMask, pwmMask, pwmMode,
                    pwmPrescaler, pwmCompare, 0, 0, lastTx);
        }

        Persisted(int schema, UUID boardId, long generation, long revision, byte[] firmware, byte[] firmwareHash,
                  byte[] checkpoint, byte[] checkpointHash, int statusOrdinal, String fault,
                  boolean running, boolean d13High, int outputMask, int highMask, int pwmMask,
                  int[] pwmMode, int[] pwmPrescaler, int[] pwmCompare,
                  int stableInputMask, int indeterminateInputMask, byte[] lastTx) {
            this(schema, boardId, generation, revision, firmware, firmwareHash, checkpoint, checkpointHash,
                    statusOrdinal, fault, running, d13High, outputMask, highMask, pwmMask, pwmMode,
                    pwmPrescaler, pwmCompare, stableInputMask, indeterminateInputMask, lastTx,
                    0, lastTx == null ? 0 : lastTx.length, false, new byte[0], false);
        }

        Persisted(int schema, UUID boardId, long generation, long revision, byte[] firmware, byte[] firmwareHash,
                  byte[] checkpoint, byte[] checkpointHash, int statusOrdinal, String fault,
                  boolean running, boolean d13High, int outputMask, int highMask, int pwmMask,
                  int[] pwmMode, int[] pwmPrescaler, int[] pwmCompare,
                  int stableInputMask, int indeterminateInputMask, byte[] serialHistory,
                  long serialStartOffset, long serialEndOffset, boolean serialTruncated,
                  byte[] installedSketchSource, boolean installedSketchSourcePresent) {
            this.schema = schema;
            this.boardId = boardId;
            this.generation = generation;
            this.revision = revision;
            this.firmware = firmware == null ? null : firmware.clone();
            this.firmwareHash = firmwareHash == null ? null : firmwareHash.clone();
            this.checkpoint = checkpoint == null ? null : checkpoint.clone();
            this.checkpointHash = checkpointHash == null ? null : checkpointHash.clone();
            this.statusOrdinal = statusOrdinal;
            this.fault = fault;
            this.running = running;
            this.d13High = d13High;
            this.outputMask = outputMask;
            this.highMask = highMask;
            this.pwmMask = pwmMask;
            this.pwmMode = pwmMode == null ? null : pwmMode.clone();
            this.pwmPrescaler = pwmPrescaler == null ? null : pwmPrescaler.clone();
            this.pwmCompare = pwmCompare == null ? null : pwmCompare.clone();
            this.stableInputMask = stableInputMask;
            this.indeterminateInputMask = indeterminateInputMask;
            this.serialHistory = serialHistory == null ? null : serialHistory.clone();
            this.serialStartOffset = serialStartOffset;
            this.serialEndOffset = serialEndOffset;
            this.serialTruncated = serialTruncated;
            this.installedSketchSource = installedSketchSource == null ? null : installedSketchSource.clone();
            this.installedSketchSourcePresent = installedSketchSourcePresent;
        }
    }
}
