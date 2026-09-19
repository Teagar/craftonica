package br.com.craftonica.item;

import br.com.craftonica.Craftonica;
import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class ItemManual extends Item {
    public ItemManual() {
        setUnlocalizedName("manual");
        setTextureName("craftonica:manual");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            Craftonica.proxy.openManual(player);
        }
        return stack;
    }
}
