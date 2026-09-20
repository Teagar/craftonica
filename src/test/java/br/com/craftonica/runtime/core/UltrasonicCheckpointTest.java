package br.com.craftonica.runtime.core;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class UltrasonicCheckpointTest {
    @Test public void scheduledEchoSurvivesAWorkerCheckpoint() throws Exception {
        AvrMachineState state = new AvrMachineState();
        state.ultrasonicTriggerPin = 7;
        state.ultrasonicEchoPin = 6;
        state.ultrasonicTriggerHigh = true;
        state.ultrasonicTriggerRiseCycle = 1234L;
        state.ultrasonicEchoStartCycle = 50000L;
        state.ultrasonicEchoEndCycle = 96500L;

        AvrMachineState restored = AvrCheckpointCodec.decode(AvrCheckpointCodec.encode(state));
        assertEquals(7, restored.ultrasonicTriggerPin);
        assertEquals(6, restored.ultrasonicEchoPin);
        assertTrue(restored.ultrasonicTriggerHigh);
        assertEquals(1234L, restored.ultrasonicTriggerRiseCycle);
        assertEquals(50000L, restored.ultrasonicEchoStartCycle);
        assertEquals(96500L, restored.ultrasonicEchoEndCycle);
    }

    @Test public void versionOneCheckpointStillLoadsAndUpgrades() throws Exception {
        AvrMachineState original = new AvrMachineState();
        original.setCycles(98765L);
        byte[] current = AvrCheckpointCodec.encode(original);
        int legacyLength = current.length - 33;
        byte[] legacy = new byte[legacyLength];
        System.arraycopy(current, 0, legacy, 0, legacyLength - 32);
        ByteBuffer header = ByteBuffer.wrap(legacy).order(ByteOrder.LITTLE_ENDIAN);
        header.position(4); header.putShort((short) 1); header.position(8);
        header.putInt(legacyLength); header.putInt(legacyLength - 16 - 32);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                java.util.Arrays.copyOf(legacy, legacyLength - 32));
        System.arraycopy(hash, 0, legacy, legacyLength - 32, 32);

        AvrMachineState restored = AvrCheckpointCodec.decode(legacy);
        assertEquals(98765L, restored.getCycles());
        assertEquals(-1, restored.ultrasonicTriggerPin);
        assertEquals(AvrCheckpointCodec.ENCODED_SIZE, AvrCheckpointCodec.encode(restored).length);
    }
}
