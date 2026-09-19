package br.com.craftonica.client.sketch;

import br.com.craftonica.sketch.network.EditorStateMessage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SketchClientController {
    public static final SketchClientController INSTANCE = new SketchClientController();

    private final ConcurrentLinkedQueue<ClientEditorState> pending = new ConcurrentLinkedQueue<ClientEditorState>();
    private final Set<String> closedEditors = new HashSet<String>();
    private final Map<String, Draft> drafts = new HashMap<String, Draft>();

    private SketchClientController() {}

    /** Called from the packet handler. It deliberately has no Minecraft UI access. */
    public void enqueue(EditorStateMessage message) {
        if (message != null && message.isValid()) pending.offer(ClientEditorState.copyOf(message));
    }

    public void editorClosed(ClientEditorState state, String draft, long revision) {
        String key = key(state.dimension, state.x, state.y, state.z);
        closedEditors.add(key);
        String draftKey = draftKey(state);
        if (draft == null) drafts.remove(draftKey);
        else drafts.put(draftKey, new Draft(draft, revision));
    }

    public void allowOpen(int dimension, int x, int y, int z) {
        closedEditors.remove(key(dimension, x, y, z));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientEditorState state;
        while ((state = pending.poll()) != null) applyOnClientThread(state);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        pending.clear();
        closedEditors.clear();
        drafts.clear();
    }

    private void applyOnClientThread(ClientEditorState state) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null
                || minecraft.theWorld.provider.dimensionId != state.dimension) return;
        if (minecraft.currentScreen instanceof GuiSketchEditor) {
            GuiSketchEditor editor = (GuiSketchEditor) minecraft.currentScreen;
            if (editor.isFor(state)) {
                editor.applyState(state);
                return;
            }
        }
        if (closedEditors.contains(key(state.dimension, state.x, state.y, state.z))) return;
        String key = key(state.dimension, state.x, state.y, state.z);
        Draft draft = drafts.get(draftKey(state));
        minecraft.displayGuiScreen(new GuiSketchEditor(state, draft == null ? null : draft.text,
                draft == null ? state.revision : draft.revision));
    }

    private static String key(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    private static String draftKey(ClientEditorState state) {
        return state.boardId.toString();
    }

    private static final class Draft {
        private final String text;
        private final long revision;

        private Draft(String text, long revision) {
            this.text = text;
            this.revision = revision;
        }
    }
}
