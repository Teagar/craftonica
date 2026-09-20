package br.com.craftonica.robot;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

/** Temporary safe spawn path used until physical assembly conversion is implemented by CRL-62. */
public final class MobileRobotSpawner {
    private MobileRobotSpawner() { }

    public static EntityMobileRobot spawnInFront(WorldServer world, EntityPlayerMP owner) {
        if (world == null || owner == null || owner.worldObj != world) return null;
        double yaw = StrictMath.toRadians(owner.rotationYaw);
        double x = owner.posX - StrictMath.sin(yaw) * 3.0;
        double y = owner.posY;
        double z = owner.posZ + StrictMath.cos(yaw) * 3.0;
        EntityMobileRobot robot = EntityMobileRobot.create(world, owner.getUniqueID());
        robot.setPositionAndRotation(x, y, z, owner.rotationYaw, 0.0F);
        int minChunkX = MathHelper.floor_double(robot.boundingBox.minX) >> 4;
        int maxChunkX = MathHelper.floor_double(robot.boundingBox.maxX) >> 4;
        int minChunkZ = MathHelper.floor_double(robot.boundingBox.minZ) >> 4;
        int maxChunkZ = MathHelper.floor_double(robot.boundingBox.maxZ) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) return null;
            }
        }
        if (!world.checkNoEntityCollision(robot.boundingBox, robot)
                || !world.getCollidingBoundingBoxes(robot, robot.boundingBox).isEmpty()
                || world.isAnyLiquid(robot.boundingBox)) return null;
        return world.spawnEntityInWorld(robot) ? robot : null;
    }
}
