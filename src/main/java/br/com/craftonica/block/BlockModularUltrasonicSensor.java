package br.com.craftonica.block;

import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/** Modular HC-SR04 with bottom mount and four unobstructed electrical faces. */
public final class BlockModularUltrasonicSensor extends BlockUltrasonicSensor {
    public BlockModularUltrasonicSensor() {
        setBlockName("modularUltrasonicSensor");
    }

    @Override public IIcon getIcon(int side, int metadata) {
        int facing = normalizeFront(metadata & 7);
        if (side == facing) return getFrontIcon();
        if (side == 1) return getVccIcon();
        if (side == backOf(facing)) return getGroundIcon();
        if (side == leftOf(facing)) return getTriggerIcon();
        if (side == rightOf(facing)) return getEchoIcon();
        return getBodyIcon();
    }

    @Override public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int facing = normalizeFront(world.getBlockMetadata(x, y, z) & 7);
        return side == 1 || side == backOf(facing) || side == leftOf(facing) || side == rightOf(facing);
    }
}
