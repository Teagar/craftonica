package br.com.craftonica.sketch.network;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class PacketCodec {
    private PacketCodec() {}

    static void require(ByteBuf buffer, int bytes) {
        if (bytes < 0 || buffer.readableBytes() < bytes) throw new IllegalArgumentException("Truncated packet");
    }

    static byte[] readBytes(ByteBuf buffer, int maximum) {
        require(buffer, 4);
        int length = buffer.readInt();
        if (length < 0 || length > maximum || buffer.readableBytes() < length)
            throw new IllegalArgumentException("Invalid bounded byte length");
        byte[] value = new byte[length];
        buffer.readBytes(value);
        return value;
    }

    static String readUtf8(ByteBuf buffer, int maximum) {
        byte[] bytes = readBytes(buffer, maximum);
        try {
            CharBuffer value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return value.toString();
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Malformed UTF-8", malformed);
        }
    }

    static void writeBytes(ByteBuf buffer, byte[] value) {
        buffer.writeInt(value.length);
        buffer.writeBytes(value);
    }

    static void writeUtf8(ByteBuf buffer, String value) {
        writeBytes(buffer, value.getBytes(StandardCharsets.UTF_8));
    }

    static boolean containsNul(byte[] value) {
        for (byte current : value) if (current == 0) return true;
        return false;
    }
}
