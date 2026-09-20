package br.com.craftonica.runtime.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Iterative AVR interpreter for the instruction forms emitted by the pinned Core fixture set. */
public final class AvrInterpreter {
    public static final int MAX_FLASH_BYTES = 32256;
    public static final int QUANTUM_CYCLES = 50000;
    public static final int MAX_EFFECTIVE_CHANGES = 64;
    public static final int MAX_TX_BYTES = 256;

    private static final int UCSR0A = 0xc0;
    private static final int UDR0 = 0xc6;
    private static final int UART_MPCM = 0x01;
    private static final int UART_U2X = 0x02;
    private static final int UART_UDRE = 0x20;
    private static final int UART_TXC = 0x40;

    private static final int C = 0;
    private static final int Z = 1;
    private static final int N = 2;
    private static final int V = 3;
    private static final int S = 4;
    private static final int H = 5;
    private static final int I = 7;

    private final byte[] flash;

    public AvrInterpreter(byte[] flash) {
        if (flash == null || flash.length == 0 || flash.length > MAX_FLASH_BYTES || (flash.length & 1) != 0) {
            throw new IllegalArgumentException("FLASH must be non-empty, even and at most 32256 bytes");
        }
        this.flash = flash.clone();
    }

    public byte[] getFlash() {
        return flash.clone();
    }

    public AvrExecutionResult executeToAbsoluteTarget(AvrMachineState state, long absoluteTarget,
                                                       AvrInputs inputs) throws AvrFault {
        if (state == null || inputs == null) {
            throw new NullPointerException();
        }
        if (absoluteTarget < state.cycles || absoluteTarget - state.cycles > QUANTUM_CYCLES) {
            throw new IllegalArgumentException("absolute target exceeds one 50000-cycle quantum");
        }
        AvrMachineState working = state.copy();
        configureUltrasonic(working, inputs.getUltrasonic());
        Output output = new Output();
        while (working.cycles < absoluteTarget) {
            step(working, inputs, output);
        }
        state.replaceWith(working);
        return new AvrExecutionResult(working.cycles, output.gpio, output.pwm, output.tx.toByteArray());
    }

