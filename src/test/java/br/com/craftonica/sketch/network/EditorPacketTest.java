package br.com.craftonica.sketch.network;

import br.com.craftonica.firmware.SourceBundle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EditorPacketTest {
    @Test
    public void actionRoundTripContainsOnlyTheFixedAuthoritySurface() {
        byte[] source = "void setup(){}\nvoid loop(){}".getBytes(StandardCharsets.UTF_8);
        EditorActionMessage sent = new EditorActionMessage(2, 1, 64, -3, UUID.randomUUID(), 4, 9,
                SketchAction.COMPILE, source);
        ByteBuf bytes = Unpooled.buffer();
        sent.toBytes(bytes);
        EditorActionMessage received = new EditorActionMessage();
        received.fromBytes(bytes);
        assertTrue(received.isValid());
        assertArrayEquals(source, received.getSource());

        Set<String> forbidden = new HashSet<String>(Arrays.asList("path", "filename", "profile", "firmware",
                "elf", "hex", "owner", "hash", "checkpoint", "filesystem"));
        for (Field field : EditorActionMessage.class.getDeclaredFields())
            assertFalse(forbidden.contains(field.getName().toLowerCase()));
    }

    @Test
    public void rejectsOversizeMalformedUtf8NulAndTrailingBytes() {
        assertRejected(rawCompile(SourceBundle.MAX_FILE_BYTES + 1, null, false));
        assertRejected(rawCompile(2, new byte[] {(byte) 0xc3, 0x28}, false));
        assertRejected(rawCompile(1, new byte[] {0}, false));
        assertRejected(rawCompile(0, new byte[0], true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonCompileActionCannotCarrySource() {
        new EditorActionMessage(0, 0, 0, 0, UUID.randomUUID(), 0, 0, SketchAction.START,
                new byte[] {1});
    }

    @Test
    public void stateRoundTripIsBoundedAndRejectsTrailingBytes() {
        byte[] source = new byte[SourceBundle.MAX_FILE_BYTES];
        Arrays.fill(source, (byte) 'a');
        byte[] serial = new byte[8192];
        EditorStateMessage sent = new EditorStateMessage(0, 1, 2, 3, UUID.randomUUID(), 4, 5, 1,
                "", source, "craftonica.editor.ready", "/secret/build/Sketch.ino: error", serial,
                8, 8 + serial.length, true);
        ByteBuf encoded = Unpooled.buffer();
        sent.toBytes(encoded);
        assertTrue(encoded.readableBytes() <= EditorStateMessage.MAX_PACKET_BYTES);
        EditorStateMessage received = new EditorStateMessage();
        received.fromBytes(encoded.copy());
        assertTrue(received.isValid());
        assertFalse(received.getDiagnostics().contains("/secret"));

        encoded.writeByte(1);
        EditorStateMessage trailing = new EditorStateMessage();
        trailing.fromBytes(encoded);
        assertFalse(trailing.isValid());
    }

    @Test
    public void rejectsStateBeforeParsingWhenTotalPacketExceedsMaximum() {
        EditorStateMessage received = new EditorStateMessage();
        received.fromBytes(Unpooled.buffer(EditorStateMessage.MAX_PACKET_BYTES + 1)
                .writeZero(EditorStateMessage.MAX_PACKET_BYTES + 1));
        assertFalse(received.isValid());
    }

    private static ByteBuf rawCompile(int declaredLength, byte[] source, boolean trailing) {
        ByteBuf value = Unpooled.buffer();
        value.writeInt(0).writeInt(1).writeInt(2).writeInt(3);
        value.writeLong(1).writeLong(2).writeLong(0).writeLong(0);
        value.writeByte(SketchAction.COMPILE.ordinal()).writeInt(declaredLength);
        if (source != null) value.writeBytes(source);
        if (trailing) value.writeByte(7);
        return value;
    }

    private static void assertRejected(ByteBuf bytes) {
        EditorActionMessage message = new EditorActionMessage();
        message.fromBytes(bytes);
        assertFalse(message.isValid());
    }
}
