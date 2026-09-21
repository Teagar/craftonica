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
import br.com.craftonica.block.BlockUltrasonicSensor;
import br.com.craftonica.block.BlockCalibrationTarget;
import br.com.craftonica.block.BlockRobotModule;
import br.com.craftonica.block.BlockHBridgeTerminal;
import br.com.craftonica.block.BlockHBridgeChannel;
import br.com.craftonica.block.BlockModularDcMotor;
import br.com.craftonica.block.BlockModularUltrasonicSensor;
import br.com.craftonica.sensor.AcousticMaterialProfile;
import br.com.craftonica.item.ItemBlockElectricalWire;
import br.com.craftonica.item.ItemBlockLed;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.tile.TileEntityElectricalLever;
import br.com.craftonica.tile.TileEntityPotentiometer;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tile.TileEntityAnalogSensor;
import br.com.craftonica.tile.TileEntityEducationalActuator;
import br.com.craftonica.tile.TileEntityElectricalWire;
import br.com.craftonica.tile.TileEntityUltrasonicSensor;
import br.com.craftonica.tile.TileEntityCalibrationTarget;
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
    public static final BlockUltrasonicSensor ULTRASONIC_SENSOR = new BlockUltrasonicSensor();
    public static final BlockModularUltrasonicSensor MODULAR_ULTRASONIC_SENSOR = new BlockModularUltrasonicSensor();
    public static final BlockRobotModule ROBOT_CHASSIS = new BlockRobotModule(BlockRobotModule.Type.CHASSIS,
            "robotChassis", "craftonica:robot_chassis");
    public static final BlockRobotModule H_BRIDGE = new BlockRobotModule(BlockRobotModule.Type.H_BRIDGE,
            "hBridge", "craftonica:h_bridge");
    public static final BlockHBridgeChannel H_BRIDGE_CHANNEL = new BlockHBridgeChannel();
    public static final BlockHBridgeTerminal H_BRIDGE_TERMINAL = new BlockHBridgeTerminal();
    public static final BlockModularDcMotor MODULAR_DC_MOTOR = new BlockModularDcMotor();
    public static final BlockCalibrationTarget TARGET_MDF = new BlockCalibrationTarget("targetMdf",
            "craftonica:target_mdf", AcousticMaterialProfile.MDF);
    public static final BlockCalibrationTarget TARGET_PLASTIC = new BlockCalibrationTarget("targetPlastic",
            "craftonica:target_plastic", AcousticMaterialProfile.RIGID_PLASTIC);
    public static final BlockCalibrationTarget TARGET_STYROFOAM = new BlockCalibrationTarget("targetStyrofoam",
            "craftonica:target_styrofoam", AcousticMaterialProfile.STYROFOAM);
    public static final BlockCalibrationTarget TARGET_FOAM = new BlockCalibrationTarget("targetFoam",
            "craftonica:target_foam", AcousticMaterialProfile.FOAM);

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
        GameRegistry.registerBlock(LED, ItemBlockLed.class, "led");
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
        GameRegistry.registerBlock(ULTRASONIC_SENSOR, "ultrasonic_sensor");
        GameRegistry.registerBlock(MODULAR_ULTRASONIC_SENSOR, "modular_ultrasonic_sensor");
        GameRegistry.registerBlock(ROBOT_CHASSIS, "robot_chassis");
        GameRegistry.registerBlock(H_BRIDGE, "h_bridge");
        GameRegistry.registerBlock(H_BRIDGE_CHANNEL, "h_bridge_channel");
        GameRegistry.registerBlock(H_BRIDGE_TERMINAL, "h_bridge_terminal");
        GameRegistry.registerBlock(MODULAR_DC_MOTOR, "modular_dc_motor");
        GameRegistry.registerBlock(TARGET_MDF, "target_mdf");
        GameRegistry.registerBlock(TARGET_PLASTIC, "target_plastic");
        GameRegistry.registerBlock(TARGET_STYROFOAM, "target_styrofoam");
        GameRegistry.registerBlock(TARGET_FOAM, "target_foam");
        GameRegistry.registerTileEntity(TileEntityLed.class, "craftonica_led");
        GameRegistry.registerTileEntity(TileEntityCircuitBreaker.class, "craftonica_circuit_breaker");
        GameRegistry.registerTileEntity(TileEntityElectricalLever.class, "craftonica_electrical_lever");
        GameRegistry.registerTileEntity(TileEntityPotentiometer.class, "craftonica_potentiometer");
        GameRegistry.registerTileEntity(TileEntityRoboBoard.class, "craftonica_robo_board");
        GameRegistry.registerTileEntity(TileEntityRoboPort.class, "craftonica_robo_port");
        GameRegistry.registerTileEntity(TileEntityAnalogSensor.class, "craftonica_analog_sensor");
        GameRegistry.registerTileEntity(TileEntityEducationalActuator.class, "craftonica_educational_actuator");
        GameRegistry.registerTileEntity(TileEntityElectricalWire.class, "craftonica_electrical_wire");
        GameRegistry.registerTileEntity(TileEntityUltrasonicSensor.class, "craftonica_ultrasonic_sensor");
        GameRegistry.registerTileEntity(TileEntityCalibrationTarget.class, "craftonica_calibration_target");
    }
}