    private void step(AvrMachineState state, AvrInputs inputs, Output output) throws AvrFault {
        int pc = state.wordPc;
        int op = fetchWord(state, pc);
        int next = pc + 1;
        int cycles = 1;

        if (op == 0x0000) { // NOP
            // Nothing to do.
        } else if ((op & 0xfc00) == 0x1c00) { // ADC
            int d = rd(op), r = rr(op);
            state.registers[d] = (byte) add(state, u(state, d), u(state, r), flag(state, C) ? 1 : 0, true);
        } else if ((op & 0xfc00) == 0x0c00) { // ADD
            int d = rd(op), r = rr(op);
            state.registers[d] = (byte) add(state, u(state, d), u(state, r), 0, false);
        } else if ((op & 0xff00) == 0x9600) { // ADIW
            int d = 24 + 2 * ((op >>> 4) & 3), k = (op & 15) | ((op >>> 2) & 0x30);
            int old = wordReg(state, d), result = (old + k) & 0xffff;
            setWordReg(state, d, result);
            setFlag(state, Z, result == 0); setFlag(state, N, (result & 0x8000) != 0);
            setFlag(state, V, (old & 0x8000) == 0 && (result & 0x8000) != 0);
            setFlag(state, C, (old & 0x8000) != 0 && (result & 0x8000) == 0);
            setFlag(state, S, flag(state, N) != flag(state, V)); cycles = 2;
        } else if ((op & 0xfc00) == 0x2000) { // AND
            logic(state, rd(op), u(state, rd(op)) & u(state, rr(op)));
        } else if ((op & 0xf000) == 0x7000) { // ANDI
            int d = 16 + ((op >>> 4) & 15), k = immediate(op);
            logic(state, d, u(state, d) & k);
        } else if ((op & 0xfc00) == 0xf000 || (op & 0xfc00) == 0xf400) { // BRBS / BRBC
            int bit = op & 7;
            boolean branch = flag(state, bit) == ((op & 0x0400) == 0);
            if (branch) { next += signed(op >>> 3, 7); cycles = 2; }
        } else if ((op & 0xfe0e) == 0x940e || (op & 0xfe0e) == 0x940c) { // CALL / JMP
            int second = fetchWord(state, pc + 1);
            int target = ((op & 1) << 16) | ((op & 0x01f0) << 13) | second;
            if ((op & 2) != 0) {
                validatePush(state, 2); pushReturn(state, pc + 2); cycles = 4;
            } else {
                cycles = 3;
            }
            next = target;
        } else if (op == 0x94f8) { // CLI
            setFlag(state, I, false);
        } else if ((op & 0xfe0f) == 0x9400) { // COM
            int d = rdSingle(op), result = (~u(state, d)) & 0xff;
            state.registers[d] = (byte) result; setNz(state, result); setFlag(state, V, false);
            setFlag(state, S, flag(state, N)); setFlag(state, C, true);
        } else if ((op & 0xfc00) == 0x1400) { // CP
            subtract(state, u(state, rd(op)), u(state, rr(op)), 0, false, false);
        } else if ((op & 0xfc00) == 0x0400) { // CPC
            subtract(state, u(state, rd(op)), u(state, rr(op)), flag(state, C) ? 1 : 0, true, false);
        } else if ((op & 0xf000) == 0x3000) { // CPI
            int d = 16 + ((op >>> 4) & 15);
            subtract(state, u(state, d), immediate(op), 0, false, false);
        } else if ((op & 0xfc00) == 0x1000) { // CPSE
            if (u(state, rd(op)) == u(state, rr(op))) {
                int skipped = fetchWord(state, pc + 1);
                int words = isTwoWord(skipped) ? 2 : 1;
                if (words == 2) fetchWord(state, pc + 2);
                next += words; cycles = 1 + words;
            }
        } else if ((op & 0xfe0f) == 0x940a) { // DEC
            int d = rdSingle(op), result = (u(state, d) - 1) & 0xff;
            state.registers[d] = (byte) result; setNz(state, result);
            setFlag(state, V, result == 0x7f); setFlag(state, S, flag(state, N) != flag(state, V));
        } else if ((op & 0xfe0f) == 0x9403) { // INC
            int d = rdSingle(op), result = (u(state, d) + 1) & 0xff;
            state.registers[d] = (byte) result; setNz(state, result);
            setFlag(state, V, result == 0x80); setFlag(state, S, flag(state, N) != flag(state, V));
        } else if ((op & 0xfe0f) == 0x9401) { // NEG
            int d = rdSingle(op), old = u(state, d), result = (-old) & 0xff;
            state.registers[d] = (byte) result; setNz(state, result);
            setFlag(state, H, ((result | old) & 0x08) != 0); setFlag(state, V, result == 0x80);
            setFlag(state, C, result != 0); setFlag(state, S, flag(state, N) != flag(state, V));
        } else if ((op & 0xfe0f) == 0x9405 || (op & 0xfe0f) == 0x9406
                || (op & 0xfe0f) == 0x9407) { // ASR / LSR / ROR
            int d = rdSingle(op), old = u(state, d), kind = op & 15;
            int result = old >>> 1;
            if (kind == 5) result |= old & 0x80;
            else if (kind == 7 && flag(state, C)) result |= 0x80;
            state.registers[d] = (byte) result; setFlag(state, C, (old & 1) != 0);
            setNz(state, result); setFlag(state, V, flag(state, N) != flag(state, C));
            setFlag(state, S, flag(state, N) != flag(state, V));
        } else if ((op & 0xfe0f) == 0x9402) { // SWAP
            int d = rdSingle(op), old = u(state, d);
            state.registers[d] = (byte) ((old << 4) | (old >>> 4));
        } else if ((op & 0xfc00) == 0x2400) { // EOR
            logic(state, rd(op), u(state, rd(op)) ^ u(state, rr(op)));
        } else if ((op & 0xf800) == 0xb000) { // IN
            int d = rdSingle(op), address = 0x20 + ((op & 15) | ((op >>> 5) & 0x30));
            state.registers[d] = (byte) readData(state, address, inputs);
        } else if (op == 0x9409 || op == 0x9509) { // IJMP / ICALL
            int target = wordReg(state, 30);
            if (op == 0x9509) {
                validatePush(state, 2);
                pushReturn(state, next);
                cycles = 3;
            } else cycles = 2;
            next = target;
        } else if ((op & 0xfe0f) == 0x900c || (op & 0xfe0f) == 0x900d || (op & 0xfe0f) == 0x900e) {
            int d = rdSingle(op), address = wordReg(state, 26), mode = op & 15;
            if (mode == 14) address = (address - 1) & 0xffff;
            int value = readData(state, address, inputs);
            state.registers[d] = (byte) value;
            if (mode == 13) setWordReg(state, 26, (address + 1) & 0xffff);
            else if (mode == 14) setWordReg(state, 26, address);
            cycles = 2;
        } else if (isLdYz(op)) {
            int d = rdSingle(op), base = (op & 8) != 0 ? 28 : 30, q = displacement(op);
            state.registers[d] = (byte) readData(state, (wordReg(state, base) + q) & 0xffff, inputs);
            cycles = 2;
        } else if ((op & 0xfe0f) == 0x9001 || (op & 0xfe0f) == 0x9002
                || (op & 0xfe0f) == 0x9009 || (op & 0xfe0f) == 0x900a) {
            int d = rdSingle(op), base = (op & 8) != 0 ? 28 : 30, address = wordReg(state, base);
            if ((op & 15) == 2 || (op & 15) == 10) address = (address - 1) & 0xffff;
            state.registers[d] = (byte) readData(state, address, inputs);
            if ((op & 15) == 1 || (op & 15) == 9) setWordReg(state, base, (address + 1) & 0xffff);
            else setWordReg(state, base, address);
            cycles = 2;
        } else if ((op & 0xf000) == 0xe000) { // LDI
            state.registers[16 + ((op >>> 4) & 15)] = (byte) immediate(op);
        } else if ((op & 0xfe0f) == 0x9000) { // LDS
            int address = fetchWord(state, pc + 1);
            state.registers[rdSingle(op)] = (byte) readData(state, address, inputs);
            next++; cycles = 2;
        } else if (op == 0x95c8 || (op & 0xfe0f) == 0x9004 || (op & 0xfe0f) == 0x9005) { // LPM
            int d = op == 0x95c8 ? 0 : rdSingle(op), address = wordReg(state, 30);
            if (address < 0 || address >= flash.length) fault(state, AvrFault.Code.INVALID_FETCH, "LPM outside FLASH");
            state.registers[d] = flash[address];
            if ((op & 0xfe0f) == 0x9005) setWordReg(state, 30, (address + 1) & 0xffff);
            cycles = 3;
        } else if ((op & 0xfc00) == 0x2c00) { // MOV
            state.registers[rd(op)] = state.registers[rr(op)];
        } else if ((op & 0xff00) == 0x0100) { // MOVW
            int d = ((op >>> 4) & 15) * 2, r = (op & 15) * 2;
            state.registers[d] = state.registers[r]; state.registers[d + 1] = state.registers[r + 1];
        } else if ((op & 0xfc00) == 0x9c00) { // MUL
            int result = u(state, rd(op)) * u(state, rr(op));
            setWordReg(state, 0, result); setFlag(state, Z, result == 0);
            setFlag(state, C, (result & 0x8000) != 0); cycles = 2;
        } else if ((op & 0xfc00) == 0x2800) { // OR
            logic(state, rd(op), u(state, rd(op)) | u(state, rr(op)));
        } else if ((op & 0xf000) == 0x6000) { // ORI
            int d = 16 + ((op >>> 4) & 15); logic(state, d, u(state, d) | immediate(op));
        } else if ((op & 0xf800) == 0xb800) { // OUT
            int r = rdSingle(op), address = 0x20 + ((op & 15) | ((op >>> 5) & 0x30));
            writeData(state, address, u(state, r), inputs, output);
        } else if ((op & 0xfe0f) == 0x900f) { // POP
            validatePop(state, 1); state.registers[rdSingle(op)] = (byte) pop(state); cycles = 2;
        } else if ((op & 0xfe0f) == 0x920f) { // PUSH
            validatePush(state, 1); push(state, u(state, rdSingle(op))); cycles = 2;
        } else if (op == 0x9508 || op == 0x9518) { // RET / RETI
            validatePop(state, 2); next = popReturn(state); cycles = 4;
            if (op == 0x9518) setFlag(state, I, true);
        } else if ((op & 0xf000) == 0xc000) { // RJMP
            next += signed(op, 12); cycles = 2;
        } else if ((op & 0xf000) == 0xd000) { // RCALL
            validatePush(state, 2); pushReturn(state, next);
            next += signed(op, 12); cycles = 3;
        } else if ((op & 0xfc00) == 0x0800) { // SBC
            int d = rd(op), result = subtract(state, u(state, d), u(state, rr(op)), flag(state, C) ? 1 : 0, true, true);
            state.registers[d] = (byte) result;
        } else if ((op & 0xf000) == 0x4000) { // SBCI
            int d = 16 + ((op >>> 4) & 15);
            state.registers[d] = (byte) subtract(state, u(state, d), immediate(op), flag(state, C) ? 1 : 0, true, true);
        } else if ((op & 0xff00) == 0x9b00) { // SBIS
            int address = 0x20 + ((op >>> 3) & 31), bit = op & 7;
            if ((readData(state, address, inputs) & (1 << bit)) != 0) {
                int skipped = fetchWord(state, pc + 1), words = isTwoWord(skipped) ? 2 : 1;
                if (words == 2) fetchWord(state, pc + 2);
                next += words; cycles = 1 + words;
            }
        } else if ((op & 0xfc08) == 0xfc00) { // SBRC / SBRS
            int r = (op >>> 4) & 31, bit = op & 7;
            boolean set = (u(state, r) & (1 << bit)) != 0;
            if (set == ((op & 0x0200) != 0)) {
                int skipped = fetchWord(state, pc + 1), words = isTwoWord(skipped) ? 2 : 1;
                if (words == 2) fetchWord(state, pc + 2);
                next += words; cycles = 1 + words;
            }
        } else if (op == 0x9478) { // SEI
            setFlag(state, I, true);
        } else if ((op & 0xfe0f) == 0x920c || (op & 0xfe0f) == 0x920d || (op & 0xfe0f) == 0x920e) {
            int r = rdSingle(op), address = wordReg(state, 26), mode = op & 15;
            if (mode == 14) address = (address - 1) & 0xffff;
            writeData(state, address, u(state, r), inputs, output);
            if (mode == 13) setWordReg(state, 26, (address + 1) & 0xffff);
            else if (mode == 14) setWordReg(state, 26, address);
            cycles = 2;
        } else if (isStYz(op)) {
            int r = rdSingle(op), base = (op & 8) != 0 ? 28 : 30, q = displacement(op);
            writeData(state, (wordReg(state, base) + q) & 0xffff, u(state, r), inputs, output); cycles = 2;
        } else if ((op & 0xfe0f) == 0x9201 || (op & 0xfe0f) == 0x9202
                || (op & 0xfe0f) == 0x9209 || (op & 0xfe0f) == 0x920a) {
            int r = rdSingle(op), base = (op & 8) != 0 ? 28 : 30, address = wordReg(state, base);
            if ((op & 15) == 2 || (op & 15) == 10) address = (address - 1) & 0xffff;
            writeData(state, address, u(state, r), inputs, output);
            if ((op & 15) == 1 || (op & 15) == 9) setWordReg(state, base, (address + 1) & 0xffff);
            else setWordReg(state, base, address);
            cycles = 2;
        } else if ((op & 0xfe0f) == 0x9200) { // STS
            int address = fetchWord(state, pc + 1), value = u(state, rdSingle(op));
            if (address == 0xc1f0) {
                if (value == 1) fault(state, AvrFault.Code.INVALID_PIN, "invalid pin ABI trap");
                fault(state, AvrFault.Code.UNSUPPORTED_ABI_TRAP, "unsupported ABI trap " + value);
            }
            writeData(state, address, value, inputs, output); next++; cycles = 2;
        } else if ((op & 0xfc00) == 0x1800) { // SUB
            int d = rd(op);
            state.registers[d] = (byte) subtract(state, u(state, d), u(state, rr(op)), 0, false, true);
        } else if ((op & 0xf000) == 0x5000) { // SUBI
            int d = 16 + ((op >>> 4) & 15);
            state.registers[d] = (byte) subtract(state, u(state, d), immediate(op), 0, false, true);
        } else if ((op & 0xff00) == 0x9700) { // SBIW
            int d = 24 + 2 * ((op >>> 4) & 3), k = (op & 15) | ((op >>> 2) & 0x30);
            int old = wordReg(state, d), result = (old - k) & 0xffff;
            setWordReg(state, d, result); setFlag(state, Z, result == 0); setFlag(state, N, (result & 0x8000) != 0);
            setFlag(state, V, (old & 0x8000) != 0 && (result & 0x8000) == 0);
            setFlag(state, C, (old & 0x8000) == 0 && (result & 0x8000) != 0);
            setFlag(state, S, flag(state, N) != flag(state, V)); cycles = 2;
        } else {
            fault(state, AvrFault.Code.INVALID_OPCODE, String.format("unsupported opcode 0x%04x", op));
        }

        state.wordPc = next;
        advance(state, cycles, inputs);
        serviceTimer0Interrupt(state);
    }

