package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.BlockRoboBoard;
import br.com.craftonica.block.BlockRoboPort;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.UUID;

public final class ItemRoboPortConfigurator extends Item {
    private static final String SELECTION = "SelectedRoboBoard";

    public ItemRoboPortConfigurator() {
        setUnlocalizedName("roboPortConfigurator");
        setTextureName("craftonica:roboport_configurator");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlockRoboBoard) && !(block instanceof BlockRoboPort)) return false;
        if (world.isRemote) return true;
        if (block instanceof BlockRoboBoard) return selectBoard(stack, player, world, x, y, z);
        return configurePort(stack, player, world, x, y, z, side);
    }

    private boolean selectBoard(ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboBoard)) return false;
        TileEntityRoboBoard board = (TileEntityRoboBoard) tile;
        if (!board.canAccess(player)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.roboport_configurator.denied"));
            return true;
        }
        if (player.isSneaking()) {
            if (stack.hasTagCompound()) stack.getTagCompound().removeTag(SELECTION);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.roboport_configurator.cleared"));
            return true;
        }
        UUID id = board.getBoardId();
        NBTTagCompound selected = new NBTTagCompound();
        selected.setInteger("Dimension", world.provider.dimensionId);
        selected.setInteger("X", x); selected.setInteger("Y", y); selected.setInteger("Z", z);
        selected.setLong("Most", id.getMostSignificantBits());
        selected.setLong("Least", id.getLeastSignificantBits());
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setTag(SELECTION, selected);
        player.addChatMessage(new ChatComponentTranslation(
                "message.craftonica.roboport_configurator.selected", x, y, z));
        return true;
    }

    private boolean configurePort(ItemStack stack, EntityPlayer player, World world,
                                  int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return false;
        TileEntityRoboBoard board = selectedBoard(stack, world);
        if (board == null) {
            player.addChatMessage(new ChatComponentTranslation(
                    "message.craftonica.roboport_configurator.no_selection"));
            return true;
        }
        if (!board.canAccess(player)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.roboport_configurator.denied"));
            return true;
        }
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        if (!port.isBoundTo(board)) {
            if (!port.bindToBoard(board, side)) {
                player.addChatMessage(new ChatComponentTranslation(
                        "message.craftonica.roboport_configurator.too_far",
                        TileEntityRoboPort.MAX_LINK_DISTANCE));
                return true;
            }
            player.addChatMessage(new ChatComponentTranslation(
                    "message.craftonica.roboport_configurator.bound", port.getRole().name()));
        } else {
            try {
                port.cycleRole(port.getRevision());
                player.addChatMessage(new ChatComponentTranslation(
                        "message.craftonica.roboport_configurator.role", port.getRole().name()));
            } catch (IllegalStateException invalidState) {
                player.addChatMessage(new ChatComponentTranslation(
                        "message.craftonica.roboport_configurator.invalid"));
                return true;
            }
        }
        ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    private TileEntityRoboBoard selectedBoard(ItemStack stack, World world) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(SELECTION)) return null;
        NBTTagCompound selected = stack.getTagCompound().getCompoundTag(SELECTION);
        if (selected.getInteger("Dimension") != world.provider.dimensionId
                || !selected.hasKey("Most") || !selected.hasKey("Least")) return null;
        int x = selected.getInteger("X"), y = selected.getInteger("Y"), z = selected.getInteger("Z");
        if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        UUID selectedId = new UUID(selected.getLong("Most"), selected.getLong("Least"));
        return tile instanceof TileEntityRoboBoard && selectedId.equals(((TileEntityRoboBoard) tile).getBoardId())
                ? (TileEntityRoboBoard) tile : null;
    }
}
