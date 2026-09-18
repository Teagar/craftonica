package br.com.craftonica.sketch.network;

import br.com.craftonica.firmware.CompilationDiagnostics;
import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.tile.RoboBoardState;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Bounded authoritative state for the implicit, fixed Sketch.ino editor. */
public final class EditorStateMessage implements IMessage {
    public static final int MAX_STATE_CODE_BYTES = 48;
    public static final int MAX_PACKET_BYTES = 64 * 1024;

    private int dimension, x, y, z;
    private UUID boardId;
    private long generation, revision;
    private int status;
    private String fault = "", compilationState = "", diagnostics = "";
    private byte[] source = new byte[0], serial = new byte[0];
    private long serialStart, serialEnd;
    private boolean serialTruncated;
    private boolean valid;

    public EditorStateMessage() {}

    public EditorStateMessage(int dimension, int x, int y, int z, UUID boardId, long generation, long revision,
                              int status, String fault, byte[] source, String compilationState,
                              String diagnostics, byte[] serial, long serialStart, long serialEnd,
                              boolean serialTruncated) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.boardId = boardId;
        this.generation = generation;
        this.revision = revision;
        this.status = status;
        this.fault = fault == null ? "" : fault;
        this.source = source == null ? new byte[0] : source.clone();
        this.compilationState = compilationState == null ? "" : compilationState;
        this.diagnostics = sanitizeDiagnostics(diagnostics);
        this.serial = serial == null ? new byte[0] : serial.clone();
        this.serialStart = serialStart;
        this.serialEnd = serialEnd;
        this.serialTruncated = serialTruncated;
        validate();
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        source = serial = new byte[0];
        try {
            if (buffer.readableBytes() > MAX_PACKET_BYTES) throw new IllegalArgumentException("State packet too large");
            PacketCodec.require(buffer, 50);
            dimension = buffer.readInt(); x = buffer.readInt(); y = buffer.readInt(); z = buffer.readInt();
            boardId = new UUID(buffer.readLong(), buffer.readLong());
            generation = buffer.readLong(); revision = buffer.readLong(); status = buffer.readUnsignedByte();
            fault = PacketCodec.readUtf8(buffer, RoboBoardState.MAX_FAULT_BYTES);
            source = PacketCodec.readBytes(buffer, SourceBundle.MAX_FILE_BYTES);
            compilationState = PacketCodec.readUtf8(buffer, MAX_STATE_CODE_BYTES);
            diagnostics = PacketCodec.readUtf8(buffer, CompilationDiagnostics.MAX_UTF8_BYTES);
            serial = PacketCodec.readBytes(buffer, RoboBoardState.MAX_SERIAL_HISTORY_BYTES);
            PacketCodec.require(buffer, 17);
            serialStart = buffer.readLong(); serialEnd = buffer.readLong(); serialTruncated = buffer.readBoolean();
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing state bytes");
            validate();
            valid = true;
        } catch (RuntimeException rejected) {
            valid = false;
            source = serial = new byte[0];
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) throw new IllegalStateException("Cannot encode invalid editor state");
        buffer.writeInt(dimension).writeInt(x).writeInt(y).writeInt(z);
        buffer.writeLong(boardId.getMostSignificantBits()).writeLong(boardId.getLeastSignificantBits());
        buffer.writeLong(generation).writeLong(revision).writeByte(status);
        PacketCodec.writeUtf8(buffer, fault);
        PacketCodec.writeBytes(buffer, source);
        PacketCodec.writeUtf8(buffer, compilationState);
        PacketCodec.writeUtf8(buffer, diagnostics);
        PacketCodec.writeBytes(buffer, serial);
        buffer.writeLong(serialStart).writeLong(serialEnd).writeBoolean(serialTruncated);
        if (buffer.writerIndex() > MAX_PACKET_BYTES) throw new IllegalStateException("Encoded state exceeds packet bound");
    }

    private void validate() {
        if (boardId == null || generation < 0 || revision < 0 || status < 0
                || status >= RoboBoardState.Status.values().length)
            throw new IllegalArgumentException("Invalid editor state identity");
        requireUtf8Bound(fault, RoboBoardState.MAX_FAULT_BYTES);
        requireUtf8Bound(compilationState, MAX_STATE_CODE_BYTES);
        requireUtf8Bound(diagnostics, CompilationDiagnostics.MAX_UTF8_BYTES);
        requirePrintableAscii(fault, false);
        requirePrintableAscii(compilationState, true);
        if (PacketCodec.containsNul(source)) throw new IllegalArgumentException("Sketch source contains NUL");
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(source));
        } catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IllegalArgumentException("Sketch source is not strict UTF-8", malformed);
        }
        if (source.length > SourceBundle.MAX_FILE_BYTES || serial.length > RoboBoardState.MAX_SERIAL_HISTORY_BYTES
                || serialStart < 0 || serialEnd < serialStart || serialEnd - serialStart != serial.length
                || serialTruncated != (serialStart > 0))
            throw new IllegalArgumentException("Invalid bounded editor payload");
    }

    private static String sanitizeDiagnostics(String value) {
        if (value == null || value.isEmpty()) return "";
        List<String> entries = CompilationDiagnostics.sanitize(Arrays.asList(value.split("\\r?\\n", -1))).getEntries();
        StringBuilder clean = new StringBuilder();
        for (String entry : entries) {
            if (clean.length() > 0) clean.append('\n');
            clean.append(entry);
        }
        return clean.toString();
    }

    private static void requireUtf8Bound(String value, int maximum) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > maximum)
            throw new IllegalArgumentException("Text field exceeds packet bound");
    }

    private static void requirePrintableAscii(String value, boolean localizationCode) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 || character > 0x7e
                    || localizationCode && !(Character.isLetterOrDigit(character)
                    || character == '.' || character == '_' || character == '-'))
                throw new IllegalArgumentException("Invalid state text");
        }
    }

    public boolean isValid() { return valid; }
    public int getDimension() { return dimension; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public UUID getBoardId() { return boardId; }
    public long getGeneration() { return generation; }
    public long getRevision() { return revision; }
    public int getStatus() { return status; }
    public String getFault() { return fault; }
    public byte[] getSource() { return source.clone(); }
    public String getCompilationState() { return compilationState; }
    public String getDiagnostics() { return diagnostics; }
    public byte[] getSerial() { return serial.clone(); }
    public long getSerialStart() { return serialStart; }
    public long getSerialEnd() { return serialEnd; }
    public boolean isSerialTruncated() { return serialTruncated; }
}
