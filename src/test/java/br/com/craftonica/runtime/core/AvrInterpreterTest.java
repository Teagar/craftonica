package br.com.craftonica.runtime.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class AvrInterpreterTest {
    @Test
    public void executesTheMinimalContractFixtureJmp() throws Exception {
        AvrMachineState state = new AvrMachineState();
        AvrExecutionResult result = new AvrInterpreter(words(0x940c, 0x0000))
                .executeToAbsoluteTarget(state, 3, AvrInputs.allLow());
        assertEquals(0, state.getWordPc());
        assertEquals(3, result.getCompletedAtCycle());
    }

    @Test
    public void preemptsAnInfiniteLoopAtTheAbsoluteQuantum() throws Exception {
        AvrMachineState state = new AvrMachineState();
        AvrInterpreter interpreter = new AvrInterpreter(words(0xcfff));
        interpreter.executeToAbsoluteTarget(state, 50000, AvrInputs.allLow());
        assertEquals(50000, state.getCycles());
        assertEquals(0, state.getWordPc());
        interpreter.executeToAbsoluteTarget(state, 100000, AvrInputs.allLow());
        assertEquals(100000, state.getCycles());
    }

    @Test
    public void faultsAreInstructionAtomic() throws Exception {
        assertFault(AvrFault.Code.INVALID_OPCODE, words(0xffff), new AvrMachineState());
        assertFault(AvrFault.Code.INVALID_FETCH, words(0x940c), new AvrMachineState());

        AvrMachineState stack = new AvrMachineState();
        stack.setStackPointer(0x100);
        AvrInterpreter pushTwice = new AvrInterpreter(words(0x920f, 0x920f));
        pushTwice.executeToAbsoluteTarget(stack, 2, AvrInputs.allLow());
        AvrMachineState before = stack.copy();
        try {
            pushTwice.executeToAbsoluteTarget(stack, 4, AvrInputs.allLow());
            fail("expected stack fault");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.MEMORY_FAULT, fault.getCode());
            assertEquals(before, stack);
        }

        AvrMachineState trap = new AvrMachineState();
        AvrInterpreter trapped = new AvrInterpreter(words(ldi(24, 1), 0x9380, 0xc1f0));
        trapped.executeToAbsoluteTarget(trap, 1, AvrInputs.allLow());
        before = trap.copy();
        try {
            trapped.executeToAbsoluteTarget(trap, 3, AvrInputs.allLow());
            fail("expected trap");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.INVALID_PIN, fault.getCode());
            assertEquals(before, trap);
        }

        AvrMachineState peripheral = new AvrMachineState();
        AvrInterpreter unsupported = new AvrInterpreter(words(ldi(24, 1), sts(24), 0x0054));
        unsupported.executeToAbsoluteTarget(peripheral, 1, AvrInputs.allLow());
        before = peripheral.copy();
        try {
            unsupported.executeToAbsoluteTarget(peripheral, 3, AvrInputs.allLow());
            fail("expected unsupported peripheral fault");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.UNSUPPORTED_PERIPHERAL, fault.getCode());
            assertEquals(before, peripheral);
        }
    }

    @Test
    public void faultLeavesTheEntireInputSliceUnchanged() throws Exception {
        AvrMachineState state = new AvrMachineState();
        state.setRegister(16, 0x55);
        AvrMachineState before = state.copy();
        try {
            new AvrInterpreter(words(ldi(16, 0xaa), 0xffff))
                    .executeToAbsoluteTarget(state, 10, AvrInputs.allLow());
            fail("expected opcode fault");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.INVALID_OPCODE, fault.getCode());
            assertEquals(1, fault.getCycle());
            assertEquals(1, fault.getWordPc());
            assertEquals(before, state);
        }
    }

    @Test
    public void callStoresSiliconOrderedBytesAndReturnsFromNonzeroTarget() throws Exception {
        AvrMachineState state = new AvrMachineState();
        AvrInterpreter interpreter = new AvrInterpreter(words(0x940e, 0x0004, 0, 0, 0x9508));

        interpreter.executeToAbsoluteTarget(state, 4, AvrInputs.allLow());
        assertEquals(4, state.getWordPc());
        assertEquals(AvrMachineState.RAMEND - 2, state.getStackPointer());
        assertEquals(0x02, state.getSramByte(AvrMachineState.RAMEND - 0x100));
        assertEquals(0x00, state.getSramByte(AvrMachineState.RAMEND - 1 - 0x100));

        interpreter.executeToAbsoluteTarget(state, 8, AvrInputs.allLow());
        assertEquals(2, state.getWordPc());
        assertEquals(AvrMachineState.RAMEND, state.getStackPointer());
    }

    @Test
    public void indirectJumpAndCallUseWordAddressInZ() throws Exception {
        AvrMachineState jump = new AvrMachineState();
        jump.setRegister(30, 2);
        new AvrInterpreter(words(0x9409, 0xffff, 0xcfff))
                .executeToAbsoluteTarget(jump, 2, AvrInputs.allLow());
        assertEquals(2, jump.getWordPc());

        AvrMachineState call = new AvrMachineState();
        call.setRegister(30, 2);
        AvrInterpreter interpreter = new AvrInterpreter(words(0x9509, 0xcfff, 0x9508));
        interpreter.executeToAbsoluteTarget(call, 3, AvrInputs.allLow());
        assertEquals(2, call.getWordPc());
        interpreter.executeToAbsoluteTarget(call, 7, AvrInputs.allLow());
        assertEquals(1, call.getWordPc());
    }

    @Test
    public void skipBitInstructionsHandleClearAndSetRegisters() throws Exception {
        AvrMachineState clear = new AvrMachineState();
        new AvrInterpreter(words(0xfc00, 0xffff, 0xcfff))
                .executeToAbsoluteTarget(clear, 2, AvrInputs.allLow());
        assertEquals(2, clear.getWordPc());

        AvrMachineState set = new AvrMachineState();
        set.setRegister(0, 1);
        new AvrInterpreter(words(0xfe00, 0xffff, 0xcfff))
                .executeToAbsoluteTarget(set, 2, AvrInputs.allLow());
        assertEquals(2, set.getWordPc());
    }

    @Test
    public void timer0OverflowUsesAtmega328pVectorAndSiliconStackOrder() throws Exception {
        byte[] flash = new byte[68];
        putWord(flash, 0, 0x0000);
        putWord(flash, 32, 0xcfff);
        AvrMachineState state = new AvrMachineState();
        state.setSreg(0x80);
        state.mmio[0x35 - 0x20] = 1;
        state.mmio[0x6e - 0x20] = 1;

        new AvrInterpreter(flash).executeToAbsoluteTarget(state, 1, AvrInputs.allLow());

        assertEquals(32, state.getWordPc());
        assertEquals(0x01, state.getSramByte(AvrMachineState.RAMEND - 0x100));
        assertEquals(0x00, state.getSramByte(AvrMachineState.RAMEND - 1 - 0x100));
        assertFalse((state.getSreg() & 0x80) != 0);
    }

    @Test
    public void executesFixtureShiftUnaryAndSwapOpcodes() throws Exception {
        assertUnary(0x9405, 0x81, 0xc0, 0x15); // ASR: H, S and C remain/set as specified.
        assertUnary(0x9406, 0x81, 0x40, 0x19); // LSR
        assertUnaryWithSreg(0x9407, 0x02, 0x81, 0x01, 0x0c); // ROR through carry
        assertUnary(0x9403, 0x7f, 0x80, 0x0c); // INC
        assertUnary(0x9401, 0x01, 0xff, 0x35); // NEG
        assertUnaryWithSreg(0x9402, 0xa5, 0x5a, 0xa5, 0xa5); // SWAP preserves SREG
    }

    @Test
    public void compiledLikePortWritesPublishD13() throws Exception {
        byte[] flash = words(ldi(24, 0x20), out(4, 24), out(5, 24), 0xcfff);
        AvrMachineState state = new AvrMachineState();
        AvrExecutionResult result = new AvrInterpreter(flash).executeToAbsoluteTarget(state, 4, AvrInputs.allLow());
        assertEquals(2, result.getGpioChanges().size());
        assertEquals(13, result.getGpioChanges().get(1).getPin());
        assertTrue(result.getGpioChanges().get(1).isOutput());
        assertTrue(result.getGpioChanges().get(1).isHigh());
    }

    @Test
    public void adcPollingConvertsZeroMidscaleAndFullscale() throws Exception {
        assertEquals(0, adc(0));
        assertEquals(512, adc(2500000));
        assertEquals(1023, adc(5000000));
    }

    @Test
    public void timerWritesPublishBoundedPwmDescriptors() throws Exception {
        byte[] flash = words(
                ldi(24, 0x23), sts(24), 0x0044,
                ldi(24, 3), sts(24), 0x0045,
                ldi(24, 64), sts(24), 0x0048,
                0xcfff);
        AvrExecutionResult result = new AvrInterpreter(flash)
                .executeToAbsoluteTarget(new AvrMachineState(), 20, AvrInputs.allLow());
        assertFalse(result.getPwmDescriptors().isEmpty());
        PwmDescriptor last = result.getPwmDescriptors().get(result.getPwmDescriptors().size() - 1);
        assertEquals(5, last.getPin());
        assertEquals(0, last.getTimer());
        assertEquals(64, last.getCompare());
        assertEquals(64, last.getPrescaler());
        assertFalse(last.isPhaseCorrect());
    }

    @Test
    public void uartStatusPreservesHardwareBitsAndUsesTxcWriteOneToClear() throws Exception {
        byte[] flash = words(
                ldi(24, 0x41), sts(24), 0x00c6,
                ldi(24, 0x02), sts(24), 0x00c0,
                lds(25), 0x00c0,
                ldi(24, 0x42), sts(24), 0x00c6,
                ldi(24, 0x40), sts(24), 0x00c0,
                lds(26), 0x00c0,
                0xcfff);
        AvrMachineState state = new AvrMachineState();
        AvrExecutionResult result = new AvrInterpreter(flash)
                .executeToAbsoluteTarget(state, 30, AvrInputs.allLow());

        assertArrayEquals(new byte[] { 0x41, 0x42 }, result.getTransmittedBytes());
        assertEquals(0x62, state.getRegister(25));
        assertEquals(0x20, state.getRegister(26));
        assertEquals(0x20, state.getMmio(0xc0));
    }

    @Test
    public void clearingTimerCompareModePublishesPwmStop() throws Exception {
        byte[] flash = words(
                ldi(16, 0x23), sts(16), 0x0044,
                ldi(16, 0x03), sts(16), 0x0045,
                ldi(16, 0x00), sts(16), 0x0044,
                0xcfff);
        AvrExecutionResult result = new AvrInterpreter(flash)
                .executeToAbsoluteTarget(new AvrMachineState(), 30, AvrInputs.allLow());
        PwmDescriptor last = result.getPwmDescriptors().get(result.getPwmDescriptors().size() - 1);
        assertEquals(5, last.getPin());
        assertEquals(0, last.getPrescaler());
    }

    @Test
    public void checkpointIsCanonicalRoundTripsAndRejectsTampering() throws Exception {
        byte[] flash = words(ldi(24, 7), 0xcfff);
        AvrInputs inputs = AvrInputs.allLow();
        AvrMachineState first = new AvrMachineState();
        AvrMachineState second = new AvrMachineState();
        AvrInterpreter interpreter = new AvrInterpreter(flash);
        interpreter.executeToAbsoluteTarget(first, 101, inputs);
        interpreter.executeToAbsoluteTarget(second, 101, inputs);
        byte[] encoded = AvrCheckpointCodec.encode(first);
        assertArrayEquals(encoded, AvrCheckpointCodec.encode(second));
        assertEquals(first, AvrCheckpointCodec.decode(encoded));

        byte[] changed = encoded.clone();
        changed[20] ^= 1;
        try {
            AvrCheckpointCodec.decode(changed);
            fail("expected checksum rejection");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.CHECKPOINT_INCOMPATIBLE, fault.getCode());
        }
        try {
            AvrCheckpointCodec.decode(Arrays.copyOf(encoded, encoded.length - 1));
            fail("expected length rejection");
        } catch (AvrFault fault) {
            assertEquals(AvrFault.Code.CHECKPOINT_INCOMPATIBLE, fault.getCode());
        }
    }

    private static int adc(int microvolts) throws Exception {
        byte[] flash = words(
                ldi(24, 0x40), sts(24), 0x007c,
                ldi(24, 0xc7), sts(24), 0x007a,
                lds(24), 0x007a,
                0xfdc6, // SBRC r28? replaced below by explicit encoder
                0xcffc,
                lds(24), 0x0078,
                lds(25), 0x0079,
                0xcfff);
        putWord(flash, 8, sbrc(24, 6));
        int[] analog = new int[6]; analog[0] = microvolts;
        AvrInputs inputs = new AvrInputs(new boolean[20], analog);
        AvrMachineState state = new AvrMachineState();
        new AvrInterpreter(flash).executeToAbsoluteTarget(state, 2000, inputs);
        return state.getRegister(24) | (state.getRegister(25) << 8);
    }

    private static void assertFault(AvrFault.Code code, byte[] flash, AvrMachineState state) {
        try {
            new AvrInterpreter(flash).executeToAbsoluteTarget(state, 10, AvrInputs.allLow());
            fail("expected fault");
        } catch (AvrFault fault) {
            assertEquals(code, fault.getCode());
            assertEquals(0, state.getCycles());
            assertEquals(0, state.getWordPc());
        }
    }

    private static void assertUnary(int opcode, int input, int expected, int expectedSreg) throws Exception {
        assertUnaryWithSreg(opcode, input, expected, 0, expectedSreg);
    }

    private static void assertUnaryWithSreg(int opcode, int input, int expected, int initialSreg,
                                            int expectedSreg) throws Exception {
        AvrMachineState state = new AvrMachineState();
        state.setRegister(16, input);
        state.setSreg(initialSreg);
        new AvrInterpreter(words(opcode | (16 << 4))).executeToAbsoluteTarget(state, 1, AvrInputs.allLow());
        assertEquals(expected, state.getRegister(16));
        assertEquals(expectedSreg, state.getSreg());
    }

    private static int ldi(int register, int value) { return 0xe000 | ((value & 0xf0) << 4) | ((register - 16) << 4) | (value & 15); }
    private static int lds(int register) { return 0x9000 | (register << 4); }
    private static int sts(int register) { return 0x9200 | (register << 4); }
    private static int out(int ioAddress, int register) { return 0xb800 | (register << 4) | (ioAddress & 15) | ((ioAddress & 0x30) << 5); }
    private static int sbrc(int register, int bit) { return 0xfc00 | (register << 4) | bit; }

    private static byte[] words(int... words) {
        byte[] bytes = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) putWord(bytes, i, words[i]);
        return bytes;
    }

    private static void putWord(byte[] bytes, int index, int word) {
        bytes[index * 2] = (byte) word;
        bytes[index * 2 + 1] = (byte) (word >>> 8);
    }
}
