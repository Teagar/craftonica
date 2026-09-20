package br.com.craftonica.runtime.core;

/** One bounded HC-SR04 peripheral attached to two logical Arduino pins. */
public final class UltrasonicPeripheral {
    public static final long ECHO_DELAY_CYCLES = 3200L;
    public static final long MIN_TRIGGER_CYCLES = 160L;
    private final boolean enabled;
    private final int triggerPin;
    private final int echoPin;
    private final long echoDurationCycles;

    public UltrasonicPeripheral(boolean enabled, int triggerPin, int echoPin, long echoDurationCycles) {
        if (triggerPin < 0 || triggerPin >= AvrInputs.DIGITAL_PIN_COUNT
                || echoPin < 0 || echoPin >= AvrInputs.DIGITAL_PIN_COUNT
                || triggerPin == echoPin || echoDurationCycles < 0 || echoDurationCycles > 10000000L)
            throw new IllegalArgumentException("invalid ultrasonic peripheral");
        this.enabled = enabled;
        this.triggerPin = triggerPin;
        this.echoPin = echoPin;
        this.echoDurationCycles = echoDurationCycles;
    }

    public boolean isEnabled() { return enabled; }
    public int getTriggerPin() { return triggerPin; }
    public int getEchoPin() { return echoPin; }
    public long getEchoDurationCycles() { return echoDurationCycles; }
}
