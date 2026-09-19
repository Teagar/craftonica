package br.com.craftonica.runtime.protocol;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.AvrMachineState;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RuntimeProtocolTest {
    @Test
    public void requestRoundTripsOnlyAfterFirmwareAndCheckpointVerification() throws Exception {
        RuntimeProtocol.Request request = request(3);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        RuntimeProtocol.writeRequest(encoded, request);
        RuntimeProtocol.Request decoded = RuntimeProtocol.readRequest(new ByteArrayInputStream(encoded.toByteArray()));
        assertEquals(3, decoded.absoluteTarget);
        assertEquals(9, decoded.identity.generation);
    }

    @Test
    public void malformedAndOversizedFramesAreRejectedBeforeAllocation() throws Exception {
        byte[] malformed = new byte[] { 'B', 'A', 'D', '!', 0, 1, 1, 0, 127, -1, -1, -1 };
        try {
            RuntimeProtocol.readRequest(new ByteArrayInputStream(malformed));
            fail("expected malformed frame rejection");
        } catch (RuntimeProtocol.ProtocolException expected) {
            assertTrue(expected.getMessage().startsWith("invalid frame header"));
        }
    }

    @Test
    public void rejectsTargetsLargerThanOneQuantum() throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        RuntimeProtocol.writeRequest(encoded, request(50001));
        try {
            RuntimeProtocol.readRequest(new ByteArrayInputStream(encoded.toByteArray()));
            fail("expected target rejection");
        } catch (RuntimeProtocol.ProtocolException expected) {
            assertEquals("target exceeds one absolute cycle quantum", expected.getMessage());
        }
    }

    private static RuntimeProtocol.Request request(long target) {
        byte[] flash = { 0x0c, (byte) 0x94, 0, 0 }; // JMP 0
        CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);
        return new RuntimeProtocol.Request(new RuntimeProtocol.Identity(0, 1, 2, 3, 9), target,
                firmware.getBytes(), AvrCheckpointCodec.encode(new AvrMachineState()), AvrInputs.allLow());
    }
}
