package br.com.craftonica.item;

import br.com.craftonica.block.WireColor;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class ItemBlockElectricalWire extends ItemBlock {
    public ItemBlockElectricalWire(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage & 15;
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return WireColor.rgb(stack.getItemDamage());
    }
}
