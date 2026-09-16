package br.com.craftonica.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public final class BlockLed extends BlockTwoTerminal {
    public BlockLed() {
        super("led", "minecraft:redstone_lamp_off");
        setLightLevel(0.0F);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int anodeSide = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
        world.setBlockMetadataWithNotify(x, y, z, anodeSide, 2);
    }

    @Override
    public boolean canConnectOnSide(World world, int x, int y, int z, int side) {
        int anodeSide = world.getBlockMetadata(x, y, z) & 7;
        return side == anodeSide || side == opposite(anodeSide);
    }

    public int getAnodeSide(World world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) & 7;
    }

    private int opposite(int side) {
        return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4;
    }
}