    private int fetchWord(AvrMachineState state, int wordPc) throws AvrFault {
        long offset = (long) wordPc * 2L;
        if (wordPc < 0 || offset + 1 >= flash.length) {
            fault(state, AvrFault.Code.INVALID_FETCH, "instruction fetch outside FLASH");
        }
        return (flash[(int) offset] & 0xff) | ((flash[(int) offset + 1] & 0xff) << 8);
    }

    private int readData(AvrMachineState state, int address, AvrInputs inputs) throws AvrFault {
        if (address >= 0 && address < 32) return u(state, address);
        if (address >= 0x100 && address <= AvrMachineState.RAMEND) return state.sram[address - 0x100] & 0xff;
        if (address < 0x20 || address > 0xff || !implementedMmio(address)) {
            fault(state, address > AvrMachineState.RAMEND ? AvrFault.Code.MEMORY_FAULT
                    : AvrFault.Code.UNSUPPORTED_PERIPHERAL, "unsupported data-space read 0x" + Integer.toHexString(address));
        }
        if (address == 0x5f) return state.sreg;
        if (address == 0x5d) return state.stackPointer & 0xff;
        if (address == 0x5e) return state.stackPointer >>> 8;
        if (address == 0x23 || address == 0x26 || address == 0x29) return pinRegister(state, address, inputs);
        return state.mmio[address - 0x20] & 0xff;
    }

