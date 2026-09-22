package br.com.craftonica.robot.modular.assembly.forge;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyWorldView;
import br.com.craftonica.robot.modular.assembly.PlacedComponent;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.block.BlockHBridgeTerminal;

/** Forge boundary for discovery. blockExists is checked before every world read. */
public final class ForgeAssemblyWorldView implements AssemblyWorldView {
    private final World world;

    public ForgeAssemblyWorldView(World world) {
        if (world == null) throw new IllegalArgumentException("world");
        this.world = world;
    }

    @Override public boolean isLoaded(GridVector position) {
        return position.y >= 0 && position.y < 256
                && world.blockExists(position.x, position.y, position.z);
    }

    @Override public PlacedComponent componentAt(GridVector position) {
        if (!isLoaded(position)) throw new IllegalStateException("unloaded position read");
        Block block = world.getBlock(position.x, position.y, position.z);
        int metadata = world.getBlockMetadata(position.x, position.y, position.z);
        String id = componentId(block);
        if (id == null) return null;
        ComponentOrientation orientation = orientation(block, metadata);
        if (block == ModBlocks.ROBO_PORT) {
            TileEntity tile = world.getTileEntity(position.x, position.y, position.z);
            if (!(tile instanceof TileEntityRoboPort) || ((TileEntityRoboPort) tile).getOutwardSide() < 0) return null;
            Direction outward = direction(((TileEntityRoboPort) tile).getOutwardSide());
            orientation = new ComponentOrientation(outward,
                    outward == Direction.UP || outward == Direction.DOWN ? Direction.NORTH : Direction.UP);
        } else if (block == ModBlocks.H_BRIDGE_TERMINAL) {
            int side = BlockHBridgeTerminal.outwardSide(world, position.x, position.y, position.z);
            if (side < 0) return null;
            Direction outward = direction(side);
            orientation = new ComponentOrientation(outward,
                    outward == Direction.UP || outward == Direction.DOWN ? Direction.NORTH : Direction.UP);
        }
        return new PlacedComponent(id, 1, orientation);
    }

    private static String componentId(Block block) {
        if (block == ModBlocks.ROBOT_CHASSIS) return StandardComponentCatalog.CHASSIS;
        if (block == ModBlocks.WIRE) return StandardComponentCatalog.WIRE;
        if (block == ModBlocks.POWER_SOURCE) return StandardComponentCatalog.POWER_SOURCE;
        if (block == ModBlocks.GROUND) return StandardComponentCatalog.GROUND;
        if (block == ModBlocks.ROBO_BOARD) return StandardComponentCatalog.ROBO_BOARD;
        if (block == ModBlocks.ROBO_PORT) return StandardComponentCatalog.ROBO_PORT;
        if (block == ModBlocks.H_BRIDGE_CHANNEL) return StandardComponentCatalog.H_BRIDGE;
        if (block == ModBlocks.H_BRIDGE_TERMINAL) return StandardComponentCatalog.H_BRIDGE_TERMINAL;
        if (block == ModBlocks.MODULAR_DC_MOTOR) return StandardComponentCatalog.DC_MOTOR;
        if (block == ModBlocks.MECHANICAL_AXLE) return StandardComponentCatalog.AXLE;
        if (block == ModBlocks.MECHANICAL_BEARING) return StandardComponentCatalog.BEARING;
        if (block == ModBlocks.SPUR_GEAR_12) return StandardComponentCatalog.GEAR_12;
        if (block == ModBlocks.SPUR_GEAR_36) return StandardComponentCatalog.GEAR_36;
        if (block == ModBlocks.EDUCATIONAL_SERVO) return StandardComponentCatalog.SERVO;
        if (block == ModBlocks.REVOLUTE_JOINT) return StandardComponentCatalog.REVOLUTE_JOINT;
        if (block == ModBlocks.PRISMATIC_JOINT) return StandardComponentCatalog.PRISMATIC_JOINT;
        if (block == ModBlocks.ROBOT_WHEEL) return StandardComponentCatalog.WHEEL;
        if (block == ModBlocks.ROBOT_WHEEL_150) return StandardComponentCatalog.WHEEL_150;
        if (block == ModBlocks.PASSIVE_CASTER) return StandardComponentCatalog.CASTER;
        if (block == ModBlocks.TRACK_MODULE) return StandardComponentCatalog.TRACK_MODULE;
        if (block == ModBlocks.OMNI_WHEEL) return StandardComponentCatalog.OMNI_WHEEL;
        if (block == ModBlocks.MECANUM_WHEEL_LEFT) return StandardComponentCatalog.MECANUM_LEFT;
        if (block == ModBlocks.MECANUM_WHEEL_RIGHT) return StandardComponentCatalog.MECANUM_RIGHT;
        if (block == ModBlocks.ROTARY_ENCODER) return StandardComponentCatalog.ENCODER;
        if (block == ModBlocks.LIMIT_SWITCH) return StandardComponentCatalog.LIMIT_SWITCH;
        if (block == ModBlocks.EDUCATIONAL_IMU) return StandardComponentCatalog.IMU;
        if (block == ModBlocks.MODULAR_ULTRASONIC_SENSOR) return StandardComponentCatalog.HC_SR04;
        return null;
    }

    private static ComponentOrientation orientation(Block block, int metadata) {
        if (block == ModBlocks.REVOLUTE_JOINT || block == ModBlocks.PRISMATIC_JOINT) {
            Direction forward = direction(metadata & 7);
            Direction up = forward == Direction.UP || forward == Direction.DOWN ? Direction.NORTH : Direction.UP;
            return new ComponentOrientation(forward, up);
        }
        if (block == ModBlocks.ROBOT_CHASSIS || block == ModBlocks.H_BRIDGE_CHANNEL
                || block == ModBlocks.MODULAR_DC_MOTOR
                || block == ModBlocks.MECHANICAL_AXLE || block == ModBlocks.MECHANICAL_BEARING
                || block == ModBlocks.SPUR_GEAR_12 || block == ModBlocks.SPUR_GEAR_36
                || block == ModBlocks.EDUCATIONAL_SERVO
                || block == ModBlocks.ROBOT_WHEEL
                || block == ModBlocks.ROBOT_WHEEL_150
                || block == ModBlocks.TRACK_MODULE
                || block == ModBlocks.OMNI_WHEEL
                || block == ModBlocks.MECANUM_WHEEL_LEFT
                || block == ModBlocks.MECANUM_WHEEL_RIGHT
                || block == ModBlocks.ROTARY_ENCODER
                || block == ModBlocks.LIMIT_SWITCH
                || block == ModBlocks.EDUCATIONAL_IMU
                || block == ModBlocks.POWER_SOURCE || block == ModBlocks.GROUND
                || block == ModBlocks.MODULAR_ULTRASONIC_SENSOR)
            return new ComponentOrientation(horizontal(metadata & 7), Direction.UP);
        return ComponentOrientation.NORTH_UP;
    }

    private static Direction horizontal(int side) {
        switch (side) {
            case 2: return Direction.NORTH;
            case 3: return Direction.SOUTH;
            case 4: return Direction.WEST;
            case 5: return Direction.EAST;
            default: return Direction.NORTH;
        }
    }

    private static Direction direction(int side) {
        switch (side) {
            case 0: return Direction.DOWN;
            case 1: return Direction.UP;
            default: return horizontal(side);
        }
    }
}
