package br.com.craftonica.block;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.block.Block;
import net.minecraft.world.World;

/** One modular H-bridge channel. The dual-channel 1.2 block remains legacy-only. */
public final class BlockHBridgeChannel extends BlockRobotModule {
    public BlockHBridgeChannel() {
        super(Type.H_BRIDGE, "hBridgeChannel", "craftonica:h_bridge");
    }

    @Override public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z); invalidate(world, x, y, z);
    }
    @Override public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        invalidate(world, x, y, z);
    }
    @Override public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        invalidate(world, x, y, z); super.breakBlock(world, x, y, z, block, metadata);
    }

    private static void invalidate(World world, int x, int y, int z) {
        if (!world.isRemote) ElectricalNetworkManager.forWorld(world)
                .invalidateAround(new BlockPosition(x, y, z));
    }
}