    private void writeData(AvrMachineState state, int address, int value, AvrInputs inputs, Output output) throws AvrFault {
        value &= 0xff;
        if (address >= 0 && address < 32) { state.registers[address] = (byte) value; return; }
        if (address >= 0x100 && address <= AvrMachineState.RAMEND) { state.sram[address - 0x100] = (byte) value; return; }
        if (address < 0x20 || address > 0xff || !implementedMmio(address)) {
            fault(state, address > AvrMachineState.RAMEND ? AvrFault.Code.MEMORY_FAULT
                    : AvrFault.Code.UNSUPPORTED_PERIPHERAL, "unsupported data-space write 0x" + Integer.toHexString(address));
        }
        if (address == 0x5f) { state.sreg = value; return; }
        if (address == 0x5d || address == 0x5e) {
            int sp = address == 0x5d ? (state.stackPointer & 0xff00) | value : (state.stackPointer & 0xff) | (value << 8);
            if (sp < 0x100 || sp > AvrMachineState.RAMEND) fault(state, AvrFault.Code.MEMORY_FAULT, "SP outside SRAM");
            state.stackPointer = sp; return;
        }
        if (address == 0x23 || address == 0x26 || address == 0x29) {
            address += 2; value = (state.mmio[address - 0x20] & 0xff) ^ value;
        }
        if (address == 0x24 || address == 0x25 || address == 0x27 || address == 0x28
                || address == 0x2a || address == 0x2b) {
            emitGpioChanges(state, address, value, inputs, output);
        }
        if (isTimerRegister(address) && (state.mmio[address - 0x20] & 0xff) != value) {
            int oldControl = timerControl(state, address);
            validateTimerMode(state, address, value);
            int newControl = timerControl(state, address);
            int controlAddress = timerControlAddress(address);
            if (address == controlAddress) newControl = value;
            int descriptors = timerChannelCount(oldControl | newControl);
            ensureChanges(state, output, descriptors);
            state.mmio[address - 0x20] = (byte) value;
            emitPwm(state, address, oldControl, output);
            return;
        }
        if (address == 0x7a && (value & 0x40) != 0 && (state.mmio[0x7a - 0x20] & 0x40) == 0) {
            startAdc(state, value, inputs);
        }
        if (address == UCSR0A) {
            int old = state.mmio[UCSR0A - 0x20] & 0xff;
            int hardware = old & (UART_UDRE | UART_TXC);
            if ((value & UART_TXC) != 0) hardware &= ~UART_TXC;
            state.mmio[UCSR0A - 0x20] = (byte) (hardware | (value & (UART_U2X | UART_MPCM)));
            return;
        }
        if (address == UDR0) {
            if (output.tx.size() >= MAX_TX_BYTES) fault(state, AvrFault.Code.FIRMWARE_OUTPUT_LIMIT, "TX byte limit exceeded");
            output.tx.write(value);
            state.mmio[UCSR0A - 0x20] |= UART_UDRE | UART_TXC;
            state.mmio[address - 0x20] = (byte) value;
            return;
        }
        if (address == 0xc2 && (value & 0xc0) != 0) {
            fault(state, AvrFault.Code.UNSUPPORTED_PERIPHERAL, "synchronous UART mode");
        }
        state.mmio[address - 0x20] = (byte) value;
    }

