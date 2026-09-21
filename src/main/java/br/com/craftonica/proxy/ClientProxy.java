package br.com.craftonica.proxy;

import br.com.craftonica.client.ClientEventHandler;
import br.com.craftonica.client.automation.AutomationBridge;
import br.com.craftonica.client.render.ElectricalBlockRenderer;
import br.com.craftonica.client.render.HandheldToolRenderer;
import br.com.craftonica.client.render.RenderMobileRobot;
import br.com.craftonica.client.render.RenderModularRobot;
import br.com.craftonica.robot.EntityMobileRobot;
import br.com.craftonica.robot.modular.EntityModularRobot;
import br.com.craftonica.client.sketch.SketchClientController;
import br.com.craftonica.client.tool.ToolClientController;
import br.com.craftonica.render.CraftonicaRenderIds;
import br.com.craftonica.sketch.network.EditorStateMessage;
import br.com.craftonica.tool.network.ToolStateMessage;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraftforge.client.MinecraftForgeClient;
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
        RenderingRegistry.registerEntityRenderingHandler(EntityMobileRobot.class, new RenderMobileRobot());
        RenderingRegistry.registerEntityRenderingHandler(EntityModularRobot.class, new RenderModularRobot());
        HandheldToolRenderer toolRenderer = new HandheldToolRenderer();
        MinecraftForgeClient.registerItemRenderer(br.com.craftonica.registry.ModItems.MULTIMETER, toolRenderer);
        MinecraftForgeClient.registerItemRenderer(
                br.com.craftonica.registry.ModItems.ROBO_PORT_CONFIGURATOR, toolRenderer);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler(renderer));
        FMLCommonHandler.instance().bus().register(SketchClientController.INSTANCE);
        FMLCommonHandler.instance().bus().register(ToolClientController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ToolClientController.INSTANCE);
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

    @Override
    public void handleEditorState(EditorStateMessage state) {
        SketchClientController.INSTANCE.enqueue(state);
    }

    @Override
    public void prepareEditorOpen(int dimension, int x, int y, int z) {
        SketchClientController.INSTANCE.allowOpen(dimension, x, y, z);
    }

    @Override
    public void handleToolState(ToolStateMessage state) {
        ToolClientController.INSTANCE.enqueue(state);
    }
}
