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
import br.com.craftonica.electrical.nodal.ResistanceMeasurement;
import br.com.craftonica.tool.network.ToolAction;
import br.com.craftonica.tool.network.ToolActionMessage;
import br.com.craftonica.tool.network.ToolNetwork;
import br.com.craftonica.tool.network.ToolStateMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.Locale;
import br.com.craftonica.persistence.NbtMigrations;

public final class ItemMultimeter extends Item {
    private static final String TAG_MODE = "CraftonicaMode";
    private static final String TAG_PROBE_SET = "CraftonicaProbeSet";
    private static final String TAG_PROBE_DIMENSION = "CraftonicaProbeDimension";
    private static final String TAG_PROBE_X = "CraftonicaProbeX";
    private static final String TAG_PROBE_Y = "CraftonicaProbeY";
    private static final String TAG_PROBE_Z = "CraftonicaProbeZ";
    private static final String TAG_PROBE_SIDE = "CraftonicaProbeSide";
    private static final String TAG_DATA_VERSION = "CraftonicaDataVersion";

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
            if (!world.isRemote && player instanceof EntityPlayerMP)
                sendState(stack, (EntityPlayerMP) player, false, 0, 0, 0, 0,
                        "tool.craftonica.multimeter.ready", "");
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
            if (player instanceof EntityPlayerMP)
                sendState(stack, (EntityPlayerMP) player, false, 0, 0, 0, 0,
                        "tool.craftonica.multimeter.probe_a", "");
            return true;
        }

        if (tag.getInteger(TAG_PROBE_DIMENSION) != world.provider.dimensionId) {
            clearProbe(tag);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_expired"));
            sendIfPossible(stack, player, "message.craftonica.multimeter.probe_expired", "");
            return true;
        }

        BlockPosition first = new BlockPosition(tag.getInteger(TAG_PROBE_X), tag.getInteger(TAG_PROBE_Y),
                tag.getInteger(TAG_PROBE_Z));
        int firstSide = tag.getInteger(TAG_PROBE_SIDE);
        if (firstSide < 0 || firstSide > 5
                || player.getDistanceSq(first.x + 0.5, first.y + 0.5, first.z + 0.5) > 64.0) {
            clearProbe(tag);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_expired"));
            sendIfPossible(stack, player, "message.craftonica.multimeter.probe_expired", "");
            return true;
        }
        if (first.equals(position) && firstSide == side) {
            sendIfPossible(stack, player, "message.craftonica.multimeter.same_probe", "");
            return true;
        }
        if (!world.blockExists(first.x, first.y, first.z)
                || !(world.getBlock(first.x, first.y, first.z) instanceof IElectricalBlock)) {
            clearProbe(tag);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.probe_expired"));
            sendIfPossible(stack, player, "message.craftonica.multimeter.probe_expired", "");
            return true;
        }

        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        CircuitResult result = manager.getResult(first);
        CircuitResult secondResult = manager.getResult(position);
        CircuitResult oversized = oversized(result) ? result : oversized(secondResult) ? secondResult : null;
        if (oversized != null) {
            player.addChatMessage(new ChatComponentTranslation(CircuitDiagnosis.translationKey(oversized)));
            sendIfPossible(stack, player, CircuitDiagnosis.translationKey(oversized), "");
            clearProbe(tag);
            return true;
        }
        if (result == null || secondResult == null) {
            manager.invalidateAround(first);
            manager.invalidateAround(position);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.pending"));
            sendIfPossible(stack, player, "message.craftonica.multimeter.pending", "");
            return true;
        }
        if (!manager.shareNetwork(first, position)) {
            sendIfPossible(stack, player, "message.craftonica.multimeter.different_networks", "");
            return true;
        }

        MultimeterMode selectedMode = mode(tag);
        if (selectedMode == MultimeterMode.RESISTANCE || selectedMode == MultimeterMode.CONTINUITY) {
            ResistanceMeasurement resistance = manager.measureResistance(first, firstSide, position, side);
            if (resistance.getStatus() == ResistanceMeasurement.Status.NONLINEAR_UNSUPPORTED) {
                sendMeasurement(stack, player, position, side,
                        "message.craftonica.multimeter.nonlinear_unsupported", "");
            } else if (resistance.getStatus() == ResistanceMeasurement.Status.UNAVAILABLE) {
                sendMeasurement(stack, player, position, side,
                        "message.craftonica.multimeter.unsupported_measurement", "");
            } else {
                CircuitResult local = resistanceMeasurement(resistance);
                sendMeasurement(stack, player, position, side, CircuitDiagnosis.translationKey(local),
                        readingPayload(selectedMode, local));
            }
            clearProbe(tag);
            return true;
        }
        Double firstVoltage = manager.getTerminalVoltage(first, firstSide);
        Double secondVoltage = manager.getTerminalVoltage(position, side);
        BranchResult firstBranch = manager.getBranchResult(first);
        BranchResult secondBranch = manager.getBranchResult(position);
        if (firstVoltage == null || secondVoltage == null) {
            sendMeasurement(stack, player, position, side,
                    "message.craftonica.multimeter.unsupported_measurement", "");
        } else {
            BranchResult branch = first.equals(position) ? firstBranch
                    : firstBranch != null && secondBranch == null ? firstBranch
                    : secondBranch != null && firstBranch == null ? secondBranch : null;
            CircuitResult local = measurement(selectedMode, firstVoltage, secondVoltage, branch, result);
            sendMeasurement(stack, player, position, side, CircuitDiagnosis.translationKey(local),
                    readingPayload(selectedMode, local));
        }
        clearProbe(tag);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, false, 0, 0, 0, 0,
                    "tool.craftonica.multimeter.ready", "");
        return stack;
    }

    public void handleGuiAction(ItemStack stack, EntityPlayerMP player, ToolActionMessage action) {
        NBTTagCompound tag = tag(stack);
        if (action.getAction() == ToolAction.MULTIMETER_CLEAR) {
            clearProbe(tag);
        } else if (action.getAction() == ToolAction.MULTIMETER_MODE) {
            MultimeterMode[] modes = MultimeterMode.values();
            if (action.getValue() < 0 || action.getValue() >= modes.length) return;
            tag.setString(TAG_MODE, modes[action.getValue()].getId());
            clearProbe(tag);
        } else return;
        player.inventory.markDirty();
        sendState(stack, player, false, 0, 0, 0, 0,
                "tool.craftonica.multimeter.ready", "");
    }

    static CircuitResult measurement(MultimeterMode mode, double firstVoltage, double secondVoltage,
                                     BranchResult branch, CircuitResult fallback) {
        double voltage = firstVoltage - secondVoltage;
        if (mode == MultimeterMode.VOLTAGE)
            return new CircuitResult(fallback.getStatus(), voltage, branch == null ? 0.0 : Math.abs(branch.getCurrent()), 0.0, "nodal_probe_voltage");
        if (branch == null)
            return new CircuitResult(CircuitStatus.UNSUPPORTED_TOPOLOGY, voltage, Double.NaN, Double.NaN, "ambiguous_or_no_branch");
        double current = Math.abs(branch.getCurrent());
        CircuitStatus status = current > 1e-12 ? CircuitStatus.CLOSED : CircuitStatus.OPEN_CIRCUIT;
        return new CircuitResult(status, voltage, current, Double.NaN, "nodal_probe_branch");
    }

    static CircuitResult resistanceMeasurement(ResistanceMeasurement measurement) {
        double resistance = measurement.getOhms();
        CircuitStatus status = measurement.getStatus() == ResistanceMeasurement.Status.VALID && resistance <= 10.0
                ? CircuitStatus.CLOSED : CircuitStatus.OPEN_CIRCUIT;
        return new CircuitResult(status, 0.0, 0.0, resistance, "nodal_auxiliary_resistance");
    }

    private String readingPayload(MultimeterMode selectedMode, CircuitResult result) {
        MultimeterReading reading = MultimeterReading.fromNetworkResult(selectedMode, result);
        switch (reading.getKind()) {
            case VALUE:
                return "message.craftonica.multimeter.reading." + selectedMode.getId()
                        + "|" + decimal(reading.getValue(), 2);
            case CONTINUITY: return "message.craftonica.multimeter.continuity_yes";
            case NO_CONTINUITY: return "message.craftonica.multimeter.continuity_no";
            case OVERCURRENT: return "message.craftonica.multimeter.no_resistor";
            case UNSUPPORTED: return "message.craftonica.multimeter.unsupported_measurement";
            default: return "";
        }
    }

    private void sendMeasurement(ItemStack stack, EntityPlayer player, BlockPosition second, int secondSide,
                                 String headline, String detail) {
        if (player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, true, second.x, second.y, second.z, secondSide,
                    headline, detail);
    }

    private void sendIfPossible(ItemStack stack, EntityPlayer player, String headline, String detail) {
        if (player instanceof EntityPlayerMP)
            sendState(stack, (EntityPlayerMP) player, false, 0, 0, 0, 0, headline, detail);
    }

    private void sendState(ItemStack stack, EntityPlayerMP player,
                           boolean secondSet, int secondX, int secondY, int secondZ, int secondSide,
                           String headline, String detail) {
        NBTTagCompound tag = tag(stack);
        boolean firstSet = tag.getBoolean(TAG_PROBE_SET)
                && tag.getInteger(TAG_PROBE_DIMENSION) == player.worldObj.provider.dimensionId;
        ToolNetwork.sendTo(player, ToolStateMessage.multimeter(mode(tag).ordinal(),
                player.worldObj.provider.dimensionId,
                firstSet, tag.getInteger(TAG_PROBE_X), tag.getInteger(TAG_PROBE_Y),
                tag.getInteger(TAG_PROBE_Z), tag.getInteger(TAG_PROBE_SIDE),
                secondSet, secondX, secondY, secondZ, secondSide, headline, detail));
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
        NBTTagCompound tag = stack.getTagCompound();
        migrateTag(tag);
        return tag;
    }

    static void migrateTag(NBTTagCompound tag) {
        if (tag.hasKey(TAG_DATA_VERSION)) return;
        tag.setString(TAG_MODE, MultimeterMode.fromId(tag.getString(TAG_MODE)).getId());
        if (tag.getBoolean(TAG_PROBE_SET)
                && (!tag.hasKey(TAG_PROBE_SIDE) || tag.getInteger(TAG_PROBE_SIDE) < 0
                || tag.getInteger(TAG_PROBE_SIDE) > 5)) clearProbe(tag);
        tag.setInteger(TAG_DATA_VERSION, NbtMigrations.SIMPLE_DATA_VERSION);
    }

    private void setFirstProbe(NBTTagCompound tag, int dimension, BlockPosition position, int side) {
        tag.setBoolean(TAG_PROBE_SET, true);
        tag.setInteger(TAG_PROBE_DIMENSION, dimension);
        tag.setInteger(TAG_PROBE_X, position.x);
        tag.setInteger(TAG_PROBE_Y, position.y);
        tag.setInteger(TAG_PROBE_Z, position.z);
        tag.setInteger(TAG_PROBE_SIDE, side);
    }

    private static void clearProbe(NBTTagCompound tag) {
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
