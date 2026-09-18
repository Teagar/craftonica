package br.com.craftonica.tile;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import org.junit.Test;

import java.util.UUID;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RoboBoardStateTest {
    @Test
    public void installsVerifiedFirmwareAndCommitsOnlyMatchingRuntimeState() {
        RoboBoardState state = new RoboBoardState(new UUID(1, 2));
        long revision = state.installVerifiedFirmware(firmware(), 0);
        byte[] checkpoint = checkpoint();
        state.commitRuntimeCheckpoint(state.getGeneration(), revision, checkpoint,
                RoboBoardState.Status.RUNNING, "", true, false);
        checkpoint[0] = 9;

        assertEquals(1, revision);
        assertArrayEquals(checkpoint(), state.getCheckpoint());
        assertTrue(state.isRunning());
        assertFalse(state.isD13High());
        reject(new Runnable() {
            @Override public void run() {
                state.commitRuntimeCheckpoint(0, revision, new byte[0],
                        RoboBoardState.Status.STOPPED, "", false, false);
            }
        });
    }

    @Test
    public void rejectsOversizedCheckpointAndFault() {
        final RoboBoardState state = new RoboBoardState(new UUID(1, 2));
        final long revision = state.installVerifiedFirmware(firmware(), 0);
        reject(new Runnable() {
            @Override public void run() {
                state.commitRuntimeCheckpoint(state.getGeneration(), revision,
                        new byte[RoboBoardState.MAX_CHECKPOINT_BYTES + 1],
                        RoboBoardState.Status.STOPPED, "", false, false);
            }
        });
        reject(new Runnable() {
            @Override public void run() {
                state.commitRuntimeCheckpoint(state.getGeneration(), revision, new byte[0],
                        RoboBoardState.Status.FAULT, repeat('X', RoboBoardState.MAX_FAULT_BYTES + 1), false, false);
            }
        });
    }

    @Test
    public void unknownSchemaAndHashMismatchFailClosed() {
        RoboBoardState original = new RoboBoardState(new UUID(1, 2));
        original.installVerifiedFirmware(firmware(), 0);
        RoboBoardState.Persisted valid = original.snapshot();
        byte[] badHash = valid.firmwareHash.clone();
        badHash[0] ^= 1;

        RoboBoardState unknown = RoboBoardState.restore(new RoboBoardState.Persisted(
                99, valid.boardId, valid.generation, valid.revision, valid.firmware, valid.firmwareHash,
                valid.checkpoint, valid.checkpointHash, valid.statusOrdinal, valid.fault, valid.running, valid.d13High));
        RoboBoardState mismatch = RoboBoardState.restore(new RoboBoardState.Persisted(
                valid.schema, valid.boardId, valid.generation, valid.revision, valid.firmware, badHash,
                valid.checkpoint, valid.checkpointHash, valid.statusOrdinal, valid.fault, valid.running, valid.d13High));

        assertFalse(unknown.hasFirmware());
        assertEquals(RoboBoardState.Status.DISABLED, unknown.getStatus());
        assertEquals("UNKNOWN_SCHEMA", unknown.getFault());
        assertFalse(mismatch.hasFirmware());
        assertEquals("INVALID_PERSISTED_STATE", mismatch.getFault());
    }

    @Test
    public void malformedAppliedOutputStateFailsClosed() {
        RoboBoardState original = new RoboBoardState(new UUID(1, 2));
        original.installVerifiedFirmware(firmware(), 0);
        RoboBoardState.Persisted valid = original.snapshot();
        RoboBoardState malformedArrays = RoboBoardState.restore(new RoboBoardState.Persisted(
                valid.schema, valid.boardId, valid.generation, valid.revision, valid.firmware, valid.firmwareHash,
                valid.checkpoint, valid.checkpointHash, valid.statusOrdinal, valid.fault, valid.running, valid.d13High,
                0, 0, 0, new int[1], valid.pwmPrescaler, valid.pwmCompare, valid.lastTx));
        RoboBoardState disabledWithOutput = RoboBoardState.restore(new RoboBoardState.Persisted(
                RoboBoardState.SCHEMA_VERSION, new UUID(3, 4), 0, 0, new byte[0], new byte[0],
                new byte[0], new byte[0], RoboBoardState.Status.DISABLED.ordinal(), "", false, false,
                1, 1, 0, new int[RoboBoardState.OUTPUT_PIN_COUNT], new int[RoboBoardState.OUTPUT_PIN_COUNT],
                new int[RoboBoardState.OUTPUT_PIN_COUNT], new byte[0]));

        assertFalse(malformedArrays.hasFirmware());
        assertEquals("INVALID_PERSISTED_STATE", malformedArrays.getFault());
        assertFalse(disabledWithOutput.hasFirmware());
        assertEquals("INVALID_PERSISTED_STATE", disabledWithOutput.getFault());
    }

    @Test
    public void validSnapshotRoundTripsAllBoundedState() {
        RoboBoardState original = new RoboBoardState(new UUID(7, 8));
        long revision = original.installVerifiedFirmware(firmware(), 0);
        original.commitRuntimeCheckpoint(original.getGeneration(), revision, checkpoint(),
                RoboBoardState.Status.SUSPENDED, "", false, false);

        RoboBoardState restored = RoboBoardState.restore(original.snapshot());

        assertEquals(original.getBoardId(), restored.getBoardId());
        assertEquals(original.getGeneration(), restored.getGeneration());
        assertEquals(revision, restored.getRevision());
        assertArrayEquals(original.getFirmware(), restored.getFirmware());
        assertArrayEquals(checkpoint(), restored.getCheckpoint());
        assertEquals(RoboBoardState.Status.SUSPENDED, restored.getStatus());
        assertFalse(restored.isD13High());
    }

    @Test
    public void unloadInvalidatesInflightGeneration() {
        RoboBoardState state = new RoboBoardState(new UUID(1, 2));
        state.installVerifiedFirmware(firmware(), 0);
        long before = state.getGeneration();
        assertEquals(before + 1, state.unloadAndIncrementGeneration());
        assertFalse(state.isRunning());
        assertFalse(state.isD13High());
    }

    @Test
    public void startAndStopAreRevisionCheckedAndClearOutputs() {
        RoboBoardState state = new RoboBoardState(new UUID(5, 6));
        long installed = state.installVerifiedFirmware(firmware(), 0);
        long started = state.start(installed);
        assertTrue(state.isRunning());
        assertEquals(installed + 1, started);

        state.commitRuntimeCheckpoint(state.getGeneration(), started, checkpoint(),
                RoboBoardState.Status.RUNNING, "", true, false,
                Arrays.asList(new RuntimeProtocol.Gpio(1, 13, true, true)),
                Arrays.asList(new RuntimeProtocol.Pwm(1, 3, 2, 3, 64, 127, true)), new byte[0]);
        long stopped = state.stop(started);
        assertEquals(started + 1, stopped);
        assertFalse(state.isRunning());
        assertFalse(state.isD13High());
        assertEquals(0, state.getOutputMask());
        assertEquals(0, state.getPwmMask());
    }

    @Test
    public void appliedOutputsPersistAndFaultClearsStalePwm() {
        RoboBoardState state = new RoboBoardState(new UUID(3, 4));
        long revision = state.installVerifiedFirmware(firmware(), 0);
        state.commitRuntimeCheckpoint(state.getGeneration(), revision, checkpoint(),
                RoboBoardState.Status.RUNNING, "", true, false,
                Arrays.asList(new RuntimeProtocol.Gpio(1, 13, true, true)),
                Arrays.asList(new RuntimeProtocol.Pwm(1, 3, 2, 3, 64, 127, true)),
                new byte[] { 65 });

        RoboBoardState restored = RoboBoardState.restore(state.snapshot());
        assertEquals(1 << 3, restored.getPwmMask());
        assertEquals(1 << 13, restored.getHighMask());
        assertArrayEquals(new byte[] { 65 }, restored.getLastTx());

        restored.commitRuntimeCheckpoint(restored.getGeneration(), revision, checkpoint(),
                RoboBoardState.Status.FAULT, "AVR_FAULT_1", false, false,
                Collections.<RuntimeProtocol.Gpio>emptyList(), Collections.<RuntimeProtocol.Pwm>emptyList(), new byte[0]);
        assertEquals(0, restored.getPwmMask());
        assertEquals(0, restored.getHighMask());
    }

    private static CRLFirmware firmware() {
        return CRLFirmware.create(new byte[32], new byte[] { 1, 2, 3, 4 });
    }

    private static byte[] checkpoint() {
        return AvrCheckpointCodec.encode(new AvrMachineState());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void reject(Runnable runnable) {
        try {
            runnable.run();
            fail("Expected strict state validation");
        } catch (IllegalArgumentException expected) {
        } catch (IllegalStateException expected) {
        }
    }
}
