package br.com.craftonica.robot.modular.transaction.forge;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.EntityModularRobot;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.transaction.AssemblyTransactionWorld;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

final class ForgeAssemblyTransactionWorld implements AssemblyTransactionWorld {
    private final World world;
    private final ComponentOrientation orientation;

    ForgeAssemblyTransactionWorld(World world, ComponentOrientation orientation) {
        this.world = world; this.orientation = orientation;
    }

    @Override public boolean matches(GridVector p, ModularBlockSnapshot expected) {
        if (!loaded(p)) return false;
        Object name = Block.blockRegistry.getNameForObject(world.getBlock(p.x, p.y, p.z));
        if (name == null || !expected.blockRegistryName.equals(name.toString())
                || (world.getBlockMetadata(p.x, p.y, p.z) & 15) != expected.metadata) return false;
        TileEntity tile = world.getTileEntity(p.x, p.y, p.z); NBTTagCompound expectedTile = expected.getTileData();
        if (tile == null) return expectedTile == null;
        if (expectedTile == null) return false;
        NBTTagCompound actual = new NBTTagCompound(); tile.writeToNBT(actual);
        return actual.toString().equals(expectedTile.toString());
    }

    @Override public boolean remove(GridVector p, ModularBlockSnapshot expected) {
        if (!loaded(p)) return false;
        if (world.isAirBlock(p.x, p.y, p.z)) return true;
        world.setBlock(p.x, p.y, p.z, Blocks.air, 0, 2);
        return world.isAirBlock(p.x, p.y, p.z);
    }

    @Override public boolean restore(GridVector p, ModularBlockSnapshot snapshot) {
        if (!loaded(p)) return false;
        Block block = (Block) Block.blockRegistry.getObject(snapshot.blockRegistryName);
        if (block == null || !world.setBlock(p.x, p.y, p.z, block, snapshot.metadata, 2)) return false;
        NBTTagCompound tileData = snapshot.getTileData();
        if (tileData != null) {
            TileEntity tile = world.getTileEntity(p.x, p.y, p.z);
            if (tile == null) { world.setBlockToAir(p.x, p.y, p.z); return false; }
            tileData.setInteger("x", p.x); tileData.setInteger("y", p.y); tileData.setInteger("z", p.z);
            try { tile.readFromNBT(tileData); tile.markDirty(); world.markBlockForUpdate(p.x, p.y, p.z); }
            catch (RuntimeException invalid) { world.setBlockToAir(p.x, p.y, p.z); return false; }
        }
        return true;
    }

    @Override public boolean isLoadedAndEmpty(GridVector p) { return loaded(p) && world.isAirBlock(p.x, p.y, p.z); }

    @Override public boolean spawnRobot(UUID robotId, UUID ownerId, GridVector anchor, ModularRobotManifest manifest) {
        return world.spawnEntityInWorld(EntityModularRobot.create(world, robotId, ownerId, anchor, orientation, manifest));
    }

    @Override public boolean removeRobot(UUID robotId) {
        EntityModularRobot robot = find(robotId); if (robot == null) return false; robot.setDead(); return true;
    }
    @Override public boolean robotExists(UUID robotId) { return find(robotId) != null; }
    boolean isLoaded(GridVector p) { return loaded(p); }

    @SuppressWarnings("unchecked") private EntityModularRobot find(UUID id) {
        for (Entity entity : (List<Entity>) world.loadedEntityList)
            if (entity instanceof EntityModularRobot && ((EntityModularRobot) entity).getRobotState() != null
                    && id.equals(((EntityModularRobot) entity).getRobotState().getRobotId())) return (EntityModularRobot) entity;
        return null;
    }
    private boolean loaded(GridVector p) { return p.y >= 0 && p.y < 256 && world.blockExists(p.x, p.y, p.z); }
}
