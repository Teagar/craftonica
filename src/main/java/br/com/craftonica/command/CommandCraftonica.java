package br.com.craftonica.command;

import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.lesson.LessonCatalog;
import br.com.craftonica.lesson.LessonEngine;
import br.com.craftonica.lesson.LessonDefinition;
import br.com.craftonica.lesson.TeacherActivityData;
import br.com.craftonica.lesson.TeacherActivityParser;
import br.com.craftonica.lesson.TeacherProgressExporter;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.showcase.ShowcaseGenerator;
import br.com.craftonica.showcase.UnoR3Generator;
import br.com.craftonica.showcase.UltrasonicLabGenerator;
import br.com.craftonica.robot.EntityMobileRobot;
import br.com.craftonica.robot.MobileRobotSpawner;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;

import java.io.File;
import java.io.IOException;

public final class CommandCraftonica extends CommandBase {
    private final LessonEngine lessons = new LessonEngine();

    @Override public String getCommandName() { return "craftonica"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "commands.craftonica.usage"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.player_only"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length >= 2 && "teacher".equals(args[0])) {
            teacher(player, args);
        } else if (args.length == 2 && "showcase".equals(args[0]) && "create".equals(args[1])) {
            showcase(player);
        } else if (args.length == 2 && "uno".equals(args[0]) && "create".equals(args[1])) {
            uno(player);
        } else if (args.length == 2 && "sonar".equals(args[0]) && "create".equals(args[1])) {
            sonar(player);
        } else if (args.length == 2 && "robot".equals(args[0]) && "create".equals(args[1])) {
            robot(player);
        } else if (args.length == 3 && "robot".equals(args[0]) && "drive".equals(args[1])) {
            driveRobot(player, args[2]);
        } else if (args.length == 3 && "robot".equals(args[0]) && "firmware".equals(args[1])) {
            robotFirmware(player, args[2]);
        } else if (args.length == 2 && "lesson".equals(args[0]) && "list".equals(args[1])) {
            for (String id : LessonCatalog.ids()) player.addChatMessage(new ChatComponentText(id));
            String assigned = TeacherActivityData.get(player.worldObj).getAssignedId();
            if (assigned != null) player.addChatMessage(new ChatComponentText("assigned -> " + assigned));
        } else if (args.length == 3 && "lesson".equals(args[0]) && "start".equals(args[1])) {
            lessons.start(player, args[2]);
        } else if (args.length == 2 && "lesson".equals(args[0]) && "status".equals(args[1])) {
            lessons.status(player);
        } else if (args.length == 5 && "lesson".equals(args[0]) && "check".equals(args[1])) {
            check(player, args);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void showcase(EntityPlayerMP player) {
        if (!player.canCommandSenderUseCommand(2, getCommandName())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.denied"));
            return;
        }
        int x = ((int) Math.floor(player.posX) & ~15) - 24;
        int y = Math.max(4, (int) Math.floor(player.posY));
        int z = ((int) Math.floor(player.posZ) & ~15) - 8;
        ShowcaseGenerator.generate((WorldServer) player.worldObj, player, x, y, z);
        player.setPositionAndUpdate(x + 24.5D, y + 1.0D, z + 2.5D);
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.showcase.created", x, y, z));
    }

    private void uno(EntityPlayerMP player) {
        if (!player.canCommandSenderUseCommand(2, getCommandName())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.denied"));
            return;
        }
        int x = ((int) Math.floor(player.posX) & ~15) - 6;
        int y = Math.max(4, player.worldObj.getTopSolidOrLiquidBlock(
                (int) Math.floor(player.posX), (int) Math.floor(player.posZ)));
        int z = ((int) Math.floor(player.posZ) & ~15) - 9;
        UnoR3Generator.generate((WorldServer) player.worldObj, player, x, y, z);
        player.setPositionAndUpdate(x + 6.5D, y + 2.0D, z + UnoR3Generator.DEPTH + 1.5D);
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.uno.created", x, y, z));
    }

    private void sonar(EntityPlayerMP player) {
        if (!player.canCommandSenderUseCommand(2, getCommandName())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.denied"));
            return;
        }
        int x = ((int) Math.floor(player.posX) & ~15) + 32;
        int z = (int) Math.floor(player.posZ) & ~15;
        int y = Math.max(5, (int) Math.floor(player.posY));
        UltrasonicLabGenerator.generate((WorldServer) player.worldObj, player, x, y, z);
        player.setPositionAndUpdate(x + UltrasonicLabGenerator.WIDTH / 2 + 0.5D, y + 1.0D, z + 2.5D);
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.sonar.created", x, y, z));
    }

    private void robot(EntityPlayerMP player) {
        if (!player.canCommandSenderUseCommand(2, getCommandName())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.denied"));
            return;
        }
        EntityMobileRobot robot = MobileRobotSpawner.spawnInFront((WorldServer) player.worldObj, player);
        player.addChatMessage(new ChatComponentTranslation(robot == null
                ? "message.craftonica.robot.spawn_blocked" : "message.craftonica.robot.created"));
    }

    @SuppressWarnings("unchecked")
    private void driveRobot(EntityPlayerMP player, String action) {
        if (!("forward".equals(action) || "reverse".equals(action) || "left".equals(action)
                || "right".equals(action) || "stop".equals(action))) throw new WrongUsageException(getCommandUsage(player));
        EntityMobileRobot nearest = null;
        double distance = 256.0;
        for (Object value : player.worldObj.loadedEntityList) if (value instanceof EntityMobileRobot) {
            EntityMobileRobot candidate = (EntityMobileRobot) value;
            if (!candidate.getRobotState().getOwnerId().equals(player.getUniqueID())) continue;
            double next = candidate.getDistanceSqToEntity(player);
            if (next < distance) { distance = next; nearest = candidate; }
        }
        if (nearest == null) player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_found"));
        else { nearest.commandTestDrive(action); player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.drive", action)); }
    }

    private void robotFirmware(EntityPlayerMP player, String action) {
        EntityMobileRobot robot = nearestRobot(player);
        if (robot == null) { player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.not_found")); return; }
        try {
            if ("status".equals(action)) {
                br.com.craftonica.tile.RoboBoardState board = robot.getRobotState().getBoardState();
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.firmware_status",
                        board.getStatus().name(), board.getRevision(), board.getSerialHistorySnapshot().getEndOffset()));
                return;
            } else if ("copy".equals(action)) {
                TileEntityRoboBoard source = nearestBoard(player);
                if (source == null || !source.canAccess(player)) {
                    player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.board_not_found")); return;
                }
                robot.installBoardCopy(source.copyBoardState());
            } else if ("start".equals(action)) robot.startBoard();
            else if ("stop".equals(action)) robot.stopBoard();
            else throw new WrongUsageException(getCommandUsage(player));
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.firmware", action));
        } catch (RuntimeException rejected) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.robot.firmware_rejected"));
        }
    }

