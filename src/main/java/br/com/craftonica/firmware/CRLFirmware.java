package br.com.craftonica.firmware;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class CRLFirmware {
    public static final int VERSION = 1;
    public static final int HEADER_LENGTH = 148;
    public static final int SEGMENT_ENTRY_LENGTH = 16;
    public static final int FLASH_SEGMENT = 1;

    private static final int FIRMWARE_HASH_OFFSET = 116;
    private static final int HASH_LENGTH = 32;
    private static final int PAYLOAD_OFFSET = HEADER_LENGTH + SEGMENT_ENTRY_LENGTH;

    private final byte[] bytes;
    private final byte[] sourceHash;
    private final byte[] payloadHash;
    private final byte[] firmwareHash;
    private final byte[] flash;

    private CRLFirmware(byte[] bytes, byte[] sourceHash, byte[] payloadHash, byte[] firmwareHash, byte[] flash) {
        this.bytes = bytes;
        this.sourceHash = sourceHash;
        this.payloadHash = payloadHash;
        this.firmwareHash = firmwareHash;
        this.flash = flash;
    }

    public static CRLFirmware create(SourceBundle sources, byte[] flash) {
        if (sources == null) throw new IllegalArgumentException("Sources are required");
        return create(sources.getSourceHash(), flash);
    }

    public static CRLFirmware create(byte[] sourceHash, byte[] flash) {
        requireHash(sourceHash, "source hash");
        validateFlash(flash);
        int totalLength = PAYLOAD_OFFSET + flash.length;
        byte[] encoded = new byte[totalLength];
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'C').put((byte) 'R').put((byte) 'L').put((byte) 'F');
        buffer.putShort((short) VERSION).putShort((short) HEADER_LENGTH).putInt(totalLength);
        buffer.putShort((short) 1).putShort((short) 0);
        buffer.putShort((short) ToolchainProfile.INSTRUCTION_ABI).putShort((short) ToolchainProfile.MMIO_ABI);
        buffer.put(ToolchainProfile.profileHash()).put(sourceHash);
        buffer.position(HEADER_LENGTH);
        buffer.put((byte) FLASH_SEGMENT).put((byte) 0).putShort((short) 0);
        buffer.putInt(0).putInt(flash.length).putInt(PAYLOAD_OFFSET);
        buffer.put(flash);

        byte[] payloadHash = FirmwareHashes.sha256(Arrays.copyOfRange(encoded, HEADER_LENGTH, encoded.length));
        System.arraycopy(payloadHash, 0, encoded, 84, HASH_LENGTH);
        byte[] firmwareHash = hashWithZeroFirmwareField(encoded);
        System.arraycopy(firmwareHash, 0, encoded, FIRMWARE_HASH_OFFSET, HASH_LENGTH);
        return new CRLFirmware(encoded, sourceHash.clone(), payloadHash, firmwareHash, flash.clone());
    }

    public static CRLFirmware decode(byte[] encoded) {
        if (encoded == null || encoded.length < PAYLOAD_OFFSET || encoded.length > PAYLOAD_OFFSET + ToolchainProfile.MAX_FLASH_BYTES)
            throw new IllegalArgumentException("Invalid firmware length");
        byte[] copy = encoded.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.get() != 'C' || buffer.get() != 'R' || buffer.get() != 'L' || buffer.get() != 'F')
            throw new IllegalArgumentException("Invalid firmware magic");
        if (u16(buffer.getShort()) != VERSION || u16(buffer.getShort()) != HEADER_LENGTH)
            throw new IllegalArgumentException("Unsupported firmware version or header");
        long totalLength = u32(buffer.getInt());
        if (totalLength != copy.length) throw new IllegalArgumentException("Firmware length mismatch or trailing bytes");
        if (u16(buffer.getShort()) != 1 || u16(buffer.getShort()) != 0)
            throw new IllegalArgumentException("Invalid segment count or reserved header");
        if (u16(buffer.getShort()) != ToolchainProfile.INSTRUCTION_ABI || u16(buffer.getShort()) != ToolchainProfile.MMIO_ABI)
            throw new IllegalArgumentException("Unsupported firmware ABI");

        byte[] profileHash = readHash(buffer);
        byte[] sourceHash = readHash(buffer);
        byte[] payloadHash = readHash(buffer);
        byte[] firmwareHash = readHash(buffer);
        if (!Arrays.equals(profileHash, ToolchainProfile.profileHash())) throw new IllegalArgumentException("Toolchain profile hash mismatch");
        if (buffer.position() != HEADER_LENGTH) throw new IllegalArgumentException("Invalid packed header");

        int type = buffer.get() & 0xff;
        int flags = buffer.get() & 0xff;
        int reserved = u16(buffer.getShort());
        long address = u32(buffer.getInt());
        long flashLength = u32(buffer.getInt());
        long payloadOffset = u32(buffer.getInt());
        if (type != FLASH_SEGMENT || flags != 0 || reserved != 0 || address != 0)
            throw new IllegalArgumentException("Invalid FLASH segment");
        if (payloadOffset != PAYLOAD_OFFSET || (payloadOffset & 3) != 0 || flashLength == 0
                || (flashLength & 1) != 0 || flashLength > ToolchainProfile.MAX_FLASH_BYTES
                || payloadOffset + flashLength != totalLength)
            throw new IllegalArgumentException("Invalid FLASH range or alignment");
        if (!Arrays.equals(payloadHash, FirmwareHashes.sha256(Arrays.copyOfRange(copy, HEADER_LENGTH, copy.length))))
            throw new IllegalArgumentException("Payload hash mismatch");
        if (!Arrays.equals(firmwareHash, hashWithZeroFirmwareField(copy)))
            throw new IllegalArgumentException("Firmware hash mismatch");

        byte[] flash = Arrays.copyOfRange(copy, PAYLOAD_OFFSET, copy.length);
        return new CRLFirmware(copy, sourceHash, payloadHash, firmwareHash, flash);
    }

    public byte[] getBytes() { return bytes.clone(); }
    public byte[] getSourceHash() { return sourceHash.clone(); }
    public byte[] getPayloadHash() { return payloadHash.clone(); }
    public byte[] getFirmwareHash() { return firmwareHash.clone(); }
    public String getFirmwareHashHex() { return FirmwareHashes.hex(firmwareHash); }
    public byte[] getFlash() { return flash.clone(); }

    private static byte[] readHash(ByteBuffer buffer) {
        byte[] value = new byte[HASH_LENGTH];
        buffer.get(value);
        return value;
    }

    private static byte[] hashWithZeroFirmwareField(byte[] encoded) {
        byte[] hashInput = encoded.clone();
        Arrays.fill(hashInput, FIRMWARE_HASH_OFFSET, FIRMWARE_HASH_OFFSET + HASH_LENGTH, (byte) 0);
        return FirmwareHashes.sha256(hashInput);
    }

    private static int u16(short value) { return value & 0xffff; }
    private static long u32(int value) { return value & 0xffffffffL; }

    private static void requireHash(byte[] value, String name) {
        if (value == null || value.length != HASH_LENGTH) throw new IllegalArgumentException("Invalid " + name);
    }

    private static void validateFlash(byte[] flash) {
        if (flash == null || flash.length == 0 || flash.length > ToolchainProfile.MAX_FLASH_BYTES || (flash.length & 1) != 0)
            throw new IllegalArgumentException("FLASH must be non-empty, even and at most 32256 bytes");
    }
}
