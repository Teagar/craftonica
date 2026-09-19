package br.com.craftonica;

import br.com.craftonica.proxy.CommonProxy;
import br.com.craftonica.command.CommandCraftonica;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
        modid = Craftonica.MOD_ID,
        name = Craftonica.MOD_NAME,
        version = Craftonica.VERSION,
        acceptedMinecraftVersions = "[1.7.10]"
)
public final class Craftonica {
    public static final String MOD_ID = "craftonica";
    public static final String MOD_NAME = "Craftonica: Robotics Lab";
    public static final String VERSION = "1.1.0";

    @SidedProxy(
            clientSide = "br.com.craftonica.proxy.ClientProxy",
            serverSide = "br.com.craftonica.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandCraftonica());
    }
}