    private EntityMobileRobot nearestRobot(EntityPlayerMP player) {
        EntityMobileRobot nearest = null; double distance = 256.0;
        for (Object value : player.worldObj.loadedEntityList) if (value instanceof EntityMobileRobot) {
            EntityMobileRobot candidate = (EntityMobileRobot) value;
            if (!candidate.getRobotState().getOwnerId().equals(player.getUniqueID())) continue;
            double next = candidate.getDistanceSqToEntity(player);
            if (next < distance) { distance = next; nearest = candidate; }
        }
        return nearest;
    }

    private TileEntityRoboBoard nearestBoard(EntityPlayerMP player) {
        TileEntityRoboBoard nearest = null; double distance = 256.0;
        for (Object value : player.worldObj.loadedTileEntityList) if (value instanceof TileEntityRoboBoard) {
            TileEntityRoboBoard candidate = (TileEntityRoboBoard) value;
            double next = player.getDistanceSq(candidate.xCoord + 0.5, candidate.yCoord + 0.5, candidate.zCoord + 0.5);
            if (next < distance) { distance = next; nearest = candidate; }
        }
        return nearest;
    }

    private void check(EntityPlayerMP player, String[] args) {
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args[2]); y = Integer.parseInt(args[3]); z = Integer.parseInt(args[4]);
        } catch (NumberFormatException invalidCoordinate) {
            throw new WrongUsageException(getCommandUsage(player));
        }
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 4096.0D) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.too_far"));
            return;
        }
        WorldServer world = (WorldServer) player.worldObj;
        if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4)
                || !(world.getBlock(x, y, z) instanceof IElectricalBlock)) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.invalid_anchor"));
            return;
        }
        lessons.check(player, new BlockPosition(x, y, z));
    }

    private void teacher(EntityPlayerMP player, String[] args) {
        if (!player.canCommandSenderUseCommand(2, getCommandName())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.denied"));
            return;
        }
        TeacherActivityData data = TeacherActivityData.get(player.worldObj);
        try {
            if ((args.length == 5 || args.length == 10) && "create".equals(args[1])) {
                LessonDefinition activity = args.length == 5
                        ? TeacherActivityParser.parseCompact(args[2], args[3], args[4])
                        : TeacherActivityParser.parse(args[2], args[3], args[4], args[5],
                                args[6], args[7], args[8], args[9]);
                data.put(activity);
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.created", activity.getId()));
            } else if (args.length == 3 && "assign".equals(args[1])) {
                if (LessonCatalog.get(args[2]) == null && data.getActivity(args[2]) == null)
                    throw new IllegalArgumentException("Licao desconhecida");
                data.assign(args[2]);
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.assigned", args[2]));
            } else if (args.length == 2 && "list".equals(args[1])) {
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.assignment",
                        data.getAssignedId() == null ? "-" : data.getAssignedId()));
                for (String id : data.getActivityIds()) player.addChatMessage(new ChatComponentText(id));
            } else if (args.length == 2 && "export".equals(args[1])) {
                File exported = TeacherProgressExporter.export((WorldServer) player.worldObj);
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.exported",
                        exported.getName()));
            } else {
                throw new WrongUsageException(getCommandUsage(player));
            }
        } catch (IllegalArgumentException invalidActivity) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.invalid",
                    invalidActivity.getMessage()));
        } catch (IllegalStateException readOnly) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.readonly"));
        } catch (IOException exportFailure) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.export_failed"));
        }
    }
}
