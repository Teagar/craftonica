package br.com.craftonica.client.sketch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** Pure bounded text model. Every stored string is valid Unicode and therefore strict UTF-8 encodable. */
public final class EditorDocument {
    private static final int HISTORY_LIMIT = 64;

    private final int maximumBytes;
    private final Deque<Snapshot> undo = new ArrayDeque<Snapshot>();
    private final Deque<Snapshot> redo = new ArrayDeque<Snapshot>();
    private String text = "";
    private int caret;
    private int anchor;
    private int preferredColumn = -1;
    private boolean dirty;

    public EditorDocument(int maximumBytes) {
        if (maximumBytes < 0) throw new IllegalArgumentException("Negative byte limit");
        this.maximumBytes = maximumBytes;
    }

    public String getText() { return text; }
    public int getCaret() { return caret; }
    public int getSelectionStart() { return Math.min(caret, anchor); }
    public int getSelectionEnd() { return Math.max(caret, anchor); }
    public boolean hasSelection() { return caret != anchor; }
    public boolean isDirty() { return dirty; }
    public int getUtf8Length() { return text.getBytes(StandardCharsets.UTF_8).length; }
    public byte[] getUtf8() { return text.getBytes(StandardCharsets.UTF_8); }

    public void setAuthoritativeText(String value) {
        String clean = sanitize(value);
        if (utf8Length(clean) > maximumBytes) throw new IllegalArgumentException("Text exceeds byte limit");
        text = clean;
        caret = anchor = 0;
        preferredColumn = -1;
        dirty = false;
        undo.clear();
        redo.clear();
    }

    public void setDraftText(String value) {
        setAuthoritativeText(value);
        dirty = true;
    }

    public boolean insert(String value) {
        String clean = sanitize(value);
        if (clean.isEmpty()) return false;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        String next = text.substring(0, start) + clean + text.substring(end);
        if (utf8Length(next) > maximumBytes) return false;
        remember();
        text = next;
        caret = anchor = start + clean.length();
        preferredColumn = -1;
        dirty = true;
        return true;
    }

    public boolean newline() { return insert("\n"); }
    public boolean tab() { return insert("  "); }

    public boolean backspace() {
        if (hasSelection()) return deleteSelection();
        if (caret == 0) return false;
        int start = previousBoundary(caret);
        return replaceRange(start, caret, "");
    }

    public boolean deleteForward() {
        if (hasSelection()) return deleteSelection();
        if (caret == text.length()) return false;
        return replaceRange(caret, nextBoundary(caret), "");
    }

    public String copySelection() {
        return text.substring(getSelectionStart(), getSelectionEnd());
    }

    public String cutSelection() {
        String selected = copySelection();
        if (!selected.isEmpty()) deleteSelection();
        return selected;
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
        preferredColumn = -1;
    }

    public void setCaret(int index, boolean selecting) {
        int safe = boundaryAtOrBefore(Math.max(0, Math.min(text.length(), index)));
        caret = safe;
        if (!selecting) anchor = safe;
        preferredColumn = -1;
    }

    public void moveLeft(boolean selecting) {
        if (!selecting && hasSelection()) moveTo(getSelectionStart(), false);
        else moveTo(previousBoundary(caret), selecting);
    }

    public void moveRight(boolean selecting) {
        if (!selecting && hasSelection()) moveTo(getSelectionEnd(), false);
        else moveTo(nextBoundary(caret), selecting);
    }

    public void moveHome(boolean selecting) { moveTo(lineStart(caret), selecting); }
    public void moveEnd(boolean selecting) { moveTo(lineEnd(caret), selecting); }
    public void moveUp(boolean selecting) { moveVertical(-1, selecting); }
    public void moveDown(boolean selecting) { moveVertical(1, selecting); }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    private boolean deleteSelection() {
        return replaceRange(getSelectionStart(), getSelectionEnd(), "");
    }

    private boolean replaceRange(int start, int end, String replacement) {
        remember();
        text = text.substring(0, start) + replacement + text.substring(end);
        caret = anchor = start + replacement.length();
        preferredColumn = -1;
        dirty = true;
        return true;
    }

    private void moveTo(int index, boolean selecting) {
        caret = Math.max(0, Math.min(text.length(), index));
        if (!selecting) anchor = caret;
        preferredColumn = -1;
    }

    private void moveVertical(int direction, boolean selecting) {
        int start = lineStart(caret);
        if (preferredColumn < 0) preferredColumn = text.codePointCount(start, caret);
        int targetStart;
        if (direction < 0) {
            if (start == 0) targetStart = 0;
            else targetStart = lineStart(start - 1);
        } else {
            int end = lineEnd(caret);
            if (end == text.length()) targetStart = end;
            else targetStart = end + 1;
        }
        int targetEnd = lineEnd(targetStart);
        int available = text.codePointCount(targetStart, targetEnd);
        caret = text.offsetByCodePoints(targetStart, Math.min(preferredColumn, available));
        if (!selecting) anchor = caret;
    }

    private int lineStart(int index) {
        int newline = text.lastIndexOf('\n', Math.max(0, index - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private int lineEnd(int index) {
        int newline = text.indexOf('\n', index);
        return newline < 0 ? text.length() : newline;
    }

    private int previousBoundary(int index) {
        return index <= 0 ? 0 : text.offsetByCodePoints(index, -1);
    }

    private int nextBoundary(int index) {
        return index >= text.length() ? text.length() : text.offsetByCodePoints(index, 1);
    }

    private int boundaryAtOrBefore(int index) {
        if (index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) return index - 1;
        return index;
    }

    private void remember() {
        undo.push(snapshot());
        while (undo.size() > HISTORY_LIMIT) undo.removeLast();
        redo.clear();
    }

    private Snapshot snapshot() { return new Snapshot(text, caret, anchor, dirty); }

    private void restore(Snapshot value) {
        text = value.text;
        caret = value.caret;
        anchor = value.anchor;
        dirty = value.dirty;
        preferredColumn = -1;
    }

    private static int utf8Length(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder clean = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    clean.append(c).append(value.charAt(i + 1));
                    i += 2;
                } else i++;
            } else if (Character.isLowSurrogate(c)) i++;
            else {
                if (c == '\r') {
                    clean.append('\n');
                    if (i + 1 < value.length() && value.charAt(i + 1) == '\n') i++;
                } else if (c != 0) clean.append(c);
                i++;
            }
        }
        return clean.toString();
    }

    private static final class Snapshot {
        final String text;
        final int caret, anchor;
        final boolean dirty;
        Snapshot(String text, int caret, int anchor, boolean dirty) {
            this.text = text; this.caret = caret; this.anchor = anchor; this.dirty = dirty;
        }
    }
}
