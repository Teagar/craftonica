package br.com.craftonica.runtime.core;

import java.util.Arrays;

/** Mutable ATmega328P execution state. It has no references to host or Forge state. */
public final class AvrMachineState {
    public static final int SRAM_SIZE = 2048;
    public static final int EEPROM_SIZE = 1024;
    public static final int RAMEND = 0x08ff;

    final byte[] registers = new byte[32];
    final byte[] sram = new byte[SRAM_SIZE];
    final byte[] eeprom = new byte[EEPROM_SIZE];
    final byte[] mmio = new byte[0xe0]; // Data-space addresses 0x20..0xff.
    int sreg;
    int stackPointer = RAMEND;
    int wordPc;
    long cycles;
    int timer0PrescaleCycles;
    int timer1PrescaleCycles;
    int timer2PrescaleCycles;
    boolean adcRunning;
    long adcCompleteCycle;
    int adcSample;

    public AvrMachineState() {
        mmio[0xc0 - 0x20] = 0x20; // UCSR0A: transmitter data register is empty.
    }

    public int getRegister(int register) {
        checkRegister(register);
        return registers[register] & 0xff;
    }

    public void setRegister(int register, int value) {
        checkRegister(register);
        registers[register] = (byte) value;
    }

    public byte[] getRegisters() { return registers.clone(); }
    public byte[] getSram() { return sram.clone(); }
    public byte[] getEeprom() { return eeprom.clone(); }
    public int getSramByte(int offset) {
        checkIndex(offset, SRAM_SIZE, "SRAM");
        return sram[offset] & 0xff;
    }
    public void setSramByte(int offset, int value) {
        checkIndex(offset, SRAM_SIZE, "SRAM");
        sram[offset] = (byte) value;
    }
    public int getEepromByte(int offset) {
        checkIndex(offset, EEPROM_SIZE, "EEPROM");
        return eeprom[offset] & 0xff;
    }
    public void setEepromByte(int offset, int value) {
        checkIndex(offset, EEPROM_SIZE, "EEPROM");
        eeprom[offset] = (byte) value;
    }
    public int getSreg() { return sreg; }
    public void setSreg(int value) { sreg = value & 0xff; }
    public int getStackPointer() { return stackPointer; }

    public void setStackPointer(int stackPointer) {
        if (stackPointer < 0x100 || stackPointer > RAMEND) {
            throw new IllegalArgumentException("stack pointer outside SRAM");
        }
        this.stackPointer = stackPointer;
    }

    public int getWordPc() { return wordPc; }

    public void setWordPc(int wordPc) {
        if (wordPc < 0) {
            throw new IllegalArgumentException("negative PC");
        }
        this.wordPc = wordPc;
    }

    public long getCycles() { return cycles; }

    public void setCycles(long cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("negative cycle count");
        }
        this.cycles = cycles;
    }

    /** Reads raw implemented MMIO for diagnostics and tests. */
    public int getMmio(int dataAddress) {
        if (dataAddress < 0x20 || dataAddress > 0xff) {
            throw new IllegalArgumentException("not MMIO");
        }
        return mmio[dataAddress - 0x20] & 0xff;
    }

    public AvrMachineState copy() {
        AvrMachineState copy = new AvrMachineState();
        copyFromInto(this, copy);
        return copy;
    }

    void replaceWith(AvrMachineState source) {
        copyFromInto(source, this);
    }

    private static void copyFromInto(AvrMachineState source, AvrMachineState target) {
        System.arraycopy(source.registers, 0, target.registers, 0, source.registers.length);
        System.arraycopy(source.sram, 0, target.sram, 0, source.sram.length);
        System.arraycopy(source.eeprom, 0, target.eeprom, 0, source.eeprom.length);
        System.arraycopy(source.mmio, 0, target.mmio, 0, source.mmio.length);
        target.sreg = source.sreg;
        target.stackPointer = source.stackPointer;
        target.wordPc = source.wordPc;
        target.cycles = source.cycles;
        target.timer0PrescaleCycles = source.timer0PrescaleCycles;
        target.timer1PrescaleCycles = source.timer1PrescaleCycles;
        target.timer2PrescaleCycles = source.timer2PrescaleCycles;
        target.adcRunning = source.adcRunning;
        target.adcCompleteCycle = source.adcCompleteCycle;
        target.adcSample = source.adcSample;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AvrMachineState)) {
            return false;
        }
        AvrMachineState that = (AvrMachineState) other;
        return sreg == that.sreg && stackPointer == that.stackPointer && wordPc == that.wordPc
                && cycles == that.cycles && timer0PrescaleCycles == that.timer0PrescaleCycles
                && timer1PrescaleCycles == that.timer1PrescaleCycles
                && timer2PrescaleCycles == that.timer2PrescaleCycles
                && adcRunning == that.adcRunning && adcCompleteCycle == that.adcCompleteCycle
                && adcSample == that.adcSample && Arrays.equals(registers, that.registers)
                && Arrays.equals(sram, that.sram) && Arrays.equals(eeprom, that.eeprom)
                && Arrays.equals(mmio, that.mmio);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(registers);
        result = 31 * result + wordPc;
        result = 31 * result + (int) (cycles ^ (cycles >>> 32));
        return result;
    }

    private static void checkRegister(int register) {
        if (register < 0 || register >= 32) {
            throw new IndexOutOfBoundsException("register");
        }
    }

    private static void checkIndex(int index, int length, String name) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(name + " offset");
        }
    }
}
