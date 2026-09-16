package br.com.craftonica.registry;

import br.com.craftonica.item.ItemMultimeter;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {
    public static final ItemMultimeter MULTIMETER = new ItemMultimeter();

    private ModItems() {
    }

    public static void register() {
        GameRegistry.registerItem(MULTIMETER, "multimeter");
    }
}
