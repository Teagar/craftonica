package br.com.craftonica.block;

public final class BlockLed extends BlockTwoTerminal {
    public BlockLed() {
        super("led", "minecraft:redstone_lamp_off");
        setLightLevel(0.0F);
    }
}
