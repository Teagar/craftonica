package br.com.craftonica.block;

import net.minecraft.world.IBlockAccess;

public interface IElectricalBlock {
    boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side);
}
