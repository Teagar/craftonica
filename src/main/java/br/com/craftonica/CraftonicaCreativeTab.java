package br.com.craftonica;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

public final class CraftonicaCreativeTab extends CreativeTabs {
    public static final CraftonicaCreativeTab INSTANCE = new CraftonicaCreativeTab();

    private CraftonicaCreativeTab() {
        super(Craftonica.MOD_ID);
    }

    @Override
    public Item getTabIconItem() {
        return Items.redstone;
    }
}
