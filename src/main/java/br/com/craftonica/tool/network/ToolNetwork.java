package br.com.craftonica.tool.network;

import br.com.craftonica.Craftonica;
import br.com.craftonica.tool.server.ToolServer;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

public final class ToolNetwork {
    private static SimpleNetworkWrapper channel;

    private ToolNetwork() {
    }

    public static synchronized void initialize() {
        if (channel != null) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("craftonica_tools");
        channel.registerMessage(ActionHandler.class, ToolActionMessage.class, 0, Side.SERVER);
        channel.registerMessage(StateHandler.class, ToolStateMessage.class, 1, Side.CLIENT);
    }

    public static void sendTo(EntityPlayerMP player, ToolStateMessage state) {
        if (channel == null) throw new IllegalStateException("Tool channel is not initialized");
        channel.sendTo(state, player);
    }

    public static void sendToServer(ToolActionMessage action) {
        if (channel == null) throw new IllegalStateException("Tool channel is not initialized");
        channel.sendToServer(action);
    }

    public static final class ActionHandler implements IMessageHandler<ToolActionMessage, IMessage> {
        @Override
        public IMessage onMessage(ToolActionMessage message, MessageContext context) {
            if (message.isValid() && context.getServerHandler() != null)
                ToolServer.enqueue(context.getServerHandler().playerEntity, message);
            return null;
        }
    }

    public static final class StateHandler implements IMessageHandler<ToolStateMessage, IMessage> {
        @Override
        public IMessage onMessage(ToolStateMessage message, MessageContext context) {
            if (message.isValid()) Craftonica.proxy.handleToolState(message);
            return null;
        }
    }
}
