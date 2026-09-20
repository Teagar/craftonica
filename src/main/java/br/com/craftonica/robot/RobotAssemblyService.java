package br.com.craftonica.robot;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Server-only transactional conversion between eight world blocks and one robot entity. */
public final class RobotAssemblyService {
    private RobotAssemblyService() { }

    public static boolean assemble(World world, int x, int y, int z, EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return false;
        Validation validation = validate(world, x, y, z, player);
        if (!validation.valid()) {
            player.addChatMessage(new ChatComponentTranslation(validation.message,
                    validation.slotName, validation.x, validation.y, validation.z));
            return false;
        }
        List<BlockSnapshot> snapshots = capture(world, x, y, z, validation.facing);
        RoboBoardState board = validation.board.copyBoardState();
        byte[] templateSketch = validation.board.getTemplateSketchSource();
        if (!removeAll(world, snapshots)) {
            restoreAll(world, snapshots);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.assembly_rollback"));
            return false;
        }
        EntityMobileRobot robot = EntityMobileRobot.createFromAssembly(world, player.getUniqueID(),
                validation.facing, board, templateSketch);
        robot.setPositionAndRotation(x + 0.5D, y, z + 0.5D, yawForFacing(validation.facing), 0.0F);
        if (!world.checkNoEntityCollision(robot.boundingBox, robot)
                || !world.getCollidingBoundingBoxes(robot, robot.boundingBox).isEmpty()
                || world.isAnyLiquid(robot.boundingBox) || !world.spawnEntityInWorld(robot)) {
            robot.setDead();
            restoreAll(world, snapshots);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.assembly_rollback"));
            return false;
        }
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.assembled"));
        return true;
    }

