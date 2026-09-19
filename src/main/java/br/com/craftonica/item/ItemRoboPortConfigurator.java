package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.BlockRoboBoard;
import br.com.craftonica.block.BlockRoboPort;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tool.network.ToolActionMessage;
import br.com.craftonica.tool.network.ToolNetwork;
import br.com.craftonica.tool.network.ToolStateMessage;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, null, "tool.craftonica.configurator.ready", "");
        return stack;
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
            if (player instanceof EntityPlayerMP)
                sendState(stack, (EntityPlayerMP) player, null, "tool.craftonica.configurator.cleared", "");
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
        if (player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, null, "tool.craftonica.configurator.selected", "");
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
            if (player instanceof EntityPlayerMP)
                sendState(stack, (EntityPlayerMP) player, null,
                        "tool.craftonica.configurator.no_selection", "");
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
        }
        ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        world.markBlockForUpdate(x, y, z);
        if (player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, port,
                    "tool.craftonica.configurator.port", "");
        return true;
    }

    public void handleGuiAction(ItemStack stack, EntityPlayerMP player, ToolActionMessage action) {
        if (action.getAction() == br.com.craftonica.tool.network.ToolAction.ROBOPORT_CLEAR_BOARD) {
            if (stack.hasTagCompound()) stack.getTagCompound().removeTag(SELECTION);
            sendState(stack, player, null, "tool.craftonica.configurator.cleared", "");
            return;
        }
        if (action.getAction() != br.com.craftonica.tool.network.ToolAction.ROBOPORT_ROLE) return;
        if (player.getDistanceSq(action.getX() + 0.5, action.getY() + 0.5, action.getZ() + 0.5) > 4096.0)
            return;
        TileEntity tile = player.worldObj.getTileEntity(action.getX(), action.getY(), action.getZ());
        if (!(tile instanceof TileEntityRoboPort)) return;
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        TileEntityRoboBoard board = selectedBoard(stack, player.worldObj);
        int ordinal = action.getValue();
        if (board == null || !board.canAccess(player) || !port.isBoundTo(board)
                || ordinal < 0 || ordinal >= TileEntityRoboPort.Role.values().length) return;
        try {
            port.setRole(TileEntityRoboPort.Role.values()[ordinal], action.getRevision());
            ElectricalNetworkManager.forWorld(player.worldObj).invalidateAround(
                    new BlockPosition(port.xCoord, port.yCoord, port.zCoord));
            player.worldObj.markBlockForUpdate(port.xCoord, port.yCoord, port.zCoord);
            sendState(stack, player, port, "tool.craftonica.configurator.role_changed", "");
        } catch (IllegalStateException rejected) {
            sendState(stack, player, port, "tool.craftonica.configurator.role_unavailable", "");
        }
    }

    private void sendState(ItemStack stack, EntityPlayerMP player, TileEntityRoboPort port,
                           String headline, String detail) {
        TileEntityRoboBoard board = selectedBoard(stack, player.worldObj);
        ToolNetwork.sendTo(player, ToolStateMessage.roboPort(player.worldObj.provider.dimensionId,
                board != null, board == null ? 0 : board.xCoord, board == null ? 0 : board.yCoord,
                board == null ? 0 : board.zCoord,
                port != null, port == null ? 0 : port.xCoord, port == null ? 0 : port.yCoord,
                port == null ? 0 : port.zCoord, port == null ? 0 : port.getRole().ordinal(),
                port == null ? 0 : port.getRevision(), headline, detail));
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
