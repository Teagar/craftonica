package br.com.craftonica.runtime.core;

public final class GpioChange {
    private final long cycle;
    private final int pin;
    private final boolean output;
    private final boolean high;

    GpioChange(long cycle, int pin, boolean output, boolean high) {
        this.cycle = cycle;
        this.pin = pin;
        this.output = output;
        this.high = high;
    }

    public long getCycle() { return cycle; }
    public int getPin() { return pin; }
    public boolean isOutput() { return output; }
    public boolean isHigh() { return high; }
}
