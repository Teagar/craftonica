package br.com.craftonica.electrical;

public final class LedState {
    public static final int BURN_TICKS = 20;

    private boolean burned;
    private int overcurrentTicks;
    private int brightness;

    public boolean update(CircuitResult result) {
        boolean wasBurned = burned;
        int previousBrightness = brightness;
        if (burned || result == null) {
            brightness = 0;
            overcurrentTicks = 0;
        } else if (result.getStatus() == CircuitStatus.OVERCURRENT) {
            overcurrentTicks++;
            brightness = 15;
            if (overcurrentTicks >= BURN_TICKS) {
                burned = true;
                brightness = 0;
            }
        } else {
            overcurrentTicks = 0;
            brightness = result.getStatus() == CircuitStatus.CLOSED
                    ? Math.min(15, (int) Math.ceil(result.getCurrentAmps() / 0.020 * 15.0)) : 0;
        }
        return wasBurned != burned || previousBrightness != brightness;
    }

    public boolean isBurned() {
        return burned;
    }

    public void setBurned(boolean burned) {
        this.burned = burned;
        if (burned) {
            brightness = 0;
            overcurrentTicks = 0;
        }
    }

    public int getOvercurrentTicks() {
        return overcurrentTicks;
    }

    public int getBrightness() {
        return brightness;
    }

    public void setClientState(boolean burned, int brightness) {
        this.burned = burned;
        this.brightness = burned ? 0 : Math.max(0, Math.min(15, brightness));
    }
}
