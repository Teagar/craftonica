package br.com.craftonica.proxy;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.registry.ModRecipes;
import br.com.craftonica.network.ElectricalNetworkEvents;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import br.com.craftonica.runtime.server.RuntimeServer;
import br.com.craftonica.sketch.network.EditorStateMessage;
import br.com.craftonica.sketch.network.SketchNetwork;
import br.com.craftonica.sketch.server.SketchServer;
import br.com.craftonica.persistence.WorldBackupService;
import cpw.mods.fml.common.eventhandler.EventPriority;
import net.minecraft.world.storage.SaveHandler;

import java.io.IOException;

public class CommonProxy {
    public void preInit() {
        SketchNetwork.initialize();
        ModBlocks.register();
        ModItems.register();
        ModRecipes.register();
    }

    public void init() {
        ElectricalNetworkEvents events = new ElectricalNetworkEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(SketchServer.EVENTS);
        FMLCommonHandler.instance().bus().register(SketchServer.EVENTS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            try {
                if (!(event.world.getSaveHandler() instanceof SaveHandler))
                    throw new IOException("Unsupported world save handler: "
                            + event.world.getSaveHandler().getClass().getName());
                WorldBackupService.prepare(((SaveHandler) event.world.getSaveHandler()).getWorldDirectory().toPath());
            } catch (IOException failure) {
                throw new IllegalStateException("Craftonica could not create a verified pre-migration backup", failure);
            }
            RuntimeServer.start();
            SketchServer.start();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            RuntimeServer.stop();
            SketchServer.stop();
        }
    }

    public void openManual(EntityPlayer player) {
    }

    /** Client proxy overrides this hook; common packet handling never imports client classes. */
    public void handleEditorState(EditorStateMessage state) {
    }

    /** Client proxy clears the local close guard before the server answers an explicit interaction. */
    public void prepareEditorOpen(int dimension, int x, int y, int z) {
    }
}
