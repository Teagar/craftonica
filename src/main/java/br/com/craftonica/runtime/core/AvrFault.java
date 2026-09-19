package br.com.craftonica.runtime.core;

/** A deterministic firmware fault. The instruction which raises it is not committed. */
public final class AvrFault extends Exception {
    public enum Code {
        INVALID_OPCODE,
        INVALID_FETCH,
        MEMORY_FAULT,
        INVALID_PIN,
        UNSUPPORTED_ABI_TRAP,
        UNSUPPORTED_PERIPHERAL,
        FIRMWARE_OUTPUT_LIMIT,
        CHECKPOINT_INCOMPATIBLE
    }

    private final Code code;
    private final long cycle;
    private final int wordPc;

    public AvrFault(Code code, String message, long cycle, int wordPc) {
        super(message + " at word PC " + wordPc + ", cycle " + cycle);
        this.code = code;
        this.cycle = cycle;
        this.wordPc = wordPc;
    }

    public Code getCode() {
        return code;
    }

    public long getCycle() {
        return cycle;
    }

    public int getWordPc() {
        return wordPc;
    }
}
