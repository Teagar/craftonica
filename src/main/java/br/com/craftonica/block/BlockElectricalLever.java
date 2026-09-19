package br.com.craftonica.block;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityElectricalLever;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/** Persistent normally-open/closed lever, distinct from the momentary button. */
public final class BlockElectricalLever extends BlockTwoTerminal {
    private static final int CLOSED_BIT = 2;
    public BlockElectricalLever() { super("electricalLever", "craftonica:lever"); }
    @Override public IIcon getBodyIcon(int metadata) { return bodyIcon; }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        int metadata = world.getBlockMetadata(x, y, z) ^ CLOSED_BIT;
        world.setBlockMetadataWithNotify(x, y, z, metadata, 3);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityElectricalLever) ((TileEntityElectricalLever) tile).setClosed((metadata & CLOSED_BIT) != 0);
        ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        return true;
    }
    @Override public boolean hasTileEntity(int metadata) { return true; }
    @Override public TileEntity createTileEntity(World world, int metadata) { return new TileEntityElectricalLever(); }
    public boolean isClosed(World world, int x, int y, int z) { return (world.getBlockMetadata(x, y, z) & CLOSED_BIT) != 0; }
}
