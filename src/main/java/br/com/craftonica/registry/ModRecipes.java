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

        for (int color = 0; color < 16; color++)
            GameRegistry.addRecipe(new ItemStack(ModBlocks.LED, 1, color),
                    " G ",
                    "RTR",
                    " I ",
                    'G', Blocks.glass_pane,
                    'R', new ItemStack(Items.dye, 1, color),
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
        GameRegistry.addRecipe(new ItemStack(ModBlocks.POTENTIOMETER),
                "IRI", "RGR", "IRI", 'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.ROBO_BOARD),
                "RGR", "ICI", "RQR",
                'R', Items.redstone, 'G', Items.gold_ingot, 'I', Items.iron_ingot,
                'C', Items.comparator, 'Q', Items.quartz);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.ROBO_PORT, 4),
                " I ", "RGR", " I ",
                'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_nugget);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.LIGHT_SENSOR),
                " G ", "QRQ", " I ",
                'G', Blocks.glass_pane, 'Q', Items.quartz, 'R', Items.redstone, 'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.TEMPERATURE_SENSOR),
                " I ", "QRQ", " G ",
                'G', Blocks.glass_pane, 'Q', Items.quartz, 'R', Items.redstone, 'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.BUZZER),
                " I ", "RNR", " I ",
                'I', Items.iron_ingot, 'R', Items.redstone, 'N', Blocks.noteblock);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.DC_MOTOR),
                "III", "RGR", "III",
                'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.ULTRASONIC_SENSOR),
                "Q Q", "RCR", "III",
                'Q', Items.quartz, 'R', Items.redstone, 'C', Items.comparator, 'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.ROBOT_CHASSIS),
                "I I", "III", "I I",
                'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(ModBlocks.H_BRIDGE),
                "RIR", "ICI", "RIR",
                'R', Items.redstone, 'I', Items.iron_ingot, 'C', Items.comparator);
        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.TARGET_MDF), Blocks.planks, Items.stick);
        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.TARGET_PLASTIC), Blocks.glass, Items.stick);
        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.TARGET_STYROFOAM), Blocks.snow, Items.stick);
        GameRegistry.addShapelessRecipe(new ItemStack(ModBlocks.TARGET_FOAM), Blocks.wool, Items.stick);

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

        GameRegistry.addRecipe(new ItemStack(ModItems.ROBO_PORT_CONFIGURATOR),
                " R ",
                "ICI",
                " I ",
                'R', Items.redstone,
                'I', Items.iron_ingot,
                'C', Items.comparator);

        GameRegistry.addRecipe(new ItemStack(ModItems.WIRE_ROUTER),
                " I ",
                "RCR",
                " I ",
                'I', Items.iron_ingot,
                'R', Items.redstone,
                'C', Items.shears);

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
