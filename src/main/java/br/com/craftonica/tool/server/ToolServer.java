package br.com.craftonica.tool.server;

import br.com.craftonica.item.ItemMultimeter;
import br.com.craftonica.item.ItemRoboPortConfigurator;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.tool.network.ToolActionMessage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ToolServer {
    public static final ToolServer EVENTS = new ToolServer();
    private static final int MAX_PENDING = 256;
    private final Queue<Pending> pending = new ConcurrentLinkedQueue<Pending>();

    private ToolServer() {
    }

    public static void enqueue(EntityPlayerMP player, ToolActionMessage action) {
        if (player == null || action == null || !action.isValid()) return;
        if (EVENTS.pending.size() >= MAX_PENDING) return;
        EVENTS.pending.add(new Pending(player, action));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Pending next;
        int budget = MAX_PENDING;
        while (budget-- > 0 && (next = pending.poll()) != null) apply(next);
    }

    private void apply(Pending pendingAction) {
        EntityPlayerMP player = pendingAction.player;
        if (player.playerNetServerHandler == null || player.worldObj == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;
        ToolActionMessage action = pendingAction.action;
        switch (action.getAction()) {
            case MULTIMETER_MODE:
            case MULTIMETER_CLEAR:
                if (held.getItem() == ModItems.MULTIMETER)
                    ((ItemMultimeter) ModItems.MULTIMETER).handleGuiAction(held, player, action);
                break;
            case ROBOPORT_ROLE:
            case ROBOPORT_CLEAR_BOARD:
                if (held.getItem() == ModItems.ROBO_PORT_CONFIGURATOR)
                    ((ItemRoboPortConfigurator) ModItems.ROBO_PORT_CONFIGURATOR)
                            .handleGuiAction(held, player, action);
                break;
            default:
                break;
        }
    }

    private static final class Pending {
        final EntityPlayerMP player;
        final ToolActionMessage action;

        Pending(EntityPlayerMP player, ToolActionMessage action) {
            this.player = player;
            this.action = action;
        }
    }
}
