package br.com.craftonica.runtime.core;

import java.util.Arrays;

/** Immutable electrical input sampled by the authoritative server. */
public final class AvrInputs {
    public static final int DIGITAL_PIN_COUNT = 20;
    public static final int ANALOG_CHANNEL_COUNT = 6;
    private final boolean[] digital;
    private final int[] analogMicrovolts;
    private final UltrasonicPeripheral ultrasonic;

    public AvrInputs(boolean[] digital, int[] analogMicrovolts) {
        this(digital, analogMicrovolts, null);
    }

    public AvrInputs(boolean[] digital, int[] analogMicrovolts, UltrasonicPeripheral ultrasonic) {
        if (digital == null || digital.length != DIGITAL_PIN_COUNT) {
            throw new IllegalArgumentException("digital inputs must contain 20 pins");
        }
        if (analogMicrovolts == null || analogMicrovolts.length != ANALOG_CHANNEL_COUNT) {
            throw new IllegalArgumentException("analog inputs must contain 6 channels");
        }
        this.digital = digital.clone();
        this.analogMicrovolts = analogMicrovolts.clone();
        this.ultrasonic = ultrasonic;
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

    public UltrasonicPeripheral getUltrasonic() { return ultrasonic; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AvrInputs)) {
            return false;
        }
        AvrInputs that = (AvrInputs) other;
        return Arrays.equals(digital, that.digital) && Arrays.equals(analogMicrovolts, that.analogMicrovolts)
                && sameUltrasonic(ultrasonic, that.ultrasonic);
    }

    @Override
    public int hashCode() {
        int result = 31 * Arrays.hashCode(digital) + Arrays.hashCode(analogMicrovolts);
        if (ultrasonic != null) {
            result = 31 * result + ultrasonic.getTriggerPin();
            result = 31 * result + ultrasonic.getEchoPin();
            result = 31 * result + (int) (ultrasonic.getEchoDurationCycles()
                    ^ ultrasonic.getEchoDurationCycles() >>> 32);
            result = 31 * result + (ultrasonic.isEnabled() ? 1 : 0);
        }
        return result;
    }

    private static boolean sameUltrasonic(UltrasonicPeripheral a, UltrasonicPeripheral b) {
        if (a == b) return true;
        return a != null && b != null && a.isEnabled() == b.isEnabled()
                && a.getTriggerPin() == b.getTriggerPin() && a.getEchoPin() == b.getEchoPin()
                && a.getEchoDurationCycles() == b.getEchoDurationCycles();
    }
}
