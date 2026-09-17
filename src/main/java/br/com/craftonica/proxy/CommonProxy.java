package br.com.craftonica.proxy;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.registry.ModRecipes;
import br.com.craftonica.network.ElectricalNetworkEvents;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

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
    }

    public void openManual(EntityPlayer player) {
    }
}
