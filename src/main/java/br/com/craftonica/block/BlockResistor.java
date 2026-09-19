package br.com.craftonica.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

public final class BlockResistor extends BlockTwoTerminal {
    private final double resistanceOhms;
    private IIcon bandIcon;

    public BlockResistor(String name, String texture, double resistanceOhms) {
        super(name, texture);
        this.resistanceOhms = resistanceOhms;
    }

    public double getResistanceOhms() {
        return resistanceOhms;
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        bodyIcon = register.registerIcon("craftonica:resistor_body");
        bandIcon = register.registerIcon("craftonica:resistor_band");
        terminalIcon = register.registerIcon("craftonica:terminal_neutral");
        blockIcon = bodyIcon;
    }

    public IIcon getBandIcon() {
        return bandIcon;
    }

    public int[] getBandColors() {
        if (resistanceOhms < 500.0) return new int[] {0xD32F2F, 0xD32F2F, 0x6D3B1F, 0xD4AF37};
        if (resistanceOhms < 5000.0) return new int[] {0x6D3B1F, 0x171717, 0xD32F2F, 0xD4AF37};
        return new int[] {0x6D3B1F, 0x171717, 0xEF6C00, 0xD4AF37};
    }
}
