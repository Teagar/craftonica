package br.com.craftonica.client.tool;

import br.com.craftonica.registry.ModItems;
import br.com.craftonica.tool.network.ToolStateMessage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ToolClientController {
    public static final ToolClientController INSTANCE = new ToolClientController();
    private final Queue<ToolStateMessage> pending = new ConcurrentLinkedQueue<ToolStateMessage>();
    private volatile ToolStateMessage state;
    private boolean openRequested;

    private ToolClientController() {
    }

    public void enqueue(ToolStateMessage message) {
        if (message != null && message.isValid() && pending.size() < 32) pending.add(message);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ToolStateMessage next;
        while ((next = pending.poll()) != null) {
            state = next;
            openRequested = true;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (openRequested && state != null && minecraft.thePlayer != null
                && (minecraft.currentScreen == null || minecraft.currentScreen instanceof GuiToolPanel)) {
            ItemStack held = minecraft.thePlayer.getHeldItem();
            boolean matching = held != null && (state.getType() == ToolStateMessage.MULTIMETER
                    ? held.getItem() == ModItems.MULTIMETER
                    : held.getItem() == ModItems.ROBO_PORT_CONFIGURATOR);
            if (matching && !(minecraft.currentScreen instanceof GuiToolPanel)) {
                minecraft.displayGuiScreen(new GuiToolPanel(this));
                openRequested = false;
            } else if (matching) openRequested = false;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        ToolStateMessage current = state;
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (current == null || player == null || minecraft.theWorld == null
                || current.getDimension() != minecraft.theWorld.provider.dimensionId) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;
        if (current.getType() == ToolStateMessage.MULTIMETER && held.getItem() == ModItems.MULTIMETER) {
            if (current.isFirstSet() && isPresent(current.getFirstX(), current.getFirstY(), current.getFirstZ()))
                renderProbe(player, event.partialTicks,
                    current.getFirstX(), current.getFirstY(), current.getFirstZ(), 0xE53935);
            if (current.isSecondSet() && isPresent(current.getSecondX(), current.getSecondY(), current.getSecondZ()))
                renderProbe(player, event.partialTicks,
                    current.getSecondX(), current.getSecondY(), current.getSecondZ(), 0x263238);
        } else if (current.getType() == ToolStateMessage.ROBOPORT
                && held.getItem() == ModItems.ROBO_PORT_CONFIGURATOR) {
            if (current.isBoardSet() && isPresent(current.getBoardX(), current.getBoardY(), current.getBoardZ()))
                renderProbe(player, event.partialTicks,
                    current.getBoardX(), current.getBoardY(), current.getBoardZ(), 0x35D6D0);
            if (current.isPortSet() && isPresent(current.getPortX(), current.getPortY(), current.getPortZ()))
                renderProbe(player, event.partialTicks,
                    current.getPortX(), current.getPortY(), current.getPortZ(), 0xFFB300);
        }
    }

    private boolean isPresent(int x, int y, int z) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.theWorld.blockExists(x, y, z) && !minecraft.theWorld.isAirBlock(x, y, z);
    }

    private void renderProbe(EntityPlayer player, float partialTicks, int x, int y, int z, int color) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double eye = py + player.getEyeHeight() - 0.35;
        double handX = px - Math.cos(Math.toRadians(player.rotationYaw)) * 0.25;
        double handZ = pz - Math.sin(Math.toRadians(player.rotationYaw)) * 0.25;
        double r = ((color >> 16) & 255) / 255.0;
        double g = ((color >> 8) & 255) / 255.0;
        double b = (color & 255) / 255.0;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(3.0F);
        GL11.glColor4d(r, g, b, 0.9);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINE_STRIP);
        tessellator.addVertex(handX, eye, handZ);
        tessellator.addVertex((handX + x + 0.5) * 0.5, Math.max(eye, y + 1.4), (handZ + z + 0.5) * 0.5);
        tessellator.addVertex(x + 0.5, y + 0.5, z + 0.5);
        tessellator.draw();
        RenderGlobal.drawOutlinedBoundingBox(AxisAlignedBB.getBoundingBox(
                x - 0.01, y - 0.01, z - 0.01, x + 1.01, y + 1.01, z + 1.01), color);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    public ToolStateMessage getState() { return state; }
}
