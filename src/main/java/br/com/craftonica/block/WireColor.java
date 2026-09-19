package br.com.craftonica.block;

public final class WireColor {
    private static final int[] RGB = {
            0x1D1D21, 0xB02E26, 0x5E7C16, 0x835432,
            0x3C44AA, 0x8932B8, 0x169C9C, 0x9D9D97,
            0x474F52, 0xF38BAA, 0x80C71F, 0xFED83D,
            0x3AB3DA, 0xC74EBD, 0xF9801D, 0xF9FFFE
    };

    private WireColor() {
    }

    public static int rgb(int dyeDamage) {
        return RGB[dyeDamage & 15];
    }
}
