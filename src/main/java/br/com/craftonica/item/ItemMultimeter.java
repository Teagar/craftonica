package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.electrical.CircuitDiagnosis;
import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.Locale;

public final class ItemMultimeter extends Item {
    public ItemMultimeter() {
        setUnlocalizedName("multimeter");
        setTextureName("craftonica:multimeter");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                             int side, float hitX, float hitY, float hitZ) {
        if (!(world.getBlock(x, y, z) instanceof IElectricalBlock)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }

        BlockPosition position = new BlockPosition(x, y, z);
        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        CircuitResult result = manager.getResult(position);
        if (result == null) {
            manager.invalidateAround(position);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.pending"));
            return true;
        }

        player.addChatMessage(new ChatComponentTranslation(CircuitDiagnosis.translationKey(result)));
        if (CircuitDiagnosis.hasMeasurements(result)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.values",
                    decimal(result.getSourceVoltage(), 2), decimal(result.getCurrentAmps() * 1000.0, 2),
                    decimal(result.getEquivalentResistanceOhms(), 2)));
        } else if (Double.isInfinite(result.getCurrentAmps())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.multimeter.no_resistor"));
        }
        return true;
    }

    private String decimal(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value);
    }
}