    private void emitGpioChanges(AvrMachineState state, int address, int value, AvrInputs inputs,
                                 Output output) throws AvrFault {
        int old = state.mmio[address - 0x20] & 0xff;
        int changed = old ^ value;
        int count = Integer.bitCount(changed & portMask(address));
        ensureChanges(state, output, count);
        state.mmio[address - 0x20] = (byte) value;
        int portBase = address <= 0x25 ? 8 : address <= 0x28 ? 14 : 0;
        int ddrAddress = address == 0x24 || address == 0x27 || address == 0x2a ? address : address - 1;
        int portAddress = ddrAddress + 1;
        for (int bit = 0; bit < 8; bit++) {
            if ((changed & (1 << bit) & portMask(address)) != 0) {
                int pin = pinForPort(portBase, bit);
                boolean isOutput = (state.mmio[ddrAddress - 0x20] & (1 << bit)) != 0;
                boolean high = (state.mmio[portAddress - 0x20] & (1 << bit)) != 0;
                output.gpio.add(new GpioChange(state.cycles, pin, isOutput, high));
                observeUltrasonicTrigger(state, inputs.getUltrasonic(), pin, isOutput, high);
            }
        }
    }

    private void advance(AvrMachineState state, int elapsed, AvrInputs inputs) {
        long end = state.cycles + elapsed;
        advanceTimer0(state, elapsed);
        advanceTimer16(state, elapsed);
        advanceTimer2(state, elapsed);
        state.cycles = end;
        if (state.adcRunning && state.cycles >= state.adcCompleteCycle) {
            state.adcRunning = false;
            state.mmio[0x7a - 0x20] &= ~0x40;
            state.mmio[0x7a - 0x20] |= 0x10;
            state.mmio[0x78 - 0x20] = (byte) state.adcSample;
            state.mmio[0x79 - 0x20] = (byte) (state.adcSample >>> 8);
        }
    }

    private void advanceTimer0(AvrMachineState state, int elapsed) {
        int divisor = timerDivisor(state.mmio[0x45 - 0x20] & 7, false);
        if (divisor == 0) return;
        int total = state.timer0PrescaleCycles + elapsed, ticks = total / divisor;
        state.timer0PrescaleCycles = total % divisor;
        if (ticks == 0) return;
        int old = state.mmio[0x46 - 0x20] & 0xff, sum = old + ticks;
        state.mmio[0x46 - 0x20] = (byte) sum;
        if (sum > 255) state.mmio[0x35 - 0x20] |= 1;
    }

    private void advanceTimer16(AvrMachineState state, int elapsed) {
        int divisor = timerDivisor(state.mmio[0x81 - 0x20] & 7, false);
        if (divisor == 0) return;
        int total = state.timer1PrescaleCycles + elapsed, ticks = total / divisor;
        state.timer1PrescaleCycles = total % divisor;
        int count = (state.mmio[0x84 - 0x20] & 0xff) | ((state.mmio[0x85 - 0x20] & 0xff) << 8);
        count = (count + ticks) & 0xffff;
        state.mmio[0x84 - 0x20] = (byte) count; state.mmio[0x85 - 0x20] = (byte) (count >>> 8);
    }

