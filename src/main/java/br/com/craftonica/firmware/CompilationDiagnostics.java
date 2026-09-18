package br.com.craftonica.firmware;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class CompilationDiagnostics {
    public static final int MAX_ENTRIES = 100;
    public static final int MAX_UTF8_BYTES = 16 * 1024;
    public static final int MAX_ENTRY_UTF8_BYTES = 2 * 1024;

    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(?:(?:[A-Za-z]:[\\\\/])|/)[^\\s:]*");
    private static final String TRUNCATED = "[diagnostics truncated]";

    private final List<String> entries;

    private CompilationDiagnostics(List<String> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static CompilationDiagnostics empty() {
        return new CompilationDiagnostics(Collections.<String>emptyList());
    }

    public static CompilationDiagnostics sanitize(Collection<String> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) return empty();
        List<String> clean = new ArrayList<String>();
        int bytes = 0;
        boolean truncated = false;
        for (String raw : rawEntries) {
            if (clean.size() >= MAX_ENTRIES) {
                truncated = true;
                break;
            }
            String value = sanitizeEntry(raw == null ? "" : raw);
            value = truncateUtf8(value, MAX_ENTRY_UTF8_BYTES);
            int length = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > MAX_UTF8_BYTES) {
                truncated = true;
                break;
            }
            clean.add(value);
            bytes += length;
        }
        if (truncated) appendMarker(clean, bytes);
        return new CompilationDiagnostics(clean);
    }

    public List<String> getEntries() { return entries; }

    public int getUtf8Bytes() {
        int total = 0;
        for (String entry : entries) total += entry.getBytes(StandardCharsets.UTF_8).length;
        return total;
    }

    private static String sanitizeEntry(String raw) {
        StringBuilder value = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') value.append(' ');
            else if (c >= 0x20 && c != 0x7f) value.append(c);
            else value.append('?');
        }
        return ABSOLUTE_PATH.matcher(value.toString()).replaceAll("<path>");
    }

    private static String truncateUtf8(String value, int maximum) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maximum) return value;
        String suffix = "...";
        int limit = maximum - suffix.length();
        int end = 0;
        int bytes = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int count = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + count > limit) break;
            bytes += count;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end) + suffix;
    }

    private static void appendMarker(List<String> entries, int currentBytes) {
        int markerBytes = TRUNCATED.getBytes(StandardCharsets.UTF_8).length;
        while (!entries.isEmpty() && (entries.size() >= MAX_ENTRIES || currentBytes + markerBytes > MAX_UTF8_BYTES)) {
            String removed = entries.remove(entries.size() - 1);
            currentBytes -= removed.getBytes(StandardCharsets.UTF_8).length;
        }
        if (currentBytes + markerBytes <= MAX_UTF8_BYTES) entries.add(TRUNCATED);
    }
}