    public static boolean disassemble(EntityMobileRobot robot, EntityPlayer player) {
        if (robot == null || robot.worldObj == null || robot.worldObj.isRemote || player == null) return false;
        if (player.getCurrentEquippedItem() == null
                || player.getCurrentEquippedItem().getItem() != ModItems.WRENCH) return false;
        if (!robot.getRobotState().getOwnerId().equals(player.getUniqueID())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_owner"));
            return false;
        }
        if (!robot.getRobotState().hasPhysicalAssemblyManifest()) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_physical"));
            return false;
        }
        if (!robot.isParkedForDisassembly()) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_parked"));
            return false;
        }
        int x = MathHelper.floor_double(robot.posX), y = MathHelper.floor_double(robot.posY + 0.01),
                z = MathHelper.floor_double(robot.posZ);
        int facing = facingForYaw(robot.rotationYaw);
        if (StrictMath.abs(robot.posX - (x + 0.5D)) > 0.125D
                || StrictMath.abs(robot.posY - y) > 0.125D
                || StrictMath.abs(robot.posZ - (z + 0.5D)) > 0.125D
                || angleDistance(robot.rotationYaw, yawForFacing(facing)) > 5.0F) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_aligned"));
            return false;
        }
        World world = robot.worldObj;
        if (!volumeLoadedAndEmpty(world, robot, x, y, z, facing)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.disassembly_blocked"));
            return false;
        }
        List<BlockSnapshot> placed = new ArrayList<BlockSnapshot>();
        for (RobotAssemblyLayout.Slot slot : RobotAssemblyLayout.slots(facing)) {
            int px = x + slot.dx, py = y + slot.dy, pz = z + slot.dz;
            Block block = blockFor(slot.type);
            int metadata = expectedMetadata(slot, facing);
            if (!world.setBlock(px, py, pz, block, metadata, 3)) {
                clearPlaced(world, placed);
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.disassembly_rollback"));
                return false;
            }
            placed.add(new BlockSnapshot(px, py, pz, block, metadata, null));
        }
        RobotAssemblyLayout.Slot boardSlot = RobotAssemblyLayout.slots(facing).get(3);
        TileEntity tile = world.getTileEntity(x + boardSlot.dx, y + boardSlot.dy, z + boardSlot.dz);
        if (!(tile instanceof TileEntityRoboBoard)) {
            clearPlaced(world, placed);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.disassembly_rollback"));
            return false;
        }
        try {
            ((TileEntityRoboBoard) tile).restoreFromRobot(robot.getRobotState().getBoardState(),
                    robot.getRobotState().getBoardTemplateSketch(), player.getUniqueID());
        } catch (RuntimeException invalidState) {
            clearPlaced(world, placed);
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.disassembly_rollback"));
            return false;
        }
        robot.prepareForDisassembly();
        robot.setDead();
        player.getCurrentEquippedItem().damageItem(1, player);
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.disassembled"));
        return true;
    }

    static Validation validate(World world, int x, int y, int z, EntityPlayer player) {
        int facing = world.getBlockMetadata(x, y, z) & 7;
        if (world.getBlock(x, y, z) != ModBlocks.ROBOT_CHASSIS || facing < 2 || facing > 5)
            return Validation.failure("message.craftonica.robot.invalid_chassis", "CHASSIS_CORE", x, y, z);
        for (RobotAssemblyLayout.Slot slot : RobotAssemblyLayout.slots(facing)) {
            int px = x + slot.dx, py = y + slot.dy, pz = z + slot.dz;
            if (!world.getChunkProvider().chunkExists(px >> 4, pz >> 4))
                return Validation.failure("message.craftonica.robot.assembly_unloaded", slot.type.name(), px, py, pz);
            Block expected = blockFor(slot.type);
            if (world.getBlock(px, py, pz) != expected)
                return Validation.failure("message.craftonica.robot.missing_module", slot.type.name(), px, py, pz);
            int expectedMeta = expectedMetadata(slot, facing);
            if (expectedMeta >= 0 && (world.getBlockMetadata(px, py, pz) & 7) != expectedMeta)
                return Validation.failure("message.craftonica.robot.wrong_orientation", slot.type.name(), px, py, pz);
        }
        RobotAssemblyLayout.Slot boardSlot = RobotAssemblyLayout.slots(facing).get(3);
        TileEntity tile = world.getTileEntity(x + boardSlot.dx, y + boardSlot.dy, z + boardSlot.dz);
        if (!(tile instanceof TileEntityRoboBoard)
                || !player.getUniqueID().equals(((TileEntityRoboBoard) tile).getOwnerId()))
            return Validation.failure("message.craftonica.robot.board_denied", "ROBO_BOARD",
                    x + boardSlot.dx, y + boardSlot.dy, z + boardSlot.dz);
        return Validation.success(facing, (TileEntityRoboBoard) tile);
    }

    static Block blockFor(RobotModuleSnapshot.Type type) {
        switch (type) {
            case CHASSIS_CORE: return ModBlocks.ROBOT_CHASSIS;
            case ROBO_BOARD: return ModBlocks.ROBO_BOARD;
            case ULTRASONIC_SENSOR: return ModBlocks.ULTRASONIC_SENSOR;
            case H_BRIDGE: return ModBlocks.H_BRIDGE;
            case LEFT_MOTOR: case RIGHT_MOTOR: return ModBlocks.DC_MOTOR;
            case POWER_5V: return ModBlocks.POWER_SOURCE;
            case GROUND: return ModBlocks.GROUND;
            default: throw new IllegalArgumentException("module type");
        }
    }

    static int expectedMetadata(RobotAssemblyLayout.Slot slot, int facing) {
        switch (slot.type) {
            case CHASSIS_CORE: case ULTRASONIC_SENSOR: case H_BRIDGE: return facing;
            case LEFT_MOTOR: case RIGHT_MOTOR: return facing == 2 || facing == 3 ? 1 : 0;
            case POWER_5V: case GROUND: return sideForVector(-slot.dx, -slot.dz);
            case ROBO_BOARD: return -1;
            default: throw new IllegalArgumentException("module type");
        }
    }

    private static List<BlockSnapshot> capture(World world, int x, int y, int z, int facing) {
        List<BlockSnapshot> values = new ArrayList<BlockSnapshot>();
        for (RobotAssemblyLayout.Slot slot : RobotAssemblyLayout.slots(facing)) {
            int px = x + slot.dx, py = y + slot.dy, pz = z + slot.dz;
            TileEntity tile = world.getTileEntity(px, py, pz);
            NBTTagCompound nbt = null;
            if (tile != null) { nbt = new NBTTagCompound(); tile.writeToNBT(nbt); }
            values.add(new BlockSnapshot(px, py, pz, world.getBlock(px, py, pz),
                    world.getBlockMetadata(px, py, pz), nbt));
        }
        return values;
    }

    private static boolean removeAll(World world, List<BlockSnapshot> values) {
        for (BlockSnapshot value : values) if (!world.setBlockToAir(value.x, value.y, value.z)) return false;
        return true;
    }

    private static boolean restoreAll(World world, List<BlockSnapshot> values) {
        boolean restored = true;
        for (BlockSnapshot value : values) {
            if (!world.setBlock(value.x, value.y, value.z, value.block, value.metadata, 3)) { restored = false; continue; }
            if (value.tile != null) {
                TileEntity tile = world.getTileEntity(value.x, value.y, value.z);
                if (tile == null) restored = false;
                else { tile.readFromNBT(value.tile); tile.markDirty(); world.markBlockForUpdate(value.x, value.y, value.z); }
            }
        }
        return restored;
    }

    @SuppressWarnings("unchecked")
    private static boolean volumeLoadedAndEmpty(World world, EntityMobileRobot robot,
                                                int x, int y, int z, int facing) {
        AxisAlignedBB volume = AxisAlignedBB.getBoundingBox(x - 1, y, z - 1, x + 2, y + 2, z + 2);
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(robot, volume);
        if (!entities.isEmpty()) return false;
        for (RobotAssemblyLayout.Slot slot : RobotAssemblyLayout.slots(facing)) {
            int px = x + slot.dx, py = y + slot.dy, pz = z + slot.dz;
            if (!world.getChunkProvider().chunkExists(px >> 4, pz >> 4) || !world.isAirBlock(px, py, pz)) return false;
        }
        return true;
    }

    private static void clearPlaced(World world, List<BlockSnapshot> placed) {
        for (BlockSnapshot value : placed) world.setBlockToAir(value.x, value.y, value.z);
    }

    static int sideForVector(int dx, int dz) {
        if (dx < 0) return 4; if (dx > 0) return 5;
        if (dz < 0) return 2; if (dz > 0) return 3;
        throw new IllegalArgumentException("zero direction");
    }

    static float yawForFacing(int facing) {
        return facing == 2 ? -180.0F : facing == 3 ? 0.0F : facing == 4 ? 90.0F : -90.0F;
    }

    static int facingForYaw(float yaw) {
        float normalized = MathHelper.wrapAngleTo180_float(yaw);
        if (normalized >= -45.0F && normalized < 45.0F) return 3;
        if (normalized >= 45.0F && normalized < 135.0F) return 4;
        if (normalized >= -135.0F && normalized < -45.0F) return 5;
        return 2;
    }

    private static float angleDistance(float a, float b) {
        return StrictMath.abs(MathHelper.wrapAngleTo180_float(a - b));
    }

    static final class Validation {
        final String message, slotName; final int x, y, z, facing; final TileEntityRoboBoard board;
        private Validation(String message, String slotName, int x, int y, int z,
                           int facing, TileEntityRoboBoard board) {
            this.message = message; this.slotName = slotName; this.x = x; this.y = y; this.z = z;
            this.facing = facing; this.board = board;
        }
        boolean valid() { return board != null; }
        static Validation success(int facing, TileEntityRoboBoard board) {
            return new Validation("", "", 0, 0, 0, facing, board);
        }
        static Validation failure(String message, String slot, int x, int y, int z) {
            return new Validation(message, slot, x, y, z, -1, null);
        }
    }

    private static final class BlockSnapshot {
        final int x, y, z, metadata; final Block block; final NBTTagCompound tile;
        BlockSnapshot(int x, int y, int z, Block block, int metadata, NBTTagCompound tile) {
            this.x = x; this.y = y; this.z = z; this.block = block; this.metadata = metadata; this.tile = tile;
        }
    }
}
