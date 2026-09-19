package br.com.craftonica.tool.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ToolPacketTest {
    @Test
    public void actionRoundTripIsFixedAndRejectsTrailingBytes() {
        ToolActionMessage sent = new ToolActionMessage(ToolAction.ROBOPORT_ROLE, 14, 2, 64, -3, 7);
        ByteBuf encoded = Unpooled.buffer();
        sent.toBytes(encoded);
        assertEquals(25, encoded.readableBytes());
        ToolActionMessage received = new ToolActionMessage();
        received.fromBytes(encoded.copy());
        assertTrue(received.isValid());
        assertEquals(ToolAction.ROBOPORT_ROLE, received.getAction());
        assertEquals(14, received.getValue());
        assertEquals(7, received.getRevision());

        encoded.writeByte(1);
        ToolActionMessage trailing = new ToolActionMessage();
        trailing.fromBytes(encoded);
        assertFalse(trailing.isValid());
    }

    @Test
    public void multimeterStateRoundTripKeepsOnlyDisplayAndProbeState() {
        ToolStateMessage sent = ToolStateMessage.multimeter(2, 0,
                true, 1, 2, 3, 4, true, 5, 6, 7, 2,
                "message.craftonica.multimeter.closed", "reading|13.64");
        ByteBuf encoded = Unpooled.buffer();
        sent.toBytes(encoded);
        ToolStateMessage received = new ToolStateMessage();
        received.fromBytes(encoded);
        assertTrue(received.isValid());
        assertEquals(ToolStateMessage.MULTIMETER, received.getType());
        assertTrue(received.isFirstSet());
        assertTrue(received.isSecondSet());
        assertEquals(4, received.getFirstSide());
        assertEquals("reading|13.64", received.getDetail());
    }

    @Test
    public void stateRejectsTrailingAndOversizePayloads() {
        ToolStateMessage sent = ToolStateMessage.roboPort(0, true, 1, 2, 3,
                true, 4, 5, 6, 20, 9, "ready", "");
        ByteBuf encoded = Unpooled.buffer();
        sent.toBytes(encoded);
        encoded.writeByte(1);
        ToolStateMessage trailing = new ToolStateMessage();
        trailing.fromBytes(encoded);
        assertFalse(trailing.isValid());

        ToolStateMessage oversized = new ToolStateMessage();
        oversized.fromBytes(Unpooled.buffer(513).writeZero(513));
        assertFalse(oversized.isValid());
    }
}
