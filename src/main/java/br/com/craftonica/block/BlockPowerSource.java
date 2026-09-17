package br.com.craftonica.block;

public final class BlockPowerSource extends BlockSingleTerminal {
    /** 5 V source with a finite 10 ohm Thevenin resistance in the nodal model. */
    public static final double VOLTAGE = 5.0;
    public static final double INTERNAL_RESISTANCE_OHMS = 10.0;

    public BlockPowerSource() {
        super("powerSource", "craftonica:power_source");
    }
}
