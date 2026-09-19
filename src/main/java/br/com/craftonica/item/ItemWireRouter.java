package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.BlockElectricalWire;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityElectricalWire;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public final class ItemWireRouter extends Item {
    public ItemWireRouter() {
        setUnlocalizedName("wireRouter");
        setTextureName("craftonica:wire_router");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (!(world.getBlock(x, y, z) instanceof BlockElectricalWire)) return false;
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityElectricalWire)) {
            tile = new TileEntityElectricalWire();
            world.setTileEntity(x, y, z, tile);
        }
        boolean blocked = ((TileEntityElectricalWire) tile).toggleFace(side);
        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        manager.invalidateAround(new BlockPosition(x, y, z));
        int nx = x + dx(side), ny = y + dy(side), nz = z + dz(side);
        manager.invalidateAround(new BlockPosition(nx, ny, nz));
        world.markBlockForUpdate(x, y, z);
        world.notifyBlocksOfNeighborChange(x, y, z, world.getBlock(x, y, z));
        player.addChatMessage(new ChatComponentTranslation(blocked
                ? "message.craftonica.wire_router.blocked" : "message.craftonica.wire_router.open", side));
        return true;
    }

    private static int dx(int side) { return side == 4 ? -1 : side == 5 ? 1 : 0; }
    private static int dy(int side) { return side == 0 ? -1 : side == 1 ? 1 : 0; }
    private static int dz(int side) { return side == 2 ? -1 : side == 3 ? 1 : 0; }
}
