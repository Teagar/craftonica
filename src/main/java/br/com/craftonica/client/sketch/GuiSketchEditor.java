package br.com.craftonica.client.sketch;

import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.sketch.network.EditorActionMessage;
import br.com.craftonica.sketch.network.SketchAction;
import br.com.craftonica.sketch.network.SketchNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GuiSketchEditor extends GuiScreen {
    private static final int COMPILE = 1, START_STOP = 2, RELOAD = 3, EDITOR = 4, SERIAL = 5;
    private static final int PCB = 0xff071411, PANEL = 0xff0b211c, PANEL_ALT = 0xff0e2b24;
    private static final int TRACE = 0xff1e5548, AMBER = 0xffffb340, CYAN = 0xff52d9d0;
    private static final int TEXT = 0xffd3ddd5, MUTED = 0xff729187, SELECTION = 0xff285f63;

    private final EditorDocument document = new EditorDocument(SourceBundle.MAX_FILE_BYTES);
    private ClientEditorState state;
    private byte[] submitted;
    private long editingRevision;
    private boolean serialTab;
    private boolean repeatWasEnabled;
    private boolean repeatCaptured;
    private long serialHiddenThrough;
    private long lastRefreshNanos;
    private int firstLine;
    private int horizontalColumn;
    private int paneTop, paneBottom, paneLeft, paneRight, gutterRight;
    private String indexedText;
    private List<Line> indexedLines;

    GuiSketchEditor(ClientEditorState initial, String draft, long draftRevision) {
        state = initial;
        editingRevision = initial.revision;
        document.setAuthoritativeText(new String(initial.sourceBytes(), StandardCharsets.UTF_8));
        if (draft != null) {
            document.setDraftText(draft);
            editingRevision = draftRevision;
        }
    }

    boolean isFor(ClientEditorState candidate) { return state.sameBoard(candidate); }

    void applyState(ClientEditorState next) {
        if (!isFor(next)) return;
        state = next;
        if (submitted != null && "craftonica.editor.compile_success".equals(next.compilationState)) {
            byte[] local = document.getUtf8();
            byte[] authoritative = next.sourceBytes();
            if (Arrays.equals(submitted, authoritative) && Arrays.equals(submitted, local)) {
                document.setAuthoritativeText(new String(authoritative, StandardCharsets.UTF_8));
                editingRevision = next.revision;
            }
            submitted = null;
        } else if (submitted != null && isTerminalCompilation(next.compilationState)) submitted = null;
        if (submitted == null && !document.isDirty()) {
            document.setAuthoritativeText(new String(next.sourceBytes(), StandardCharsets.UTF_8));
            editingRevision = next.revision;
        }
        updateButtons();
    }

    @Override
    public void initGui() {
        if (!repeatCaptured) {
            repeatWasEnabled = Keyboard.areRepeatEventsEnabled();
            repeatCaptured = true;
        }
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int gap = 3;
        if (width < 400) {
            int actionWidth = (width - 16 - gap * 2) / 3;
            buttonList.add(new GuiButton(COMPILE, 8, 20, actionWidth, 18, tr("craftonica.editor.button.compile")));
            buttonList.add(new GuiButton(START_STOP, 8 + actionWidth + gap, 20, actionWidth, 18, ""));
            buttonList.add(new GuiButton(RELOAD, 8 + (actionWidth + gap) * 2, 20, actionWidth, 18,
                    tr("craftonica.editor.button.reload")));
            int tabWidth = (width - 16 - gap) / 2;
            buttonList.add(new GuiButton(EDITOR, 8, 41, tabWidth, 18, tr("craftonica.editor.tab.editor")));
            buttonList.add(new GuiButton(SERIAL, 8 + tabWidth + gap, 41, tabWidth, 18,
                    tr("craftonica.editor.tab.serial")));
        } else {
            int available = width - 16 - gap * 4;
            int buttonWidth = available / 5;
            int x = 8;
            buttonList.add(new GuiButton(COMPILE, x, 20, buttonWidth, 18, tr("craftonica.editor.button.compile")));
            x += buttonWidth + gap;
            buttonList.add(new GuiButton(START_STOP, x, 20, buttonWidth, 18, ""));
            x += buttonWidth + gap;
            buttonList.add(new GuiButton(RELOAD, x, 20, buttonWidth, 18, tr("craftonica.editor.button.reload")));
            x += buttonWidth + gap;
            buttonList.add(new GuiButton(EDITOR, x, 20, buttonWidth, 18, tr("craftonica.editor.tab.editor")));
            x += buttonWidth + gap;
            buttonList.add(new GuiButton(SERIAL, x, 20, width - 8 - x, 18, tr("craftonica.editor.tab.serial")));
        }
        layout();
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        if (repeatCaptured) Keyboard.enableRepeatEvents(repeatWasEnabled);
        SketchClientController.INSTANCE.editorClosed(state, document.isDirty() ? document.getText() : null,
                editingRevision);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        long now = System.nanoTime();
        if (now - lastRefreshNanos >= 1000000000L) {
            send(SketchAction.REFRESH, null);
            lastRefreshNanos = now;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == COMPILE) compile();
        else if (button.id == START_STOP) send(isRunning() ? SketchAction.STOP : SketchAction.START, null);
        else if (button.id == RELOAD) reload();
        else if (button.id == EDITOR) serialTab = false;
        else if (button.id == SERIAL) serialTab = true;
        updateButtons();
    }

    @Override
    protected void keyTyped(char typed, int key) {
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_F6) {
            serialTab = !serialTab;
            updateButtons();
            return;
        }
        if (key == Keyboard.KEY_F5) {
            send(isRunning() ? SketchAction.STOP : SketchAction.START, null);
            return;
        }
        if (key == Keyboard.KEY_F7) {
            reload();
            return;
        }
        if (ctrl && key == Keyboard.KEY_S) {
            compile();
            return;
        }
        if (serialTab) {
            if (ctrl && key == Keyboard.KEY_L) serialHiddenThrough = state.serialEnd;
            return;
        }
        if (ctrl) {
            if (key == Keyboard.KEY_A) document.selectAll();
            else if (key == Keyboard.KEY_C) setClipboardString(document.copySelection());
            else if (key == Keyboard.KEY_X) setClipboardString(document.cutSelection());
            else if (key == Keyboard.KEY_V) document.insert(getClipboardString());
            else if (key == Keyboard.KEY_Z && shift) document.redo();
            else if (key == Keyboard.KEY_Z) document.undo();
            else if (key == Keyboard.KEY_Y) document.redo();
            ensureCaretVisible();
            return;
        }
        if (key == Keyboard.KEY_LEFT) document.moveLeft(shift);
        else if (key == Keyboard.KEY_RIGHT) document.moveRight(shift);
        else if (key == Keyboard.KEY_UP) document.moveUp(shift);
        else if (key == Keyboard.KEY_DOWN) document.moveDown(shift);
        else if (key == Keyboard.KEY_HOME) document.moveHome(shift);
        else if (key == Keyboard.KEY_END) document.moveEnd(shift);
        else if (key == Keyboard.KEY_BACK) document.backspace();
        else if (key == Keyboard.KEY_DELETE) document.deleteForward();
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) document.newline();
        else if (key == Keyboard.KEY_TAB) document.tab();
        else if (typed >= 0x20 && typed != 0x7f) document.insert(String.valueOf(typed));
        ensureCaretVisible();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (serialTab || button != 0 || mouseX < gutterRight || mouseX >= paneRight
                || mouseY < paneTop || mouseY >= paneBottom) return;
        List<Line> lines = lines();
        int lineIndex = firstLine + (mouseY - paneTop - 3) / fontRendererObj.FONT_HEIGHT;
        lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndex));
        Line line = lines.get(lineIndex);
        String value = document.getText().substring(line.start, line.end);
        int begin = charOffset(value, horizontalColumn);
        int relative = begin;
        int targetX = Math.max(0, mouseX - gutterRight - 4);
        while (relative < value.length()) {
            int next = value.offsetByCodePoints(relative, 1);
            if (fontRendererObj.getStringWidth(value.substring(begin, next)) > targetX) break;
            relative = next;
        }
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        document.setCaret(line.start + relative, shift);
        ensureCaretVisible();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        drawGradientRect(0, 0, width, height, PCB, 0xff030907);
        drawRect(5, 4, width - 5, 17, PANEL_ALT);
        drawRect(5, 17, width - 5, 18, TRACE);
        String title = "CRAFTONICA // Sketch.ino";
        fontRendererObj.drawString(title, 9, 7, CYAN);
        String revision = "R" + editingRevision + (document.isDirty() ? " *" : "");
        int revisionX = width - 9 - fontRendererObj.getStringWidth(revision);
        fontRendererObj.drawString(revision, revisionX, 7, AMBER);
        String shortcuts = tr("craftonica.editor.shortcuts");
        int shortcutWidth = fontRendererObj.getStringWidth(shortcuts);
        int shortcutX = 19 + fontRendererObj.getStringWidth(title);
        if (shortcutX + shortcutWidth + 10 <= revisionX)
            fontRendererObj.drawString(shortcuts, shortcutX, 7, MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);

        int statusTop = width < 400 ? 62 : 41;
        drawRect(6, statusTop, width - 6, statusTop + 10, PANEL_ALT);
        String status = tr("craftonica.editor.status." + statusName(state.status));
        String compiler = localizedCode(state.compilationState);
        String count = document.getUtf8Length() + " / " + SourceBundle.MAX_FILE_BYTES + " B";
        int countX = width - 10 - fontRendererObj.getStringWidth(count);
        String statusLine = fontRendererObj.trimStringToWidth(status + "  //  " + compiler,
                Math.max(8, countX - 14));
        fontRendererObj.drawString(statusLine, 10, statusTop + 1,
                state.status == 4 ? 0xffff6b5f : TEXT);
        fontRendererObj.drawString(count, countX, statusTop + 1,
                document.getUtf8Length() >= SourceBundle.MAX_FILE_BYTES ? 0xffff6b5f : MUTED);

        drawRect(paneLeft, paneTop, paneRight, paneBottom, PANEL);
        drawRect(paneLeft, paneTop, paneRight, paneTop + 1, TRACE);
        drawRect(paneLeft, paneBottom - 1, paneRight, paneBottom, TRACE);
        if (serialTab) drawSerial(); else drawEditor();
        drawDiagnostics();
    }

    private void drawEditor() {
        drawRect(paneLeft + 1, paneTop + 1, gutterRight, paneBottom - 1, 0xff091a16);
        List<Line> lines = lines();
        int rows = visibleRows();
        for (int row = 0; row < rows && firstLine + row < lines.size(); row++) {
            int index = firstLine + row;
            Line line = lines.get(index);
            int y = paneTop + 3 + row * fontRendererObj.FONT_HEIGHT;
            String number = String.valueOf(index + 1);
            fontRendererObj.drawString(number, gutterRight - 4 - fontRendererObj.getStringWidth(number), y, MUTED);
            drawEditorLine(line, y);
        }
    }

    private void drawEditorLine(Line line, int y) {
        String all = document.getText();
        String value = all.substring(line.start, line.end);
        int visibleStart = charOffset(value, horizontalColumn);
        String visible = value.substring(visibleStart);
        int maxWidth = paneRight - gutterRight - 8;
        visible = fontRendererObj.trimStringToWidth(visible, maxWidth);
        int globalStart = line.start + visibleStart;
        int globalEnd = globalStart + visible.length();
        int selectionStart = Math.max(globalStart, document.getSelectionStart());
        int selectionEnd = Math.min(globalEnd, document.getSelectionEnd());
        if (selectionStart < selectionEnd) {
            int x1 = gutterRight + 4 + fontRendererObj.getStringWidth(all.substring(globalStart, selectionStart));
            int x2 = gutterRight + 4 + fontRendererObj.getStringWidth(all.substring(globalStart, selectionEnd));
            drawRect(x1, y - 1, x2, y + fontRendererObj.FONT_HEIGHT, SELECTION);
        }
        fontRendererObj.drawString(displaySafe(visible), gutterRight + 4, y, TEXT);
        if ((System.currentTimeMillis() / 500L & 1L) == 0 && document.getCaret() >= globalStart
                && document.getCaret() <= globalEnd && document.getCaret() >= line.start
                && document.getCaret() <= line.end) {
            int x = gutterRight + 4 + fontRendererObj.getStringWidth(all.substring(globalStart, document.getCaret()));
            drawRect(x, y - 1, x + 1, y + fontRendererObj.FONT_HEIGHT, AMBER);
        }
    }

    private void drawSerial() {
        int first = (int) Math.max(0L, serialHiddenThrough - state.serialStart);
        byte[] serial = state.serialBytes();
        first = Math.min(first, serial.length);
        byte[] visible = Arrays.copyOfRange(serial, first, serial.length);
        String text = SerialSanitizer.sanitize(visible);
        List<String> rows = wrap(text, paneRight - paneLeft - 12);
        int capacity = Math.max(1, visibleRows() - 2);
        int begin = Math.max(0, rows.size() - capacity);
        int y = paneTop + 4;
        String range = "TX ONLY  [" + Math.max(serialHiddenThrough, state.serialStart) + ".." + state.serialEnd + ")";
        fontRendererObj.drawString(range, paneLeft + 6, y, AMBER);
        y += fontRendererObj.FONT_HEIGHT + 2;
        if (state.serialTruncated) {
            fontRendererObj.drawString(tr("craftonica.editor.serial.truncated"), paneLeft + 6, y, 0xffff6b5f);
            y += fontRendererObj.FONT_HEIGHT;
        }
        for (int i = begin; i < rows.size() && y + fontRendererObj.FONT_HEIGHT <= paneBottom - 2; i++) {
            fontRendererObj.drawString(displaySafe(rows.get(i)), paneLeft + 6, y, TEXT);
            y += fontRendererObj.FONT_HEIGHT;
        }
        if (visible.length == 0 && y + fontRendererObj.FONT_HEIGHT <= paneBottom)
            fontRendererObj.drawString(tr("craftonica.editor.serial.empty"), paneLeft + 6, y, MUTED);
    }

    private void drawDiagnostics() {
        int top = paneBottom + 4;
        drawRect(6, top, width - 6, height - 6, 0xff160f08);
        fontRendererObj.drawString(tr("craftonica.editor.diagnostics"), 10, top + 3, AMBER);
        String diagnostics = state.diagnostics;
        if (diagnostics.isEmpty() && !state.fault.isEmpty()) diagnostics = localizedCode(state.fault);
        if (diagnostics.isEmpty()) diagnostics = tr("craftonica.editor.diagnostics.empty");
        List<String> rows = wrap(displaySafe(diagnostics), width - 22);
        int y = top + 3 + fontRendererObj.FONT_HEIGHT;
        for (String row : rows) {
            if (y + fontRendererObj.FONT_HEIGHT > height - 7) break;
            fontRendererObj.drawString(row, 10, y, TEXT);
            y += fontRendererObj.FONT_HEIGHT;
        }
    }

    private void compile() {
        submitted = document.getUtf8();
        SketchNetwork.sendToServer(new EditorActionMessage(state.dimension, state.x, state.y, state.z,
                state.boardId, state.generation, editingRevision, SketchAction.COMPILE, submitted));
    }

    private void reload() {
        document.setAuthoritativeText(new String(state.sourceBytes(), StandardCharsets.UTF_8));
        editingRevision = state.revision;
        submitted = null;
        firstLine = horizontalColumn = 0;
        send(SketchAction.REFRESH, null);
    }

    private void send(SketchAction action, byte[] source) {
        SketchNetwork.sendToServer(new EditorActionMessage(state.dimension, state.x, state.y, state.z,
                state.boardId, state.generation, state.revision, action, source));
    }

    private void updateButtons() {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id == COMPILE) button.enabled = !"craftonica.editor.compiling".equals(state.compilationState);
            else if (button.id == START_STOP) {
                button.displayString = tr(isRunning() ? "craftonica.editor.button.stop" : "craftonica.editor.button.start");
                button.enabled = isRunning() || state.status != 0;
            } else if (button.id == EDITOR) button.enabled = serialTab;
            else if (button.id == SERIAL) button.enabled = !serialTab;
        }
    }

    private void layout() {
        paneLeft = 6;
        paneRight = width - 6;
        paneTop = width < 400 ? 75 : 54;
        int diagnosticHeight = height < 260 ? 38 : 48;
        paneBottom = Math.max(paneTop + 40, height - diagnosticHeight - 10);
        gutterRight = paneLeft + 34;
    }

    private int visibleRows() { return Math.max(1, (paneBottom - paneTop - 6) / fontRendererObj.FONT_HEIGHT); }

    private void ensureCaretVisible() {
        List<Line> values = lines();
        int line = lineAt(values, document.getCaret());
        if (line < firstLine) firstLine = line;
        if (line >= firstLine + visibleRows()) firstLine = line - visibleRows() + 1;
        Line current = values.get(line);
        int column = document.getText().codePointCount(current.start, document.getCaret());
        int approximateColumns = Math.max(4, (paneRight - gutterRight - 8) / 6);
        if (column < horizontalColumn) horizontalColumn = column;
        if (column >= horizontalColumn + approximateColumns) horizontalColumn = column - approximateColumns + 1;
    }

    private List<Line> lines() {
        String text = document.getText();
        if (text == indexedText && indexedLines != null) return indexedLines;
        List<Line> values = new ArrayList<Line>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                values.add(new Line(start, i));
                start = i + 1;
            }
        }
        values.add(new Line(start, text.length()));
        indexedText = text;
        indexedLines = values;
        return indexedLines;
    }

    private static int lineAt(List<Line> lines, int position) {
        for (int i = 0; i < lines.size(); i++) if (position <= lines.get(i).end) return i;
        return lines.size() - 1;
    }

    private List<String> wrap(String value, int maximumWidth) {
        List<String> output = new ArrayList<String>();
        String[] physical = value.split("\r?\n", -1);
        for (String line : physical) {
            if (line.isEmpty()) {
                output.add("");
                continue;
            }
            String rest = line;
            while (!rest.isEmpty()) {
                String part = fontRendererObj.trimStringToWidth(rest, Math.max(8, maximumWidth));
                if (part.isEmpty()) part = rest.substring(0, rest.offsetByCodePoints(0, 1));
                output.add(part);
                rest = rest.substring(part.length());
            }
        }
        return output;
    }

    private boolean isRunning() { return state.status == 2 || state.status == 3; }

    private static boolean isTerminalCompilation(String code) {
        return code.startsWith("craftonica.editor.compile_") && !"craftonica.editor.compiling".equals(code)
                || "craftonica.editor.verify_rejected".equals(code)
                || "craftonica.editor.compiler_unavailable".equals(code)
                || "craftonica.editor.invalid_source".equals(code)
                || "craftonica.editor.rate_limited".equals(code);
    }

    private static String statusName(int status) {
        String[] names = {"disabled", "stopped", "running", "suspended", "fault"};
        return status >= 0 && status < names.length ? names[status] : "fault";
    }

    private static String localizedCode(String code) {
        return code == null || code.isEmpty() ? "" : StatCollector.canTranslate(code)
                ? StatCollector.translateToLocal(code) : displaySafe(code);
    }

    private static String tr(String key) { return StatCollector.translateToLocal(key); }

    private static String displaySafe(String value) { return value == null ? "" : value.replace('\u00a7', '?'); }

    private static int charOffset(String value, int codePoints) {
        return value.offsetByCodePoints(0, Math.min(codePoints, value.codePointCount(0, value.length())));
    }

    private static final class Line {
        final int start, end;
        Line(int start, int end) { this.start = start; this.end = end; }
    }
}
