package br.com.craftonica.electrical.nodal.forge;

import br.com.craftonica.network.BlockPosition;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

/** Minimal world surface used by the Forge adapter and its pure test doubles. */
public interface ForgeWorldAccess {
    boolean isChunkLoaded(int chunkX, int chunkZ);
    Block getBlock(BlockPosition position);
    int getMetadata(BlockPosition position);
    TileEntity getTileEntity(BlockPosition position);
    boolean canConnectOnSide(Block block, BlockPosition position, int side);
}
