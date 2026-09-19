package br.com.craftonica.block;

import br.com.craftonica.electrical.nodal.DcComponentParameters;

public final class BlockPowerSource extends BlockSingleTerminal {
    /** 5 V source with a finite 10 ohm Thevenin resistance in the nodal model. */
    public static final double VOLTAGE = DcComponentParameters.SOURCE_VOLTAGE;
    public static final double INTERNAL_RESISTANCE_OHMS = DcComponentParameters.SOURCE_INTERNAL_RESISTANCE_OHMS;

    public BlockPowerSource() {
        super("powerSource", "craftonica:power_source");
    }
}
