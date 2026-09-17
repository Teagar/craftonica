package br.com.craftonica.registry;

import br.com.craftonica.block.BlockElectricalButton;
import br.com.craftonica.block.BlockElectricalWire;
import br.com.craftonica.block.BlockGround;
import br.com.craftonica.block.BlockLed;
import br.com.craftonica.block.BlockPowerSource;
import br.com.craftonica.block.BlockResistor;
import br.com.craftonica.block.BlockCircuitBreaker;
import br.com.craftonica.block.BlockDiode;
import br.com.craftonica.block.BlockElectricalLever;
import br.com.craftonica.block.BlockPotentiometer;
import br.com.craftonica.item.ItemBlockElectricalWire;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.tile.TileEntityElectricalLever;
import br.com.craftonica.tile.TileEntityPotentiometer;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {
    public static final BlockElectricalWire WIRE = new BlockElectricalWire();
    public static final BlockPowerSource POWER_SOURCE = new BlockPowerSource();
    public static final BlockGround GROUND = new BlockGround();
    public static final BlockElectricalButton BUTTON = new BlockElectricalButton();
    public static final BlockResistor RESISTOR_220 = new BlockResistor("resistor220", "craftonica:resistor_220", 220.0);
    public static final BlockResistor RESISTOR_1K = new BlockResistor("resistor1k", "craftonica:resistor_1k", 1000.0);
    public static final BlockResistor RESISTOR_10K = new BlockResistor("resistor10k", "craftonica:resistor_10k", 10000.0);
    public static final BlockLed LED = new BlockLed();
    public static final BlockCircuitBreaker CIRCUIT_BREAKER = new BlockCircuitBreaker();
    public static final BlockDiode DIODE = new BlockDiode();
    public static final BlockElectricalLever LEVER = new BlockElectricalLever();
    public static final BlockPotentiometer POTENTIOMETER = new BlockPotentiometer();

    private ModBlocks() {
    }

    public static void register() {
        GameRegistry.registerBlock(WIRE, ItemBlockElectricalWire.class, "electrical_wire");
        GameRegistry.registerBlock(POWER_SOURCE, "power_source");
        GameRegistry.registerBlock(GROUND, "ground");
        GameRegistry.registerBlock(BUTTON, "electrical_button");
        GameRegistry.registerBlock(RESISTOR_220, "resistor_220");
        GameRegistry.registerBlock(RESISTOR_1K, "resistor_1k");
        GameRegistry.registerBlock(RESISTOR_10K, "resistor_10k");
        GameRegistry.registerBlock(LED, "led");
        GameRegistry.registerBlock(CIRCUIT_BREAKER, "circuit_breaker");
        GameRegistry.registerBlock(DIODE, "diode");
        GameRegistry.registerBlock(LEVER, "electrical_lever");
        GameRegistry.registerBlock(POTENTIOMETER, "potentiometer");
        GameRegistry.registerTileEntity(TileEntityLed.class, "craftonica_led");
        GameRegistry.registerTileEntity(TileEntityCircuitBreaker.class, "craftonica_circuit_breaker");
        GameRegistry.registerTileEntity(TileEntityElectricalLever.class, "craftonica_electrical_lever");
        GameRegistry.registerTileEntity(TileEntityPotentiometer.class, "craftonica_potentiometer");
    }
}
