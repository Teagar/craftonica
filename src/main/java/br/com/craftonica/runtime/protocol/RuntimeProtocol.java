package br.com.craftonica.runtime.protocol;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrExecutionResult;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.core.GpioChange;
import br.com.craftonica.runtime.core.PwmDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Length-delimited worker protocol. Every frame is authenticated before its payload is decoded. */
public final class RuntimeProtocol {
    public static final int MAX_FRAME_BYTES = 131072;
    public static final int MAX_FAULT_BYTES = 512;
    private static final int MAGIC = 0x43524c52; // CRLR
    private static final int VERSION = 1;
    private static final int REQUEST = 1;
    private static final int RESULT = 2;
    private static final int HASH_BYTES = 32;

    private RuntimeProtocol() {}

    public static void writeRequest(OutputStream output, Request request) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeIdentity(out, request.identity);
        out.writeLong(request.absoluteTarget);
        writeBytes(out, request.firmware);
        writeBytes(out, request.checkpoint);
        boolean[] digital = request.inputs.getDigitalPins();
        int[] analog = request.inputs.getAnalogMicrovolts();
        for (boolean value : digital) out.writeBoolean(value);
        for (int value : analog) out.writeInt(value);
        writeFrame(output, REQUEST, bytes.toByteArray());
    }

    public static Request readRequest(InputStream input) throws IOException, AvrFault {
        DataInputStream in = payload(readFrame(input, REQUEST));
        Identity identity = readIdentity(in);
        long target = in.readLong();
        byte[] firmwareBytes = readBytes(in, CRLFirmware.HEADER_LENGTH + CRLFirmware.SEGMENT_ENTRY_LENGTH + 32256);
        byte[] checkpoint = readBytes(in, AvrCheckpointCodec.ENCODED_SIZE);
        if (checkpoint.length != AvrCheckpointCodec.ENCODED_SIZE) throw new ProtocolException("non-canonical checkpoint length");
        boolean[] digital = new boolean[AvrInputs.DIGITAL_PIN_COUNT];
        int[] analog = new int[AvrInputs.ANALOG_CHANNEL_COUNT];
        for (int i = 0; i < digital.length; i++) digital[i] = in.readBoolean();
        for (int i = 0; i < analog.length; i++) analog[i] = in.readInt();
        requireEnd(in);
        CRLFirmware firmware = CRLFirmware.decode(firmwareBytes);
        AvrMachineState state = AvrCheckpointCodec.decode(checkpoint);
        long delta = target - state.getCycles();
        if (delta < 0 || delta > 50000) throw new ProtocolException("target exceeds one absolute cycle quantum");
        return new Request(identity, target, firmware.getBytes(), checkpoint, new AvrInputs(digital, analog));
    }

    public static void writeResult(OutputStream output, Result result) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeIdentity(out, result.identity);
        out.writeLong(result.completedAtCycle);
        out.writeBoolean(result.d13High);
        writeBytes(out, result.checkpoint);
        out.writeShort(result.gpio.size());
        for (Gpio value : result.gpio) {
            out.writeLong(value.cycle); out.writeByte(value.pin); out.writeBoolean(value.output); out.writeBoolean(value.high);
        }
        out.writeShort(result.pwm.size());
        for (Pwm value : result.pwm) {
            out.writeLong(value.cycle); out.writeByte(value.pin); out.writeByte(value.timer); out.writeByte(value.mode);
            out.writeInt(value.prescaler); out.writeInt(value.compare); out.writeBoolean(value.phaseCorrect);
        }
        writeBytes(out, result.tx);
        out.writeBoolean(result.fault != null);
        if (result.fault != null) {
            out.writeByte(result.fault.code);
            out.writeLong(result.fault.cycle);
            out.writeInt(result.fault.wordPc);
            writeBytes(out, result.fault.message.getBytes(StandardCharsets.UTF_8));
        }
        writeFrame(output, RESULT, bytes.toByteArray());
    }

    public static Result readResult(InputStream input) throws IOException {
        DataInputStream in = payload(readFrame(input, RESULT));
        Identity identity = readIdentity(in);
        long completedAt = in.readLong();
        boolean d13 = in.readBoolean();
        byte[] checkpoint = readBytes(in, AvrCheckpointCodec.ENCODED_SIZE);
        if (checkpoint.length != AvrCheckpointCodec.ENCODED_SIZE) throw new ProtocolException("non-canonical result checkpoint");
        try {
            AvrMachineState decoded = AvrCheckpointCodec.decode(checkpoint);
            if (decoded.getCycles() != completedAt) throw new ProtocolException("result cycle does not match checkpoint");
        } catch (AvrFault invalid) {
            throw new ProtocolException("result checkpoint verification failed");
        }
        int gpioCount = in.readUnsignedShort();
        if (gpioCount > 64) throw new ProtocolException("GPIO result limit exceeded");
        List<Gpio> gpio = new ArrayList<Gpio>(gpioCount);
        for (int i = 0; i < gpioCount; i++) gpio.add(new Gpio(in.readLong(), in.readUnsignedByte(), in.readBoolean(), in.readBoolean()));
        int pwmCount = in.readUnsignedShort();
        if (gpioCount + pwmCount > 64) throw new ProtocolException("effective change limit exceeded");
        List<Pwm> pwm = new ArrayList<Pwm>(pwmCount);
        for (int i = 0; i < pwmCount; i++) pwm.add(new Pwm(in.readLong(), in.readUnsignedByte(), in.readUnsignedByte(),
                in.readUnsignedByte(), in.readInt(), in.readInt(), in.readBoolean()));
        byte[] tx = readBytes(in, 256);
        Fault fault = null;
        if (in.readBoolean()) {
            int code = in.readUnsignedByte();
            long cycle = in.readLong();
            int pc = in.readInt();
            byte[] message = readBytes(in, MAX_FAULT_BYTES);
            fault = new Fault(code, cycle, pc, new String(message, StandardCharsets.UTF_8));
        }
        requireEnd(in);
        return new Result(identity, completedAt, d13, checkpoint, gpio, pwm, tx, fault);
    }

    public static Result fromExecution(Identity identity, AvrMachineState state, AvrExecutionResult execution, AvrFault fault) {
        List<Gpio> gpio = new ArrayList<Gpio>();
        List<Pwm> pwm = new ArrayList<Pwm>();
        byte[] tx = new byte[0];
        long completedAt = state.getCycles();
        if (execution != null) {
            for (GpioChange value : execution.getGpioChanges()) gpio.add(new Gpio(value.getCycle(), value.getPin(), value.isOutput(), value.isHigh()));
            for (PwmDescriptor value : execution.getPwmDescriptors()) pwm.add(new Pwm(value.getCycle(), value.getPin(), value.getTimer(), value.getMode(), value.getPrescaler(), value.getCompare(), value.isPhaseCorrect()));
            tx = execution.getTransmittedBytes();
            completedAt = execution.getCompletedAtCycle();
        }
        Fault encodedFault = fault == null ? null : new Fault(fault.getCode().ordinal(), fault.getCycle(), fault.getWordPc(), bounded(fault.getMessage()));
        boolean d13 = (state.getMmio(0x24) & 0x20) != 0 && (state.getMmio(0x25) & 0x20) != 0;
        return new Result(identity, completedAt, d13, AvrCheckpointCodec.encode(state), gpio, pwm, tx, encodedFault);
    }

    private static byte[] readFrame(InputStream input, int expectedType) throws IOException {
        DataInputStream in = new DataInputStream(input);
        int first = in.read();
        if (first < 0) throw new EndOfStreamException();
        int magic = (first << 24) | (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
        int version = in.readUnsignedShort();
        int type = in.readUnsignedByte();
        int reserved = in.readUnsignedByte();
        int length = in.readInt();
        if (magic != MAGIC || version != VERSION || type != expectedType || reserved != 0 || length < 0 || length > MAX_FRAME_BYTES)
            throw new ProtocolException(String.format("invalid frame header magic=%08x version=%d type=%d reserved=%d length=%d",
                    magic, version, type, reserved, length));
        byte[] expectedHash = new byte[HASH_BYTES];
        in.readFully(expectedHash);
        byte[] payload = new byte[length];
        in.readFully(payload);
        if (!MessageDigest.isEqual(expectedHash, sha256(type, payload))) throw new ProtocolException("frame checksum mismatch");
        return payload;
    }

    private static void writeFrame(OutputStream output, int type, byte[] payload) throws IOException {
        if (payload.length > MAX_FRAME_BYTES) throw new ProtocolException("frame is too large");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC); out.writeShort(VERSION); out.writeByte(type); out.writeByte(0); out.writeInt(payload.length);
        out.write(sha256(type, payload)); out.write(payload); out.flush();
    }

    private static byte[] sha256(int type, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) VERSION); digest.update((byte) type); digest.update(payload);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static DataInputStream payload(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static void requireEnd(DataInputStream in) throws IOException { if (in.read() != -1) throw new ProtocolException("trailing payload bytes"); }
    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException { out.writeInt(value.length); out.write(value); }
    private static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max) throw new ProtocolException("byte field exceeds limit");
        byte[] value = new byte[length]; in.readFully(value); return value;
    }
    private static void writeIdentity(DataOutputStream out, Identity value) throws IOException {
        out.writeInt(value.dimension); out.writeInt(value.x); out.writeInt(value.y); out.writeInt(value.z); out.writeLong(value.generation);
    }
    private static Identity readIdentity(DataInputStream in) throws IOException {
        return new Identity(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readLong());
    }
    private static String bounded(String value) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_FAULT_BYTES) return value;
        return new String(bytes, 0, MAX_FAULT_BYTES, StandardCharsets.UTF_8);
    }

    public static final class ProtocolException extends IOException { public ProtocolException(String message) { super(message); } }
    public static final class EndOfStreamException extends EOFException { private EndOfStreamException() { super("clean protocol EOF"); } }

    public static final class Identity {
        public final int dimension, x, y, z;
        public final long generation;
        public Identity(int dimension, int x, int y, int z, long generation) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.generation = generation;
        }
        public String key() { return dimension + ":" + x + ":" + y + ":" + z; }
    }

    public static final class Request {
        public final Identity identity;
        public final long absoluteTarget;
        public final byte[] firmware, checkpoint;
        public final AvrInputs inputs;
        public Request(Identity identity, long absoluteTarget, byte[] firmware, byte[] checkpoint, AvrInputs inputs) {
            if (identity == null || firmware == null || checkpoint == null || inputs == null) throw new NullPointerException();
            this.identity = identity; this.absoluteTarget = absoluteTarget; this.firmware = firmware.clone();
            this.checkpoint = checkpoint.clone(); this.inputs = inputs;
        }
    }

    public static final class Result {
        public final Identity identity;
        public final long completedAtCycle;
        public final boolean d13High;
        public final byte[] checkpoint, tx;
        public final List<Gpio> gpio;
        public final List<Pwm> pwm;
        public final Fault fault;
        public Result(Identity identity, long completedAtCycle, boolean d13High, byte[] checkpoint,
                      List<Gpio> gpio, List<Pwm> pwm, byte[] tx, Fault fault) {
            this.identity = identity; this.completedAtCycle = completedAtCycle; this.d13High = d13High;
            this.checkpoint = checkpoint.clone(); this.gpio = java.util.Collections.unmodifiableList(new ArrayList<Gpio>(gpio));
            this.pwm = java.util.Collections.unmodifiableList(new ArrayList<Pwm>(pwm)); this.tx = tx.clone(); this.fault = fault;
        }
    }

    public static final class Gpio {
        public final long cycle; public final int pin; public final boolean output, high;
        public Gpio(long cycle, int pin, boolean output, boolean high) { this.cycle = cycle; this.pin = pin; this.output = output; this.high = high; }
    }
    public static final class Pwm {
        public final long cycle; public final int pin, timer, mode, prescaler, compare; public final boolean phaseCorrect;
        public Pwm(long cycle, int pin, int timer, int mode, int prescaler, int compare, boolean phaseCorrect) {
            this.cycle = cycle; this.pin = pin; this.timer = timer; this.mode = mode; this.prescaler = prescaler; this.compare = compare; this.phaseCorrect = phaseCorrect;
        }
    }
    public static final class Fault {
        public final int code; public final long cycle; public final int wordPc; public final String message;
        public Fault(int code, long cycle, int wordPc, String message) { this.code = code; this.cycle = cycle; this.wordPc = wordPc; this.message = message; }
    }
}
