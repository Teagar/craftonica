package br.com.craftonica.runtime.core;

public final class PwmDescriptor {
    private final long cycle;
    private final int pin;
    private final int timer;
    private final int mode;
    private final int prescaler;
    private final int compare;
    private final boolean phaseCorrect;

    PwmDescriptor(long cycle, int pin, int timer, int mode, int prescaler, int compare,
                  boolean phaseCorrect) {
        this.cycle = cycle;
        this.pin = pin;
        this.timer = timer;
        this.mode = mode;
        this.prescaler = prescaler;
        this.compare = compare;
        this.phaseCorrect = phaseCorrect;
    }

    public long getCycle() { return cycle; }
    public int getPin() { return pin; }
    public int getTimer() { return timer; }
    public int getMode() { return mode; }
    public int getPrescaler() { return prescaler; }
    public int getCompare() { return compare; }
    public boolean isPhaseCorrect() { return phaseCorrect; }
}
