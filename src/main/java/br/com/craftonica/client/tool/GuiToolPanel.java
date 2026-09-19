package br.com.craftonica.client.tool;

import br.com.craftonica.electrical.MultimeterMode;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tool.network.ToolAction;
import br.com.craftonica.tool.network.ToolActionMessage;
import br.com.craftonica.tool.network.ToolNetwork;
import br.com.craftonica.tool.network.ToolStateMessage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

public final class GuiToolPanel extends GuiScreen {
    private static final int CLOSE = 1;
    private static final int CLEAR = 2;
    private static final int MODE_BASE = 20;
    private static final int ROLE_BASE = 100;
    private final ToolClientController controller;
    private ToolStateMessage displayed;

    public GuiToolPanel(ToolClientController controller) {
        this.controller = controller;
    }

    @Override
    public void initGui() {
        rebuild();
    }

    @Override
    public void updateScreen() {
        if (displayed != controller.getState()) rebuild();
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        displayed = controller.getState();
        buttonList.clear();
        if (displayed == null) return;
        int center = width / 2;
        if (displayed.getType() == ToolStateMessage.MULTIMETER) {
            MultimeterMode[] modes = MultimeterMode.values();
            int available = Math.min(420, width - 20);
            int buttonWidth = (available - (modes.length - 1) * 4) / modes.length;
            int start = center - available / 2;
            for (int i = 0; i < modes.length; i++)
                buttonList.add(new GuiButton(MODE_BASE + i, start + i * (buttonWidth + 4), height / 2 + 45,
                        buttonWidth, 20, tr("mode.craftonica.multimeter." + modes[i].getId())));
            buttonList.add(new GuiButton(CLEAR, center - 102, height / 2 + 72, 100, 20,
                    tr("tool.craftonica.clear")));
            buttonList.add(new GuiButton(CLOSE, center + 2, height / 2 + 72, 100, 20,
                    tr("tool.craftonica.close")));
        } else {
            TileEntityRoboPort.Role[] roles = TileEntityRoboPort.Role.values();
            int columns = 6, buttonWidth = Math.max(32, Math.min(44, (width - 32) / columns - 2));
            int startX = center - columns * (buttonWidth + 2) / 2;
            int startY = height / 2 - 38;
            for (int i = 0; i < roles.length; i++) {
                String roleName = roles[i] == TileEntityRoboPort.Role.POWER_5V ? "5V"
                        : roles[i] == TileEntityRoboPort.Role.GROUND ? "GND" : roles[i].name();
                String label = displayed.isPortSet() && displayed.getRole() == i
                        ? "[" + roleName + "]" : roleName;
                GuiButton role = new GuiButton(ROLE_BASE + i,
                        startX + i % columns * (buttonWidth + 2), startY + i / columns * 22,
                        buttonWidth, 20, label);
                role.enabled = displayed.isPortSet();
                buttonList.add(role);
            }
            buttonList.add(new GuiButton(CLEAR, center - 102, height / 2 + 58, 100, 20,
                    tr("tool.craftonica.configurator.clear")));
            buttonList.add(new GuiButton(CLOSE, center + 2, height / 2 + 58, 100, 20,
                    tr("tool.craftonica.close")));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (displayed == null || !button.enabled) return;
        if (button.id == CLOSE) {
            mc.displayGuiScreen(null);
        } else if (displayed.getType() == ToolStateMessage.MULTIMETER) {
            if (button.id == CLEAR)
                ToolNetwork.sendToServer(new ToolActionMessage(ToolAction.MULTIMETER_CLEAR, 0, 0, 0, 0, 0));
            else if (button.id >= MODE_BASE && button.id < MODE_BASE + MultimeterMode.values().length)
                ToolNetwork.sendToServer(new ToolActionMessage(ToolAction.MULTIMETER_MODE,
                        button.id - MODE_BASE, 0, 0, 0, 0));
        } else if (button.id == CLEAR) {
            ToolNetwork.sendToServer(new ToolActionMessage(ToolAction.ROBOPORT_CLEAR_BOARD, 0, 0, 0, 0, 0));
        } else if (button.id >= ROLE_BASE && button.id < ROLE_BASE + TileEntityRoboPort.Role.values().length
                && displayed.isPortSet()) {
            ToolNetwork.sendToServer(new ToolActionMessage(ToolAction.ROBOPORT_ROLE,
                    button.id - ROLE_BASE, displayed.getPortX(), displayed.getPortY(), displayed.getPortZ(),
                    displayed.getRevision()));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (displayed == null) return;
        int center = width / 2;
        int panelHeight = displayed.getType() == ToolStateMessage.MULTIMETER ? 180 : 210;
        int top = height / 2 - panelHeight / 2;
        String title = displayed.getType() == ToolStateMessage.MULTIMETER
                ? tr("tool.craftonica.multimeter.title") : tr("tool.craftonica.configurator.title");
        drawCenteredString(fontRendererObj, title, center, top + 12, 0xFFFFFF);
        if (displayed.getType() == ToolStateMessage.MULTIMETER) drawMultimeter(center, top);
        else drawConfigurator(center, top);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawMultimeter(int center, int top) {
        MultimeterMode[] modes = MultimeterMode.values();
        int mode = displayed.getMode();
        String modeName = mode >= 0 && mode < modes.length
                ? tr("mode.craftonica.multimeter." + modes[mode].getId()) : "?";
        drawCenteredString(fontRendererObj, tr("tool.craftonica.mode") + ": " + modeName,
                center, top + 31, 0xA0A0A0);
        drawCenteredString(fontRendererObj, probe("A", displayed.isFirstSet(), displayed.getFirstX(),
                displayed.getFirstY(), displayed.getFirstZ()), center, top + 49, 0xEF5350);
        drawCenteredString(fontRendererObj, probe("B", displayed.isSecondSet(), displayed.getSecondX(),
                displayed.getSecondY(), displayed.getSecondZ()), center, top + 62, 0xB0BEC5);
        drawCenteredString(fontRendererObj, localized(displayed.getHeadline()), center, top + 82, 0xFFFFFF);
        drawCenteredString(fontRendererObj, localizedPayload(displayed.getDetail()), center, top + 98, 0xFFCC66);
    }

    private void drawConfigurator(int center, int top) {
        String board = displayed.isBoardSet() ? coords(displayed.getBoardX(), displayed.getBoardY(), displayed.getBoardZ())
                : tr("tool.craftonica.none");
        String port = displayed.isPortSet() ? coords(displayed.getPortX(), displayed.getPortY(), displayed.getPortZ())
                : tr("tool.craftonica.none");
        drawCenteredString(fontRendererObj, tr("tool.craftonica.configurator.board") + ": " + board,
                center, top + 28, 0xA0A0A0);
        drawCenteredString(fontRendererObj, tr("tool.craftonica.configurator.port_label") + ": " + port,
                center, top + 41, 0xFFB300);
        drawCenteredString(fontRendererObj, localized(displayed.getHeadline()), center, top + 56, 0xFFFFFF);
    }

    private String probe(String name, boolean set, int x, int y, int z) {
        return name + ": " + (set ? coords(x, y, z) : tr("tool.craftonica.none"));
    }

    private String coords(int x, int y, int z) { return x + ", " + y + ", " + z; }
    private String localized(String key) { return key == null || key.length() == 0 ? "" : tr(key); }
    private String localizedPayload(String payload) {
        if (payload == null || payload.length() == 0) return "";
        int separator = payload.indexOf('|');
        return separator < 0 ? tr(payload) : StatCollector.translateToLocalFormatted(
                payload.substring(0, separator), payload.substring(separator + 1));
    }
    private String tr(String key) { return StatCollector.translateToLocal(key); }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
