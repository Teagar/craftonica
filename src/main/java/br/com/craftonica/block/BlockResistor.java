package br.com.craftonica.block;

public final class BlockResistor extends BlockTwoTerminal {
    private final double resistanceOhms;

    public BlockResistor(String name, String texture, double resistanceOhms) {
        super(name, texture);
        this.resistanceOhms = resistanceOhms;
    }

    public double getResistanceOhms() {
        return resistanceOhms;
    }
}
