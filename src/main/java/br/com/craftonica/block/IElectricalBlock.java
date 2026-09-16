package br.com.craftonica.block;

import net.minecraft.world.World;

public interface IElectricalBlock {
    boolean canConnectOnSide(World world, int x, int y, int z, int side);
}
