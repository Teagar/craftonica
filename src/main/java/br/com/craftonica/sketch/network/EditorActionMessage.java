package br.com.craftonica.sketch.network;

import br.com.craftonica.firmware.SourceBundle;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The complete client authority surface: identity, optimistic revision, action, and fixed sketch bytes. */
public final class EditorActionMessage implements IMessage {
    public static final int MAX_PACKET_BYTES = 4 * 4 + 16 + 8 + 8 + 1 + 4 + SourceBundle.MAX_FILE_BYTES;

    private int dimension, x, y, z;
    private UUID boardId;
    private long generation, expectedRevision;
    private SketchAction action;
    private byte[] source = new byte[0];
    private boolean valid;

    public EditorActionMessage() {}

    public EditorActionMessage(int dimension, int x, int y, int z, UUID boardId, long generation,
                               long expectedRevision, SketchAction action, byte[] source) {
        if (boardId == null || generation < 0 || expectedRevision < 0 || action == null)
            throw new IllegalArgumentException("Invalid board action identity");
        byte[] safeSource = source == null ? new byte[0] : source.clone();
        validateSource(action, safeSource);
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.boardId = boardId;
        this.generation = generation;
        this.expectedRevision = expectedRevision;
        this.action = action;
        this.source = safeSource;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        source = new byte[0];
        try {
            if (buffer.readableBytes() > MAX_PACKET_BYTES) throw new IllegalArgumentException("Action packet too large");
            PacketCodec.require(buffer, 49);
            dimension = buffer.readInt();
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            boardId = new UUID(buffer.readLong(), buffer.readLong());
            generation = buffer.readLong();
            expectedRevision = buffer.readLong();
            int actionCode = buffer.readUnsignedByte();
            if (generation < 0 || expectedRevision < 0 || actionCode >= SketchAction.values().length)
                throw new IllegalArgumentException("Invalid action fields");
            action = SketchAction.values()[actionCode];
            source = PacketCodec.readBytes(buffer, SourceBundle.MAX_FILE_BYTES);
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing action bytes");
            validateSource(action, source);
            valid = true;
        } catch (RuntimeException rejected) {
            valid = false;
            source = new byte[0];
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) throw new IllegalStateException("Cannot encode an invalid action");
        buffer.writeInt(dimension).writeInt(x).writeInt(y).writeInt(z);
        buffer.writeLong(boardId.getMostSignificantBits()).writeLong(boardId.getLeastSignificantBits());
        buffer.writeLong(generation).writeLong(expectedRevision).writeByte(action.ordinal());
        PacketCodec.writeBytes(buffer, source);
    }

    private static void validateSource(SketchAction action, byte[] source) {
        if (source.length > SourceBundle.MAX_FILE_BYTES || action != SketchAction.COMPILE && source.length != 0)
            throw new IllegalArgumentException("Source is accepted only for COMPILE");
        if (action == SketchAction.COMPILE) {
            if (PacketCodec.containsNul(source)) throw new IllegalArgumentException("Sketch contains NUL");
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source));
            } catch (CharacterCodingException malformed) {
                throw new IllegalArgumentException("Sketch is not strict UTF-8", malformed);
            }
        }
    }

    public boolean isValid() { return valid; }
    public int getDimension() { return dimension; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public UUID getBoardId() { return boardId; }
    public long getGeneration() { return generation; }
    public long getExpectedRevision() { return expectedRevision; }
    public SketchAction getAction() { return action; }
    public byte[] getSource() { return source.clone(); }
}
