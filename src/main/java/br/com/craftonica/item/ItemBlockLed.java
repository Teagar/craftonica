package br.com.craftonica.item;

import br.com.craftonica.block.WireColor;
import br.com.craftonica.tile.TileEntityLed;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;

public final class ItemBlockLed extends ItemBlock {
    public ItemBlockLed(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override public int getMetadata(int damage) { return 0; }

    @Override
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return WireColor.rgb(stack.getItemDamage());
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List items) {
        for (int color = 0; color < 16; color++) items.add(new ItemStack(item, 1, color));
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
                                World world, int x, int y, int z, int side,
                                float hitX, float hitY, float hitZ, int metadata) {
        boolean placed = super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
        if (placed && !world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityLed) {
                ((TileEntityLed) tile).setColor(stack.getItemDamage());
                world.markBlockForUpdate(x, y, z);
            }
        }
        return placed;
    }
}