    private void advanceTimer2(AvrMachineState state, int elapsed) {
        int divisor = timerDivisor(state.mmio[0xb1 - 0x20] & 7, true);
        if (divisor == 0) return;
        int total = state.timer2PrescaleCycles + elapsed, ticks = total / divisor;
        state.timer2PrescaleCycles = total % divisor;
        state.mmio[0xb2 - 0x20] = (byte) ((state.mmio[0xb2 - 0x20] & 0xff) + ticks);
    }

    private void serviceTimer0Interrupt(AvrMachineState state) throws AvrFault {
        if (flag(state, I) && (state.mmio[0x35 - 0x20] & 1) != 0 && (state.mmio[0x6e - 0x20] & 1) != 0) {
            validatePush(state, 2); pushReturn(state, state.wordPc); setFlag(state, I, false);
            state.mmio[0x35 - 0x20] &= ~1; state.wordPc = 32; state.cycles += 4;
            advanceTimer0(state, 4); advanceTimer16(state, 4); advanceTimer2(state, 4);
        }
    }

    private void startAdc(AvrMachineState state, int adcsra, AvrInputs inputs) throws AvrFault {
        int admux = state.mmio[0x7c - 0x20] & 0xff;
        if ((admux & 0xc0) != 0x40 || (admux & 0x20) != 0) {
            fault(state, AvrFault.Code.UNSUPPORTED_PERIPHERAL, "ADC requires AVcc reference and right adjustment");
        }
        int channel = admux & 31;
        if (channel >= AvrInputs.ANALOG_CHANNEL_COUNT) {
            fault(state, AvrFault.Code.UNSUPPORTED_PERIPHERAL, "unsupported ADC channel");
        }
        long microvolts = inputs.getAnalogMicrovolts(channel);
        long sample = microvolts * 1024L / 5000000L;
        state.adcSample = (int) Math.max(0, Math.min(1023, sample));
        int prescaler = 1 << Math.max(1, adcsra & 7);
        state.adcRunning = true; state.adcCompleteCycle = state.cycles + 13L * prescaler;
    }

    private static boolean implementedMmio(int a) {
        return (a >= 0x23 && a <= 0x2b) || (a >= 0x35 && a <= 0x37)
                || (a >= 0x44 && a <= 0x48) || (a >= 0x5d && a <= 0x5f)
                || (a >= 0x6e && a <= 0x70) || (a >= 0x78 && a <= 0x7a) || a == 0x7c
                || (a >= 0x80 && a <= 0x82) || (a >= 0x84 && a <= 0x85)
                || (a >= 0x88 && a <= 0x8b) || (a >= 0xb0 && a <= 0xb4)
                || (a >= 0xc0 && a <= 0xc6);
    }

    private static void validateTimerMode(AvrMachineState s, int address, int value) throws AvrFault {
        if ((address == 0x45 || address == 0x81) && (value & 7) >= 6) fault(s, AvrFault.Code.UNSUPPORTED_PERIPHERAL, "external timer clock");
        if (address == 0xb1 && (value & 0xc0) != 0) fault(s, AvrFault.Code.UNSUPPORTED_PERIPHERAL, "asynchronous Timer2 mode");
    }

    private static boolean isTimerRegister(int a) {
        return (a >= 0x44 && a <= 0x48) || (a >= 0x80 && a <= 0x82)
                || (a >= 0x84 && a <= 0x85) || (a >= 0x88 && a <= 0x8b)
                || (a >= 0xb0 && a <= 0xb4);
    }

    private static int timerControlAddress(int address) {
        return address < 0x50 ? 0x44 : address < 0xa0 ? 0x80 : 0xb0;
    }

    private static int timerControl(AvrMachineState s, int address) {
        int controlAddress = timerControlAddress(address);
        return s.mmio[controlAddress - 0x20] & 0xff;
    }

    private static int timerChannelCount(int control) {
        int count = 0;
        if ((control & 0xc0) != 0) count++;
        if ((control & 0x30) != 0) count++;
        return count;
    }

    private static void emitPwm(AvrMachineState s, int address, int oldControl, Output output) {
        int timer = address < 0x50 ? 0 : address < 0xa0 ? 1 : 2;
        int a = timer == 0 ? 0x44 : timer == 1 ? 0x80 : 0xb0;
        int b = timer == 0 ? 0x45 : timer == 1 ? 0x81 : 0xb1;
        int control = s.mmio[a - 0x20] & 0xff;
        int mode = (control & 3) | ((s.mmio[b - 0x20] >>> (timer == 1 ? 1 : 1)) & (timer == 1 ? 12 : 4));
        int prescaler = timerDivisor(s.mmio[b - 0x20] & 7, timer == 2);
        int ocrA = timer == 0 ? 0x47 : timer == 1 ? 0x88 : 0xb3;
        int ocrB = timer == 0 ? 0x48 : timer == 1 ? 0x8a : 0xb4;
        int pinA = timer == 0 ? 6 : timer == 1 ? 9 : 11;
        int pinB = timer == 0 ? 5 : timer == 1 ? 10 : 3;
        boolean phase = mode == 1 || mode == 5;
        if (((oldControl | control) & 0xc0) != 0) output.pwm.add(new PwmDescriptor(s.cycles, pinA, timer, mode,
                (control & 0xc0) == 0 ? 0 : prescaler, readOcr(s, ocrA, timer), phase));
        if (((oldControl | control) & 0x30) != 0) output.pwm.add(new PwmDescriptor(s.cycles, pinB, timer, mode,
                (control & 0x30) == 0 ? 0 : prescaler, readOcr(s, ocrB, timer), phase));
    }

