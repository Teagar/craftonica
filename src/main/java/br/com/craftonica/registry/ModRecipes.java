package br.com.craftonica.registry;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class ModRecipes {
    private ModRecipes() {
    }

    public static void register() {
        GameRegistry.addRecipe(new ItemStack(ModBlocks.WIRE, 8),
                "RSR",
                'R', Items.redstone,
                'S', Items.string);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.POWER_SOURCE),
                "IRI",
                "IGI",
                "III",
                'I', Items.iron_ingot,
                'R', Items.redstone,
                'G', Items.gold_ingot);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.GROUND),
                " I ",
                " I ",
                "CCC",
                'I', Items.iron_ingot,
                'C', Blocks.cobblestone);

        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.BUTTON),
                Blocks.stone_button, ModBlocks.WIRE);

        registerResistor(ModBlocks.RESISTOR_220, 1, 1, 3);
        registerResistor(ModBlocks.RESISTOR_1K, 3, 0, 1);
        registerResistor(ModBlocks.RESISTOR_10K, 3, 0, 14);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.LED),
                " G ",
                "RTR",
                " I ",
                'G', Blocks.glass_pane,
                'R', new ItemStack(Items.dye, 1, 1),
                'T', Blocks.redstone_torch,
                'I', Items.iron_ingot);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.CIRCUIT_BREAKER),
                "IRI", "RCR", "IRI",
                'I', Items.iron_ingot, 'R', Items.redstone, 'C', Items.comparator);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.DIODE),
                " G ", "RTR", " I ",
                'G', Blocks.glass_pane, 'R', Items.redstone, 'T', Blocks.redstone_torch, 'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.LEVER),
                " I ", "IRI", " I ", 'I', Items.iron_ingot, 'R', Items.redstone);

        GameRegistry.addRecipe(new ItemStack(ModItems.MULTIMETER),
                "GIG",
                "RCR",
                "III",
                'G', Blocks.glass_pane,
                'I', Items.iron_ingot,
                'R', Items.redstone,
                'C', Items.comparator);

        GameRegistry.addRecipe(new ItemStack(ModItems.WRENCH),
                "I I",
                " II",
                " I ",
                'I', Items.iron_ingot);

        GameRegistry.addShapelessRecipe(new ItemStack(ModItems.MANUAL), Items.book, Items.redstone);
    }

    private static void registerResistor(net.minecraft.block.Block resistor, int firstBand,
                                         int secondBand, int multiplierBand) {
        GameRegistry.addRecipe(new ItemStack(resistor, 2),
                "ABC",
                " R ",
                'A', new ItemStack(Items.dye, 1, firstBand),
                'B', new ItemStack(Items.dye, 1, secondBand),
                'C', new ItemStack(Items.dye, 1, multiplierBand),
                'R', Items.redstone);
    }
}
