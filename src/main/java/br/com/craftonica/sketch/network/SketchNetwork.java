package br.com.craftonica.sketch.network;

import br.com.craftonica.Craftonica;
import br.com.craftonica.sketch.server.SketchServer;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

public final class SketchNetwork {
    private static SimpleNetworkWrapper channel;

    private SketchNetwork() {}

    public static synchronized void initialize() {
        if (channel != null) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("craftonica_sketch");
        channel.registerMessage(ActionHandler.class, EditorActionMessage.class, 0, Side.SERVER);
        channel.registerMessage(StateHandler.class, EditorStateMessage.class, 1, Side.CLIENT);
    }

    public static void sendTo(EntityPlayerMP player, EditorStateMessage state) {
        if (channel == null) throw new IllegalStateException("Sketch channel is not initialized");
        channel.sendTo(state, player);
    }

    public static void sendToServer(EditorActionMessage action) {
        if (channel == null) throw new IllegalStateException("Sketch channel is not initialized");
        channel.sendToServer(action);
    }

    public static final class ActionHandler implements IMessageHandler<EditorActionMessage, IMessage> {
        @Override
        public IMessage onMessage(EditorActionMessage message, MessageContext context) {
            if (message.isValid() && context.getServerHandler() != null)
                SketchServer.enqueue(context.getServerHandler().playerEntity, message);
            return null;
        }
    }

    public static final class StateHandler implements IMessageHandler<EditorStateMessage, IMessage> {
        @Override
        public IMessage onMessage(EditorStateMessage message, MessageContext context) {
            if (message.isValid()) Craftonica.proxy.handleEditorState(message);
            return null;
        }
    }
}
