package br.com.craftonica.block;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityPotentiometer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Three-terminal potentiometer: A, cursor and B, with a fixed nominal track. */
public final class BlockPotentiometer extends BlockTwoTerminal {
    public static final int NOMINAL_RESISTANCE_OHMS = 10000;
    public static final int MIN_RESISTANCE_OHMS = 1;
    public static final int MAX_STEP = 100;
    private static final int AXIS_BIT = 1;

    public BlockPotentiometer() {
        super("potentiometer", "craftonica:potentiometer");
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int axis = world.getBlockMetadata(x, y, z) & AXIS_BIT;
        return axis == 0 ? side == 2 || side == 3 || side == 1 : side == 4 || side == 5 || side == 1;
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        return side == 1 ? terminalIcon : super.getIcon(side, metadata);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (!world.isRemote && world.getTileEntity(x, y, z) == null) {
            world.setTileEntity(x, y, z, new TileEntityPotentiometer());
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityPotentiometer)) return false;
        TileEntityPotentiometer potentiometer = (TileEntityPotentiometer) tile;
        int delta = player.isSneaking() ? -10 : 10;
        if (side == 1 || side == 0) delta = player.isSneaking() ? -1 : 1;
        if (potentiometer.adjust(delta)) {
            world.markBlockForUpdate(x, y, z);
            world.notifyBlockChange(x, y, z, this);
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        }
        return true;
    }

    @Override public boolean hasTileEntity(int metadata) { return true; }
    @Override public TileEntity createTileEntity(World world, int metadata) { return new TileEntityPotentiometer(); }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if ((world.getBlockMetadata(x, y, z) & AXIS_BIT) == 0)
            setBlockBounds(0.125F, 0.1875F, 0.0F, 0.875F, 0.8125F, 1.0F);
        else
            setBlockBounds(0.0F, 0.1875F, 0.125F, 1.0F, 0.8125F, 0.875F);
    }
}
