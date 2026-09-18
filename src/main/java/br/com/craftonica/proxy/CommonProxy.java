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

public class CommonProxy {
    public void preInit() {
        ModBlocks.register();
        ModItems.register();
        ModRecipes.register();
    }

    public void init() {
        ElectricalNetworkEvents events = new ElectricalNetworkEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) RuntimeServer.start();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) RuntimeServer.stop();
    }

    public void openManual(EntityPlayer player) {
    }
}
