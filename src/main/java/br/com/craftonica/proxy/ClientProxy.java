package br.com.craftonica.proxy;

import br.com.craftonica.client.render.ElectricalBlockRenderer;
import br.com.craftonica.render.CraftonicaRenderIds;
import cpw.mods.fml.client.registry.RenderingRegistry;

public final class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        CraftonicaRenderIds.ELECTRICAL_COMPONENT = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new ElectricalBlockRenderer());
    }
}
