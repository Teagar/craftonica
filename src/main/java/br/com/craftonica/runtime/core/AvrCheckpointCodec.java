package br.com.craftonica.runtime.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Canonical, versioned checkpoint with an embedded SHA-256 checksum. */
public final class AvrCheckpointCodec {
    private static final byte[] MAGIC = { 'C', 'R', 'L', 'C' };
    private static final int VERSION = 2;
    private static final int HEADER_SIZE = 16;
    private static final int HASH_SIZE = 32;
    private static final int LEGACY_PAYLOAD_SIZE = 32 + 1 + 2 + 4 + 8
            + AvrMachineState.SRAM_SIZE + AvrMachineState.EEPROM_SIZE + 0xe0
            + 4 + 4 + 4 + 1 + 8 + 4;
    private static final int LEGACY_ENCODED_SIZE = HEADER_SIZE + LEGACY_PAYLOAD_SIZE + HASH_SIZE;
    private static final int PAYLOAD_SIZE = LEGACY_PAYLOAD_SIZE + 4 + 4 + 1 + 8 + 8 + 8;
    public static final int ENCODED_SIZE = HEADER_SIZE + PAYLOAD_SIZE + HASH_SIZE;

    private AvrCheckpointCodec() {}

    public static byte[] encode(AvrMachineState state) {
        if (state == null) {
            throw new NullPointerException("state");
        }
        ByteBuffer out = ByteBuffer.allocate(ENCODED_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        out.put(MAGIC).putShort((short) VERSION).putShort((short) HEADER_SIZE);
        out.putInt(ENCODED_SIZE).putInt(PAYLOAD_SIZE);
        out.put(state.registers).put((byte) state.sreg).putShort((short) state.stackPointer);
        out.putInt(state.wordPc).putLong(state.cycles);
        out.put(state.sram).put(state.eeprom).put(state.mmio);
        out.putInt(state.timer0PrescaleCycles).putInt(state.timer1PrescaleCycles);
        out.putInt(state.timer2PrescaleCycles).put((byte) (state.adcRunning ? 1 : 0));
        out.putLong(state.adcCompleteCycle).putInt(state.adcSample);
        out.putInt(state.ultrasonicTriggerPin).putInt(state.ultrasonicEchoPin);
        out.put((byte) (state.ultrasonicTriggerHigh ? 1 : 0));
        out.putLong(state.ultrasonicTriggerRiseCycle).putLong(state.ultrasonicEchoStartCycle)
                .putLong(state.ultrasonicEchoEndCycle);
        byte[] bytes = out.array();
        byte[] hash = sha256(bytes, 0, ENCODED_SIZE - HASH_SIZE);
        System.arraycopy(hash, 0, bytes, ENCODED_SIZE - HASH_SIZE, HASH_SIZE);
        return bytes;
    }

    public static AvrMachineState decode(byte[] encoded) throws AvrFault {
        if (encoded == null || (encoded.length != ENCODED_SIZE && encoded.length != LEGACY_ENCODED_SIZE)) {
            throw incompatible("checkpoint length is not canonical");
        }
        ByteBuffer in = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        in.get(magic);
        int version = in.getShort() & 0xffff;
        int headerSize = in.getShort() & 0xffff;
        int totalSize = in.getInt();
        int payloadSize = in.getInt();
        boolean legacy = version == 1 && encoded.length == LEGACY_ENCODED_SIZE
                && totalSize == LEGACY_ENCODED_SIZE && payloadSize == LEGACY_PAYLOAD_SIZE;
        boolean current = version == VERSION && encoded.length == ENCODED_SIZE
                && totalSize == ENCODED_SIZE && payloadSize == PAYLOAD_SIZE;
        if (!Arrays.equals(MAGIC, magic) || headerSize != HEADER_SIZE || (!legacy && !current)) {
            throw incompatible("unknown or malformed checkpoint header");
        }
        byte[] expected = sha256(encoded, 0, encoded.length - HASH_SIZE);
        byte[] actual = Arrays.copyOfRange(encoded, encoded.length - HASH_SIZE, encoded.length);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw incompatible("checkpoint checksum mismatch");
        }
        AvrMachineState state = new AvrMachineState();
        in.get(state.registers);
        state.sreg = in.get() & 0xff;
        state.stackPointer = in.getShort() & 0xffff;
        state.wordPc = in.getInt();
        state.cycles = in.getLong();
        in.get(state.sram).get(state.eeprom).get(state.mmio);
        state.timer0PrescaleCycles = in.getInt();
        state.timer1PrescaleCycles = in.getInt();
        state.timer2PrescaleCycles = in.getInt();
        int adcRunning = in.get() & 0xff;
        state.adcCompleteCycle = in.getLong();
        state.adcSample = in.getInt();
        int ultrasonicHigh = 0;
        if (current) {
            state.ultrasonicTriggerPin = in.getInt();
            state.ultrasonicEchoPin = in.getInt();
            ultrasonicHigh = in.get() & 0xff;
            state.ultrasonicTriggerRiseCycle = in.getLong();
            state.ultrasonicEchoStartCycle = in.getLong();
            state.ultrasonicEchoEndCycle = in.getLong();
        }
        if (state.stackPointer < 0x100 || state.stackPointer > AvrMachineState.RAMEND
                || state.wordPc < 0 || state.cycles < 0 || adcRunning > 1
                || state.adcSample < 0 || state.adcSample > 1023
                || state.timer0PrescaleCycles < 0 || state.timer1PrescaleCycles < 0
                || state.timer2PrescaleCycles < 0 || ultrasonicHigh > 1
                || !validPinPair(state.ultrasonicTriggerPin, state.ultrasonicEchoPin)
                || state.ultrasonicTriggerRiseCycle < -1L || state.ultrasonicEchoStartCycle < -1L
                || state.ultrasonicEchoEndCycle < -1L
                || (state.ultrasonicEchoStartCycle >= 0L
                    && state.ultrasonicEchoEndCycle < state.ultrasonicEchoStartCycle)) {
            throw incompatible("checkpoint state is out of range");
        }
        state.adcRunning = adcRunning != 0;
        state.ultrasonicTriggerHigh = ultrasonicHigh != 0;
        return state;
    }

    public static boolean isSupportedLength(int length) {
        return length == ENCODED_SIZE || length == LEGACY_ENCODED_SIZE;
    }

    private static boolean validPinPair(int trigger, int echo) {
        if (trigger == -1 && echo == -1) return true;
        return trigger >= 0 && trigger < AvrInputs.DIGITAL_PIN_COUNT
                && echo >= 0 && echo < AvrInputs.DIGITAL_PIN_COUNT && trigger != echo;
    }

    public static byte[] sha256(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        return sha256(bytes, 0, bytes.length);
    }

    private static byte[] sha256(byte[] bytes, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static AvrFault incompatible(String message) {
        return new AvrFault(AvrFault.Code.CHECKPOINT_INCOMPATIBLE, message, 0, 0);
    }
}
