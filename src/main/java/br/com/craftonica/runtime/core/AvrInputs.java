package br.com.craftonica.runtime.core;

import java.util.Arrays;

/** Immutable electrical input sampled by the authoritative server. */
public final class AvrInputs {
    public static final int DIGITAL_PIN_COUNT = 20;
    public static final int ANALOG_CHANNEL_COUNT = 6;
    private final boolean[] digital;
    private final int[] analogMicrovolts;

    public AvrInputs(boolean[] digital, int[] analogMicrovolts) {
        if (digital == null || digital.length != DIGITAL_PIN_COUNT) {
            throw new IllegalArgumentException("digital inputs must contain 20 pins");
        }
        if (analogMicrovolts == null || analogMicrovolts.length != ANALOG_CHANNEL_COUNT) {
            throw new IllegalArgumentException("analog inputs must contain 6 channels");
        }
        this.digital = digital.clone();
        this.analogMicrovolts = analogMicrovolts.clone();
    }

    public static AvrInputs allLow() {
        return new AvrInputs(new boolean[DIGITAL_PIN_COUNT], new int[ANALOG_CHANNEL_COUNT]);
    }

    public boolean isDigitalHigh(int pin) {
        if (pin < 0 || pin >= digital.length) {
            throw new IndexOutOfBoundsException("pin");
        }
        return digital[pin];
    }

    public int getAnalogMicrovolts(int channel) {
        if (channel < 0 || channel >= analogMicrovolts.length) {
            throw new IndexOutOfBoundsException("channel");
        }
        return analogMicrovolts[channel];
    }

    public boolean[] getDigitalPins() {
        return digital.clone();
    }

    public int[] getAnalogMicrovolts() {
        return analogMicrovolts.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AvrInputs)) {
            return false;
        }
        AvrInputs that = (AvrInputs) other;
        return Arrays.equals(digital, that.digital) && Arrays.equals(analogMicrovolts, that.analogMicrovolts);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(digital) + Arrays.hashCode(analogMicrovolts);
    }
}
