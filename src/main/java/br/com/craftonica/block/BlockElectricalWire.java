package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class BlockElectricalWire extends Block implements IElectricalBlock {
    public BlockElectricalWire() {
        super(Material.circuits);
        setBlockName("electricalWire");
        setBlockTextureName("minecraft:redstone_block");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(0.2F);
        setStepSound(soundTypeCloth);
    }

    @Override
    public boolean canConnectOnSide(World world, int x, int y, int z, int side) {
        return side >= 0 && side < 6;
    }

    public int getConnectionMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            Block neighbor = world.getBlock(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
            if (neighbor instanceof IElectricalBlock) {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int mask = getConnectionMask(world, x, y, z);
        float minX = (mask & 1 << 4) != 0 ? 0.0F : 0.375F;
        float maxX = (mask & 1 << 5) != 0 ? 1.0F : 0.625F;
        float minY = (mask & 1) != 0 ? 0.0F : 0.375F;
        float maxY = (mask & 1 << 1) != 0 ? 1.0F : 0.625F;
        float minZ = (mask & 1 << 2) != 0 ? 0.0F : 0.375F;
        float maxZ = (mask & 1 << 3) != 0 ? 1.0F : 0.625F;
        setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }
}
