package br.com.craftonica.command;

import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.lesson.LessonCatalog;
import br.com.craftonica.lesson.LessonEngine;
import br.com.craftonica.network.BlockPosition;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;

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
        if (args.length == 2 && "lesson".equals(args[0]) && "list".equals(args[1])) {
            for (String id : LessonCatalog.ids()) player.addChatMessage(new ChatComponentText(id));
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
}
