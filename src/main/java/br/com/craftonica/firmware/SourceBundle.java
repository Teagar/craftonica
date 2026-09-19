package br.com.craftonica.firmware;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SourceBundle {
    public static final int MAX_FILES = 16;
    public static final int MAX_TOTAL_BYTES = 64 * 1024;
    public static final int MAX_FILE_BYTES = 32 * 1024;

    private static final Comparator<String> UTF8_ORDER = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            byte[] a = left.getBytes(StandardCharsets.UTF_8);
            byte[] b = right.getBytes(StandardCharsets.UTF_8);
            int common = Math.min(a.length, b.length);
            for (int i = 0; i < common; i++) {
                int difference = (a[i] & 0xff) - (b[i] & 0xff);
                if (difference != 0) return difference;
            }
            return a.length - b.length;
        }
    };

    private final String mainName;
    private final Map<String, byte[]> files;
    private final int totalBytes;
    private final byte[] sourceHash;

    public SourceBundle(String mainName, Map<String, byte[]> files) {
        if (!isSafeMainName(mainName)) throw new IllegalArgumentException("Unsafe main sketch name");
        if (files == null || files.isEmpty() || files.size() > MAX_FILES)
            throw new IllegalArgumentException("A source bundle must contain between 1 and 16 files");

        TreeMap<String, byte[]> copy = new TreeMap<String, byte[]>(UTF8_ORDER);
        long total = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            byte[] content = entry.getValue();
            if (!isSafePath(path) || !hasAllowedExtension(path))
                throw new IllegalArgumentException("Unsafe or unsupported source path: " + path);
            if (content == null || content.length > MAX_FILE_BYTES)
                throw new IllegalArgumentException("Source file exceeds 32 KiB: " + path);
            if (copy.put(path, content.clone()) != null)
                throw new IllegalArgumentException("Duplicate source path: " + path);
            total += content.length;
            if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Source bundle exceeds 64 KiB");
        }
        String mainPath = mainName + ".ino";
        if (!copy.containsKey(mainPath)) throw new IllegalArgumentException("Missing main sketch: " + mainPath);

        this.mainName = mainName;
        this.files = Collections.unmodifiableMap(copy);
        this.totalBytes = (int) total;
        this.sourceHash = hash(copy);
    }

    public String getMainName() { return mainName; }
    public String getMainPath() { return mainName + ".ino"; }
    public int getTotalBytes() { return totalBytes; }
    public int size() { return files.size(); }
    public List<String> getFileNames() { return Collections.unmodifiableList(new ArrayList<String>(files.keySet())); }

    public byte[] getFile(String path) {
        byte[] content = files.get(path);
        return content == null ? null : content.clone();
    }

    public byte[] getSourceHash() { return sourceHash.clone(); }
    public String getSourceHashHex() { return FirmwareHashes.hex(sourceHash); }

    private static byte[] hash(Map<String, byte[]> files) {
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] content = entry.getValue();
            writeU16(canonical, name.length);
            canonical.write(name, 0, name.length);
            writeU32(canonical, content.length);
            canonical.write(content, 0, content.length);
        }
        return FirmwareHashes.sha256(canonical.toByteArray());
    }

    private static void writeU16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static boolean isSafeMainName(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isAsciiLetterOrDigit(c) && c != '_' && c != '-') return false;
        }
        return isAsciiLetterOrDigit(name.charAt(0));
    }

    private static boolean isSafePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 64 || path.charAt(0) == '/'
                || path.indexOf('\\') >= 0 || path.indexOf('/') >= 0)
            return false;
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 64 || part.equals(".") || part.equals("..") || !isAsciiLetterOrDigit(part.charAt(0))) return false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!isAsciiLetterOrDigit(c) && c != '.' && c != '_' && c != '-') return false;
            }
        }
        return true;
    }

    private static boolean hasAllowedExtension(String path) {
        return path.endsWith(".ino") || path.endsWith(".h") || path.endsWith(".c") || path.endsWith(".cpp");
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
    }
}
