package br.com.craftonica.registry;

import br.com.craftonica.item.ItemManual;
import br.com.craftonica.item.ItemMultimeter;
import br.com.craftonica.item.ItemWrench;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {
    public static final ItemMultimeter MULTIMETER = new ItemMultimeter();
    public static final ItemWrench WRENCH = new ItemWrench();
    public static final ItemManual MANUAL = new ItemManual();

    private ModItems() {
    }

    public static void register() {
        GameRegistry.registerItem(MULTIMETER, "multimeter");
        GameRegistry.registerItem(WRENCH, "wrench");
        GameRegistry.registerItem(MANUAL, "manual");
    }
}
