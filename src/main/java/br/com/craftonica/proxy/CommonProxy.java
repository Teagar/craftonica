package br.com.craftonica.proxy;

import br.com.craftonica.registry.ModBlocks;

public class CommonProxy {
    public void preInit() {
        ModBlocks.register();
    }

    public void init() {
    }
}
