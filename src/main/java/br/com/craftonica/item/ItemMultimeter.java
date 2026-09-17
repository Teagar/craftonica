package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.electrical.CircuitDiagnosis;
import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import br.com.craftonica.electrical.MultimeterMode;
import br.com.craftonica.electrical.MultimeterReading;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.electrical.nodal.BranchResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.Locale;

public final class ItemMultimeter extends Item {
    private static final String TAG_MODE = "CraftonicaMode";
    private static final String TAG_PROBE_SET = "CraftonicaProbeSet";
    private static final String TAG_PROBE_DIMENSION = "CraftonicaProbeDimension";
    private static final String TAG_PROBE_X = "CraftonicaProbeX";
    private static final String TAG_PROBE_Y = "CraftonicaProbeY";
    private static final String TAG_PROBE_Z = "CraftonicaProbeZ";
    private static final String TAG_PROBE_SIDE = "CraftonicaProbeSide";

    public ItemMultimeter() {
        setUnlocalizedName("multimeter");
        setTextureName("craftonica:multimeter");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                             int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                cycleMode(stack, player);
            }
            return true;
        }
        if (!(world.getBlock(x, y, z) instanceof IElectricalBlock)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }

        NBTTagCompound tag = tag(stack);
        BlockPosition position = new BlockPosition(x, y, z);
        if (!tag.getBoolean(TAG_PROBE_SET)) {
            setFirstProbe(tag, world.provider.dimensionId, position, side);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_a", x, y, z));
            return true;
        }

        if (tag.getInteger(TAG_PROBE_DIMENSION) != world.provider.dimensionId) {
            clearProbe(tag);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_expired"));
            return true;
        }

        BlockPosition first = new BlockPosition(tag.getInteger(TAG_PROBE_X), tag.getInteger(TAG_PROBE_Y),
                tag.getInteger(TAG_PROBE_Z));
        int firstSide = tag.getInteger(TAG_PROBE_SIDE);
        if (first.equals(position)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.same_probe"));
            return true;
        }
        if (!world.blockExists(first.x, first.y, first.z)
                || !(world.getBlock(first.x, first.y, first.z) instanceof IElectricalBlock)) {
            clearProbe(tag);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_expired"));
            return true;
        }

        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        CircuitResult result = manager.getResult(first);
        CircuitResult secondResult = manager.getResult(position);
        CircuitResult oversized = oversized(result) ? result : oversized(secondResult) ? secondResult : null;
        if (oversized != null) {
            player.addChatMessage(new ChatComponentTranslation(CircuitDiagnosis.translationKey(oversized)));
            clearProbe(tag);
            return true;
        }
        if (result == null || secondResult == null) {
            manager.invalidateAround(first);
            manager.invalidateAround(position);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.pending"));
            return true;
        }
        if (!manager.shareNetwork(first, position)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.different_networks"));
            return true;
        }

        player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_b", x, y, z));
        Double firstVoltage = manager.getTerminalVoltage(first, firstSide);
        Double secondVoltage = manager.getTerminalVoltage(position, side);
        BranchResult firstBranch = manager.getBranchResult(first);
        BranchResult secondBranch = manager.getBranchResult(position);
        if (firstVoltage == null || secondVoltage == null) {
            showUnsupported(player);
        } else {
            BranchResult branch = firstBranch != null && secondBranch == null ? firstBranch
                    : secondBranch != null && firstBranch == null ? secondBranch : null;
            CircuitResult local = measurement(mode(tag), firstVoltage, secondVoltage, branch, result);
            player.addChatMessage(new ChatComponentTranslation(CircuitDiagnosis.translationKey(local)));
            showReading(player, mode(tag), local);
        }
        clearProbe(tag);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking() && !world.isRemote) {
            cycleMode(stack, player);
        }
        return stack;
    }

    private void cycleMode(ItemStack stack, EntityPlayer player) {
        NBTTagCompound tag = tag(stack);
        MultimeterMode next = mode(tag).next();
        tag.setString(TAG_MODE, next.getId());
        clearProbe(tag);
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.mode",
                new ChatComponentTranslation("mode.craftonica.multimeter." + next.getId())));
    }

    private CircuitResult measurement(MultimeterMode mode, double firstVoltage, double secondVoltage,
                                      BranchResult branch, CircuitResult fallback) {
        double voltage = Math.abs(firstVoltage - secondVoltage);
        if (mode == MultimeterMode.VOLTAGE)
            return new CircuitResult(CircuitStatus.CLOSED, voltage, branch == null ? 0.0 : Math.abs(branch.getCurrent()), 0.0, "nodal_probe_voltage");
        if (branch == null)
            return new CircuitResult(CircuitStatus.UNSUPPORTED_TOPOLOGY, voltage, Double.NaN, Double.NaN, "ambiguous_or_no_branch");
        double current = Math.abs(branch.getCurrent());
        CircuitStatus status = current > 1e-12 ? CircuitStatus.CLOSED : CircuitStatus.OPEN_CIRCUIT;
        double resistance = current < 1e-15 ? Double.POSITIVE_INFINITY : Math.abs(branch.getVoltage() / branch.getCurrent());
        return new CircuitResult(status, voltage, current, resistance, "nodal_probe_branch");
    }

    private void showReading(EntityPlayer player, MultimeterMode mode, CircuitResult result) {
        MultimeterReading reading = MultimeterReading.fromNetworkResult(mode, result);
        switch (reading.getKind()) {
            case VALUE:
                player.addChatMessage(new ChatComponentTranslation(
                        "message.craftonica.multimeter.reading." + mode.getId(), decimal(reading.getValue(), 2)));
                break;
            case CONTINUITY:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.continuity_yes"));
                break;
            case NO_CONTINUITY:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.continuity_no"));
                break;
            case OVERCURRENT:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.no_resistor"));
                break;
            case UNSUPPORTED:
                showUnsupported(player);
                break;
            default:
                throw new IllegalStateException("Unknown multimeter reading: " + reading.getKind());
        }
    }

    private void showUnsupported(EntityPlayer player) {
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.unsupported_measurement"));
    }

    private MultimeterMode mode(NBTTagCompound tag) {
        return MultimeterMode.fromId(tag.getString(TAG_MODE));
    }

    private boolean oversized(CircuitResult result) {
        return result != null && result.getStatus() == CircuitStatus.NETWORK_TOO_LARGE;
    }

    private NBTTagCompound tag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    private void setFirstProbe(NBTTagCompound tag, int dimension, BlockPosition position, int side) {
        tag.setBoolean(TAG_PROBE_SET, true);
        tag.setInteger(TAG_PROBE_DIMENSION, dimension);
        tag.setInteger(TAG_PROBE_X, position.x);
        tag.setInteger(TAG_PROBE_Y, position.y);
        tag.setInteger(TAG_PROBE_Z, position.z);
        tag.setInteger(TAG_PROBE_SIDE, side);
    }

    private void clearProbe(NBTTagCompound tag) {
        tag.removeTag(TAG_PROBE_SET);
        tag.removeTag(TAG_PROBE_DIMENSION);
        tag.removeTag(TAG_PROBE_X);
        tag.removeTag(TAG_PROBE_Y);
        tag.removeTag(TAG_PROBE_Z);
        tag.removeTag(TAG_PROBE_SIDE);
    }

    private String decimal(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value);
    }
}
