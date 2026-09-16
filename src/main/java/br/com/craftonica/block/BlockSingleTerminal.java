package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class BlockSingleTerminal extends Block implements IElectricalBlock {
    protected BlockSingleTerminal(String name, String texture) {
        super(Material.iron);
        setBlockName(name);
        setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.5F);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int side = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
        world.setBlockMetadataWithNotify(x, y, z, side, 2);
    }

    @Override
    public boolean canConnectOnSide(World world, int x, int y, int z, int side) {
        return (world.getBlockMetadata(x, y, z) & 7) == side;
    }
}
