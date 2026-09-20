package br.com.craftonica.block;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class BlockLedVisualTest {
    @Test
    public void inactiveGlassIsLessSaturatedButKeepsVariantIdentity() {
        int red = WireColor.rgb(1);
        int blue = WireColor.rgb(4);
        int inactiveRed = BlockLed.inactiveColor(red);
        int inactiveBlue = BlockLed.inactiveColor(blue);

        assertTrue(saturation(inactiveRed) < saturation(red));
        assertTrue(saturation(inactiveBlue) < saturation(blue));
        assertNotEquals(inactiveRed, inactiveBlue);
    }

    @Test
    public void inactiveEmitterRemainsVisibleWithoutLookingPowered() {
        int active = WireColor.rgb(1);
        int inactive = BlockLed.inactiveCoreColor(active);

        assertTrue(channel(inactive, 16) > 36);
        assertTrue(channel(inactive, 16) < channel(active, 16));
        assertTrue(channel(inactive, 8) >= 36);
        assertTrue(channel(inactive, 0) >= 36);
    }

    private int saturation(int rgb) {
        int r = channel(rgb, 16), g = channel(rgb, 8), b = channel(rgb, 0);
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private int channel(int rgb, int shift) {
        return rgb >> shift & 255;
    }
}