    private static int readOcr(AvrMachineState s, int address, int timer) {
        int low = s.mmio[address - 0x20] & 0xff;
        return timer == 1 ? low | ((s.mmio[address + 1 - 0x20] & 0xff) << 8) : low;
    }

    private static int timerDivisor(int select, boolean timer2) {
        if (select == 0) return 0;
        if (!timer2) return new int[] { 0, 1, 8, 64, 256, 1024, 0, 0 }[select];
        return new int[] { 0, 1, 8, 32, 64, 128, 256, 1024 }[select];
    }

    private static int pinRegister(AvrMachineState s, int address, AvrInputs inputs) {
        int ddr = s.mmio[address + 1 - 0x20] & 0xff, port = s.mmio[address + 2 - 0x20] & 0xff;
        int base = address == 0x23 ? 8 : address == 0x26 ? 14 : 0, value = 0;
        for (int bit = 0; bit < 8; bit++) {
            int pin = pinForPort(base, bit);
            if (pin >= 0 && (((ddr & (1 << bit)) != 0 && (port & (1 << bit)) != 0)
                    || ((ddr & (1 << bit)) == 0
                    && (digitalInputHigh(s, inputs, pin) || (port & (1 << bit)) != 0)))) value |= 1 << bit;
        }
        return value;
    }

    private static boolean digitalInputHigh(AvrMachineState state, AvrInputs inputs, int pin) {
        UltrasonicPeripheral ultrasonic = inputs.getUltrasonic();
        if (ultrasonic != null && ultrasonic.isEnabled() && pin == ultrasonic.getEchoPin()
                && state.ultrasonicEchoStartCycle >= 0L
                && state.cycles >= state.ultrasonicEchoStartCycle
                && state.cycles < state.ultrasonicEchoEndCycle) return true;
        return inputs.isDigitalHigh(pin);
    }

    private static void configureUltrasonic(AvrMachineState state, UltrasonicPeripheral peripheral) {
        int trigger = peripheral == null ? -1 : peripheral.getTriggerPin();
        int echo = peripheral == null ? -1 : peripheral.getEchoPin();
        if (state.ultrasonicTriggerPin != trigger || state.ultrasonicEchoPin != echo
                || peripheral == null || !peripheral.isEnabled()) {
            state.ultrasonicTriggerPin = trigger;
            state.ultrasonicEchoPin = echo;
            state.ultrasonicTriggerHigh = false;
            state.ultrasonicTriggerRiseCycle = -1L;
            state.ultrasonicEchoStartCycle = -1L;
            state.ultrasonicEchoEndCycle = -1L;
        }
    }

    private static void observeUltrasonicTrigger(AvrMachineState state, UltrasonicPeripheral peripheral,
                                                 int pin, boolean output, boolean high) {
        if (peripheral == null || !peripheral.isEnabled() || pin != peripheral.getTriggerPin()) return;
        if (!output) {
            state.ultrasonicTriggerHigh = false;
            state.ultrasonicTriggerRiseCycle = -1L;
            return;
        }
        if (high && !state.ultrasonicTriggerHigh) {
            state.ultrasonicTriggerHigh = true;
            state.ultrasonicTriggerRiseCycle = state.cycles;
        } else if (!high && state.ultrasonicTriggerHigh) {
            long width = state.ultrasonicTriggerRiseCycle < 0L ? 0L
                    : state.cycles - state.ultrasonicTriggerRiseCycle;
            state.ultrasonicTriggerHigh = false;
            state.ultrasonicTriggerRiseCycle = -1L;
            if (width >= UltrasonicPeripheral.MIN_TRIGGER_CYCLES
                    && peripheral.getEchoDurationCycles() > 0L) {
                state.ultrasonicEchoStartCycle = state.cycles + UltrasonicPeripheral.ECHO_DELAY_CYCLES;
                state.ultrasonicEchoEndCycle = state.ultrasonicEchoStartCycle
                        + peripheral.getEchoDurationCycles();
            }
        }
    }

    private static int pinForPort(int base, int bit) {
        if (base == 8) return bit <= 5 ? 8 + bit : -1;
        if (base == 14) return bit <= 5 ? 14 + bit : -1;
        return bit;
    }

    private static int portMask(int address) {
        if (address <= 0x25) return 0x3f;
        if (address <= 0x28) return 0x3f;
        return 0xff;
    }

