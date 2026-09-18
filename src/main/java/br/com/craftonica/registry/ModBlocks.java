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
import br.com.craftonica.block.BlockRoboBoard;
import br.com.craftonica.block.BlockRoboPort;
import br.com.craftonica.block.BlockAnalogSensor;
import br.com.craftonica.block.BlockEducationalActuator;
import br.com.craftonica.item.ItemBlockElectricalWire;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.tile.TileEntityElectricalLever;
import br.com.craftonica.tile.TileEntityPotentiometer;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tile.TileEntityAnalogSensor;
import br.com.craftonica.tile.TileEntityEducationalActuator;
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
    public static final BlockRoboBoard ROBO_BOARD = new BlockRoboBoard();
    public static final BlockRoboPort ROBO_PORT = new BlockRoboPort();
    public static final BlockAnalogSensor LIGHT_SENSOR = new BlockAnalogSensor(BlockAnalogSensor.Type.LIGHT,
            "light_sensor", "lightSensor", "craftonica:light_sensor");
    public static final BlockAnalogSensor TEMPERATURE_SENSOR = new BlockAnalogSensor(BlockAnalogSensor.Type.TEMPERATURE,
            "temperature_sensor", "temperatureSensor", "craftonica:temperature_sensor");
    public static final BlockEducationalActuator BUZZER = new BlockEducationalActuator(BlockEducationalActuator.Type.BUZZER,
            "buzzer", "buzzer", "craftonica:buzzer");
    public static final BlockEducationalActuator DC_MOTOR = new BlockEducationalActuator(BlockEducationalActuator.Type.DC_MOTOR,
            "dc_motor", "dcMotor", "craftonica:dc_motor");

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
        GameRegistry.registerBlock(ROBO_BOARD, "robo_board");
        GameRegistry.registerBlock(ROBO_PORT, "robo_port");
        GameRegistry.registerBlock(LIGHT_SENSOR, "light_sensor");
        GameRegistry.registerBlock(TEMPERATURE_SENSOR, "temperature_sensor");
        GameRegistry.registerBlock(BUZZER, "buzzer");
        GameRegistry.registerBlock(DC_MOTOR, "dc_motor");
        GameRegistry.registerTileEntity(TileEntityLed.class, "craftonica_led");
        GameRegistry.registerTileEntity(TileEntityCircuitBreaker.class, "craftonica_circuit_breaker");
        GameRegistry.registerTileEntity(TileEntityElectricalLever.class, "craftonica_electrical_lever");
        GameRegistry.registerTileEntity(TileEntityPotentiometer.class, "craftonica_potentiometer");
        GameRegistry.registerTileEntity(TileEntityRoboBoard.class, "craftonica_robo_board");
        GameRegistry.registerTileEntity(TileEntityRoboPort.class, "craftonica_robo_port");
        GameRegistry.registerTileEntity(TileEntityAnalogSensor.class, "craftonica_analog_sensor");
        GameRegistry.registerTileEntity(TileEntityEducationalActuator.class, "craftonica_educational_actuator");
    }
}
