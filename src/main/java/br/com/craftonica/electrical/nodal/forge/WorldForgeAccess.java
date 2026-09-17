package br.com.craftonica.electrical.nodal.forge;

import br.com.craftonica.network.BlockPosition;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import br.com.craftonica.block.IElectricalBlock;

/** Forge 1.7.10 view; chunkExists is deliberately checked before any block read. */
public final class WorldForgeAccess implements ForgeWorldAccess {
    private final World world;

    public WorldForgeAccess(World world) {
        if (world == null) throw new IllegalArgumentException("Mundo nulo");
        this.world = world;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return world.getChunkProvider().chunkExists(chunkX, chunkZ);
    }

    @Override
    public Block getBlock(BlockPosition position) {
        return world.getBlock(position.x, position.y, position.z);
    }

    @Override
    public int getMetadata(BlockPosition position) {
        return world.getBlockMetadata(position.x, position.y, position.z);
    }

    @Override
    public TileEntity getTileEntity(BlockPosition position) {
        return world.getTileEntity(position.x, position.y, position.z);
    }

    @Override
    public boolean canConnectOnSide(Block block, BlockPosition position, int side) {
        return block instanceof IElectricalBlock
                && ((IElectricalBlock) block).canConnectOnSide(world, position.x, position.y, position.z, side);
    }
}
