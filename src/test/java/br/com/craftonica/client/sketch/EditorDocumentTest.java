package br.com.craftonica.client.sketch;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class EditorDocumentTest {
    @Test
    public void insertsEditsAndTracksDirtyState() {
        EditorDocument document = new EditorDocument(64);
        document.setAuthoritativeText("ab\ncd");
        assertFalse(document.isDirty());
        document.moveEnd(false);
        assertTrue(document.insert("!"));
        assertEquals("ab!\ncd", document.getText());
        assertTrue(document.isDirty());
        assertTrue(document.backspace());
        assertEquals("ab\ncd", document.getText());
        assertTrue(document.deleteForward());
        assertEquals("abcd", document.getText());
    }

    @Test
    public void selectionCopyCutPasteAndSelectAllAreBounded() {
        EditorDocument document = new EditorDocument(8);
        document.setAuthoritativeText("alpha");
        document.setCaret(1, false);
        document.setCaret(4, true);
        assertEquals("lph", document.copySelection());
        assertEquals("lph", document.cutSelection());
        assertEquals("aa", document.getText());
        assertTrue(document.insert("xy"));
        document.selectAll();
        assertFalse(document.insert("123456789"));
        assertEquals("axya", document.getText());
    }

    @Test
    public void navigationUsesLinesAndSelectionAnchor() {
        EditorDocument document = new EditorDocument(64);
        document.setAuthoritativeText("one\ntwelve\nxy");
        document.setCaret(7, false);
        document.moveDown(false);
        assertEquals(13, document.getCaret());
        document.moveHome(true);
        assertEquals("xy", document.copySelection());
        document.moveUp(false);
        assertEquals(4, document.getCaret());
        document.moveEnd(false);
        assertEquals(10, document.getCaret());
    }

    @Test
    public void tabNewlineUndoAndRedoRestoreCompleteState() {
        EditorDocument document = new EditorDocument(64);
        document.setAuthoritativeText("x");
        document.moveEnd(false);
        assertTrue(document.tab());
        assertTrue(document.newline());
        assertEquals("x  \n", document.getText());
        assertTrue(document.undo());
        assertEquals("x  ", document.getText());
        assertTrue(document.undo());
        assertEquals("x", document.getText());
        assertFalse(document.isDirty());
        assertTrue(document.redo());
        assertEquals("x  ", document.getText());
        assertTrue(document.isDirty());
    }

    @Test
    public void utf8LimitCountsBytesNotUtf16Units() {
        EditorDocument document = new EditorDocument(5);
        assertTrue(document.insert("é"));
        assertTrue(document.insert("€"));
        assertEquals(5, document.getUtf8Length());
        assertFalse(document.insert("a"));
        assertEquals("é€", new String(document.getUtf8(), StandardCharsets.UTF_8));
    }

    @Test
    public void codePointEditsNeverSplitSurrogatePairs() {
        EditorDocument document = new EditorDocument(16);
        document.setAuthoritativeText("A\ud83d\ude80B");
        document.setCaret(2, false);
        assertEquals(1, document.getCaret());
        document.moveRight(false);
        assertEquals(3, document.getCaret());
        assertTrue(document.backspace());
        assertEquals("AB", document.getText());
        assertTrue(document.undo());
        document.setCaret(1, false);
        assertTrue(document.deleteForward());
        assertEquals("AB", document.getText());
    }

    @Test
    public void malformedClipboardSurrogatesAndNulAreDiscarded() {
        EditorDocument document = new EditorDocument(16);
        assertTrue(document.insert("a\ud83d\u0000b\ude80c"));
        assertEquals("abc", document.getText());
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), document.getUtf8());
    }

    @Test
    public void restoredDraftRemainsDirty() {
        EditorDocument document = new EditorDocument(16);
        document.setAuthoritativeText("installed");
        document.setDraftText("draft");
        assertEquals("draft", document.getText());
        assertTrue(document.isDirty());
    }

    @Test
    public void cachedUtf8LengthTracksUndoRedoAndReplacement() {
        EditorDocument document = new EditorDocument(16);
        document.setAuthoritativeText("é");
        assertEquals(2, document.getUtf8Length());
        document.moveEnd(false);
        assertTrue(document.insert("€"));
        assertEquals(5, document.getUtf8Length());
        assertTrue(document.undo());
        assertEquals(2, document.getUtf8Length());
        assertTrue(document.redo());
        assertEquals(5, document.getUtf8Length());
        document.selectAll();
        assertTrue(document.insert("a"));
        assertEquals(1, document.getUtf8Length());
    }
}
