package br.com.craftonica.proxy;

import br.com.craftonica.client.ClientEventHandler;
import br.com.craftonica.client.automation.AutomationBridge;
import br.com.craftonica.client.render.ElectricalBlockRenderer;
import br.com.craftonica.render.CraftonicaRenderIds;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        CraftonicaRenderIds.ELECTRICAL_COMPONENT = RenderingRegistry.getNextAvailableRenderId();
        ElectricalBlockRenderer renderer = new ElectricalBlockRenderer();
        RenderingRegistry.registerBlockHandler(renderer);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler(renderer));
        AutomationBridge.startConfigured();
    }

    @Override
    public void openManual(EntityPlayer player) {
        NBTTagList pages = new NBTTagList();
        for (int page = 0; StatCollector.canTranslate("manual.craftonica.page." + page); page++) {
            String text = StatCollector.translateToLocal("manual.craftonica.page." + page).replace('|', '\n');
            pages.appendTag(new NBTTagString(text));
        }

        ItemStack book = new ItemStack(br.com.craftonica.registry.ModItems.MANUAL);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("pages", pages);
        book.setTagCompound(tag);
        Minecraft.getMinecraft().displayGuiScreen(new GuiScreenBook(player, book, false));
    }
}
