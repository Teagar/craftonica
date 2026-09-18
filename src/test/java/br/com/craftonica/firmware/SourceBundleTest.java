package br.com.craftonica.firmware;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class SourceBundleTest {
    @Test
    public void hashesExactBytesInUnsignedUtf8NameOrder() throws Exception {
        Map<String, byte[]> first = new LinkedHashMap<String, byte[]>();
        first.put("z.cpp", new byte[] { (byte) 0xff, 0 });
        first.put("main.ino", "void setup(){}".getBytes(StandardCharsets.UTF_8));
        first.put("a.h", new byte[] { 1, 2, 3 });
        SourceBundle bundle = new SourceBundle("main", first);

        Map<String, byte[]> second = new LinkedHashMap<String, byte[]>();
        second.put("a.h", new byte[] { 1, 2, 3 });
        second.put("main.ino", "void setup(){}".getBytes(StandardCharsets.UTF_8));
        second.put("z.cpp", new byte[] { (byte) 0xff, 0 });
        assertArrayEquals(bundle.getSourceHash(), new SourceBundle("main", second).getSourceHash());
        assertArrayEquals(independentHash(second), bundle.getSourceHash());
        assertEquals(Arrays.asList("a.h", "main.ino", "z.cpp"), bundle.getFileNames());
    }

    @Test
    public void makesDefensiveCopies() {
        byte[] source = { 1, 2 };
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        files.put("main.ino", source);
        SourceBundle bundle = new SourceBundle("main", files);
        source[0] = 9;
        byte[] returned = bundle.getFile("main.ino");
        returned[1] = 9;
        assertArrayEquals(new byte[] { 1, 2 }, bundle.getFile("main.ino"));
    }

    @Test
    public void rejectsTraversalAbsoluteUnicodeAndUnsupportedFiles() {
        reject("main", "../main.ino");
        reject("main", "/main.ino");
        reject("main", "folder\\main.ino");
        reject("main", "máin.ino");
        reject("main", "main.o");
        reject("../main", "main.ino");
    }

    @Test
    public void enforcesAllSizeAndCountLimits() {
        Map<String, byte[]> files = singleton("main.ino", new byte[SourceBundle.MAX_FILE_BYTES + 1]);
        expectInvalid("main", files);

        files = new LinkedHashMap<String, byte[]>();
        files.put("main.ino", new byte[SourceBundle.MAX_FILE_BYTES]);
        files.put("other.cpp", new byte[SourceBundle.MAX_FILE_BYTES]);
        files.put("extra.h", new byte[] { 1 });
        expectInvalid("main", files);

        files = new LinkedHashMap<String, byte[]>();
        files.put("main.ino", new byte[0]);
        for (int i = 0; i < 16; i++) files.put("f" + i + ".h", new byte[0]);
        expectInvalid("main", files);
    }

    private static void reject(String mainName, String path) {
        expectInvalid(mainName, singleton(path, new byte[0]));
    }

    private static void expectInvalid(String mainName, Map<String, byte[]> files) {
        try {
            new SourceBundle(mainName, files);
            fail("Expected invalid source bundle");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static Map<String, byte[]> singleton(String name, byte[] value) {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        result.put(name, value);
        return result;
    }

    private static byte[] independentHash(Map<String, byte[]> files) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String[] names = files.keySet().toArray(new String[files.size()]);
        Arrays.sort(names);
        for (String name : names) {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) nameBytes.length);
            digest.update((byte) (nameBytes.length >>> 8));
            digest.update(nameBytes);
            int length = files.get(name).length;
            digest.update((byte) length);
            digest.update((byte) (length >>> 8));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 24));
            digest.update(files.get(name));
        }
        return digest.digest();
    }
}
