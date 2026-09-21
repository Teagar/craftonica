package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** One physical terminal bound by adjacency to exactly one H-bridge core. */
public final class BlockHBridgeTerminal extends Block implements IElectricalBlock {
    public BlockHBridgeTerminal() {
        super(Material.iron);
        setBlockName("hBridgeTerminal"); setBlockTextureName("craftonica:roboport");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.2F); setResistance(4.0F);
    }

    @Override public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        return side >= 0 && side < 6 && outwardSide(world, x, y, z) == side;
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

    public static int outwardSide(IBlockAccess world, int x, int y, int z) {
        if (world == null) return -1;
        int found = -1;
        for (int side = 0; side < 6; side++) {
            int nx = x + dx(side), ny = y + dy(side), nz = z + dz(side);
            if (world instanceof World && !((World) world).getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
            Block neighbor = world.getBlock(nx, ny, nz);
            if (!(neighbor instanceof BlockHBridgeChannel)) continue;
            if (found >= 0) return -1;
            found = opposite(side);
        }
        return found;
    }

    private static int opposite(int side) { return side == 0 ? 1 : side == 1 ? 0 : side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
    private static int dx(int side) { return side == 4 ? -1 : side == 5 ? 1 : 0; }
    private static int dy(int side) { return side == 0 ? -1 : side == 1 ? 1 : 0; }
    private static int dz(int side) { return side == 2 ? -1 : side == 3 ? 1 : 0; }
    private static void invalidate(World world, int x, int y, int z) {
        if (!world.isRemote) ElectricalNetworkManager.forWorld(world)
                .invalidateAround(new BlockPosition(x, y, z));
    }
}
