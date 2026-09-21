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
        return new PlacedComponent(id, 1, orientation(block, metadata));
    }

    private static String componentId(Block block) {
        if (block == ModBlocks.ROBOT_CHASSIS) return StandardComponentCatalog.CHASSIS;
        if (block == ModBlocks.WIRE) return StandardComponentCatalog.WIRE;
        if (block == ModBlocks.POWER_SOURCE) return StandardComponentCatalog.POWER_SOURCE;
        if (block == ModBlocks.GROUND) return StandardComponentCatalog.GROUND;
        if (block == ModBlocks.ROBO_BOARD) return StandardComponentCatalog.ROBO_BOARD;
        if (block == ModBlocks.H_BRIDGE) return StandardComponentCatalog.H_BRIDGE;
        if (block == ModBlocks.DC_MOTOR) return StandardComponentCatalog.DC_MOTOR;
        if (block == ModBlocks.ULTRASONIC_SENSOR) return StandardComponentCatalog.HC_SR04;
        return null;
    }

    private static ComponentOrientation orientation(Block block, int metadata) {
        if (block == ModBlocks.DC_MOTOR)
            return new ComponentOrientation((metadata & 1) == 0 ? Direction.NORTH : Direction.EAST,
                    Direction.UP);
        if (block == ModBlocks.ROBOT_CHASSIS || block == ModBlocks.H_BRIDGE
                || block == ModBlocks.POWER_SOURCE || block == ModBlocks.GROUND
                || block == ModBlocks.ULTRASONIC_SENSOR)
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
}
