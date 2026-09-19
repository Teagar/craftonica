package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** One physical, outward-facing electrical terminal linked to a RoboBoard. */
public final class BlockRoboPort extends BlockContainer implements IElectricalBlock {
    public BlockRoboPort() {
        super(Material.iron);
        setBlockName("roboPort");
        setBlockTextureName("craftonica:roboport");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.5F);
        setResistance(6.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityRoboPort();
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return side >= 0 && side < 6 && tile instanceof TileEntityRoboPort
                && ((TileEntityRoboPort) tile).getOutwardSide() == side;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return false;
        if (!world.isRemote) {
            TileEntityRoboPort port = (TileEntityRoboPort) tile;
            TileEntityRoboBoard board = port.getBoundBoard();
            if (board == null || !board.canAccess(player)) return false;
            try {
                port.cycleRole(port.getRevision());
                player.addChatMessage(new ChatComponentTranslation(
                        "message.craftonica.roboport.role", port.getRole().name()));
            } catch (IllegalStateException invalidBindingOrRevision) {
                return false;
            }
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
            world.markBlockForUpdate(x, y, z);
        }
        return true;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityRoboPort) ((TileEntityRoboPort) tile).bindToAdjacentBoard();
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, net.minecraft.block.Block neighbor) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityRoboPort) {
                TileEntityRoboPort port = (TileEntityRoboPort) tile;
                if (port.getOwnerBoardId() == null) port.bindToAdjacentBoard();
                else port.validateBinding();
            }
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        }
    }

    @Override public boolean isOpaqueCube() { return true; }
    @Override public boolean renderAsNormalBlock() { return true; }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return 0xFFFFFF;
        TileEntityRoboPort.Role role = ((TileEntityRoboPort) tile).getRole();
        if (role == TileEntityRoboPort.Role.GROUND) return 0x58C7D8;
        if (role == TileEntityRoboPort.Role.POWER_5V) return 0xE76B62;
        return 0xE3B657;
    }
}