    private static int add(AvrMachineState s, int a, int b, int carry, boolean preserveZero) {
        int sum = a + b + carry, r = sum & 0xff;
        setFlag(s, H, ((a & 15) + (b & 15) + carry) > 15);
        setFlag(s, V, ((~(a ^ b) & (a ^ r)) & 0x80) != 0); setFlag(s, N, (r & 0x80) != 0);
        setFlag(s, S, flag(s, N) != flag(s, V)); setFlag(s, C, sum > 255);
        setFlag(s, Z, r == 0 && (!preserveZero || flag(s, Z))); return r;
    }

    private static int subtract(AvrMachineState s, int a, int b, int carry, boolean preserveZero, boolean store) {
        int r = (a - b - carry) & 0xff;
        setFlag(s, H, (((~a & b) | (b & r) | (r & ~a)) & 8) != 0);
        setFlag(s, V, (((a & ~b & ~r) | (~a & b & r)) & 0x80) != 0); setFlag(s, N, (r & 0x80) != 0);
        setFlag(s, S, flag(s, N) != flag(s, V)); setFlag(s, C, (((~a & b) | (b & r) | (r & ~a)) & 0x80) != 0);
        setFlag(s, Z, r == 0 && (!preserveZero || flag(s, Z))); return r;
    }

    private static void logic(AvrMachineState s, int d, int result) {
        result &= 0xff; s.registers[d] = (byte) result; setNz(s, result);
        setFlag(s, V, false); setFlag(s, S, flag(s, N));
    }

    private static void setNz(AvrMachineState s, int result) {
        setFlag(s, Z, (result & 0xff) == 0); setFlag(s, N, (result & 0x80) != 0);
    }

    private static int rd(int op) { return (op >>> 4) & 31; }
    private static int rr(int op) { return (op & 15) | ((op >>> 5) & 16); }
    private static int rdSingle(int op) { return (op >>> 4) & 31; }
    private static int immediate(int op) { return (op & 15) | ((op >>> 4) & 0xf0); }
    private static int displacement(int op) { return (op & 7) | ((op >>> 7) & 0x18) | ((op >>> 8) & 0x20); }
    private static int signed(int value, int bits) { int mask = (1 << bits) - 1; value &= mask; return (value & (1 << (bits - 1))) == 0 ? value : value - (1 << bits); }
    private static boolean isTwoWord(int op) { return (op & 0xfe0e) == 0x940c || (op & 0xfe0e) == 0x940e || (op & 0xfe0f) == 0x9000 || (op & 0xfe0f) == 0x9200; }
    private static boolean isLdYz(int op) { int masked = op & 0xd208; return masked == 0x8000 || masked == 0x8008; }
    private static boolean isStYz(int op) { int masked = op & 0xd208; return masked == 0x8200 || masked == 0x8208; }
    private static int u(AvrMachineState s, int r) { return s.registers[r] & 0xff; }
    private static int wordReg(AvrMachineState s, int r) { return u(s, r) | (u(s, r + 1) << 8); }
    private static void setWordReg(AvrMachineState s, int r, int value) { s.registers[r] = (byte) value; s.registers[r + 1] = (byte) (value >>> 8); }
    private static boolean flag(AvrMachineState s, int bit) { return (s.sreg & (1 << bit)) != 0; }
    private static void setFlag(AvrMachineState s, int bit, boolean value) { if (value) s.sreg |= 1 << bit; else s.sreg &= ~(1 << bit); }

    private static void validatePush(AvrMachineState s, int count) throws AvrFault {
        if (s.stackPointer < 0x100 || s.stackPointer > AvrMachineState.RAMEND || s.stackPointer - count + 1 < 0x100) fault(s, AvrFault.Code.MEMORY_FAULT, "stack overflow");
    }

    private static void validatePop(AvrMachineState s, int count) throws AvrFault {
        if (s.stackPointer < 0xff || s.stackPointer + count > AvrMachineState.RAMEND) fault(s, AvrFault.Code.MEMORY_FAULT, "stack underflow");
    }

    private static void push(AvrMachineState s, int value) { s.sram[s.stackPointer - 0x100] = (byte) value; s.stackPointer--; }
    private static int pop(AvrMachineState s) { s.stackPointer++; return s.sram[s.stackPointer - 0x100] & 0xff; }
    private static void pushReturn(AvrMachineState s, int pc) { push(s, pc); push(s, pc >>> 8); }
    private static int popReturn(AvrMachineState s) { int high = pop(s), low = pop(s); return low | (high << 8); }

    private static void ensureChanges(AvrMachineState s, Output output, int additional) throws AvrFault {
        if (output.gpio.size() + output.pwm.size() + additional > MAX_EFFECTIVE_CHANGES) fault(s, AvrFault.Code.FIRMWARE_OUTPUT_LIMIT, "GPIO/timer change limit exceeded");
    }

    private static void fault(AvrMachineState s, AvrFault.Code code, String message) throws AvrFault {
        throw new AvrFault(code, message, s.cycles, s.wordPc);
    }

    private static final class Output {
        final List<GpioChange> gpio = new ArrayList<GpioChange>();
        final List<PwmDescriptor> pwm = new ArrayList<PwmDescriptor>();
        final ByteArrayOutputStream tx = new ByteArrayOutputStream();
    }
}
