package br.com.craftonica.proxy;

import br.com.craftonica.client.ClientEventHandler;
import br.com.craftonica.client.render.ElectricalBlockRenderer;
import br.com.craftonica.render.CraftonicaRenderIds;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        CraftonicaRenderIds.ELECTRICAL_COMPONENT = RenderingRegistry.getNextAvailableRenderId();
        ElectricalBlockRenderer renderer = new ElectricalBlockRenderer();
        RenderingRegistry.registerBlockHandler(renderer);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler(renderer));
    }
}
