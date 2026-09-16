package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class BlockTwoTerminal extends Block implements IElectricalBlock {
    protected BlockTwoTerminal(String name, String texture) {
        super(Material.iron);
        setBlockName(name);
        setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.0F);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int axis = facing == 0 || facing == 2 ? 0 : 1;
        world.setBlockMetadataWithNotify(x, y, z, axis, 2);
    }

    @Override
    public boolean canConnectOnSide(World world, int x, int y, int z, int side) {
        int axis = world.getBlockMetadata(x, y, z) & 1;
        return axis == 0 ? side == 2 || side == 3 : side == 4 || side == 5;
    }
}
