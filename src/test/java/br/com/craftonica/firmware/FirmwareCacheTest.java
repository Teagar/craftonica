package br.com.craftonica.firmware;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FirmwareCacheTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void storesByVerifiedContentHashAndNeverMutatesAnEntry() throws Exception {
        Path root = temporary.newFolder("cache").toPath();
        FirmwareCache cache = new FirmwareCache(root);
        CRLFirmware firmware = firmware();
        String key = cache.put(firmware);
        assertEquals(firmware.getFirmwareHashHex(), key);
        assertArrayEquals(firmware.getBytes(), cache.get(key).get().getBytes());
        assertEquals(key, cache.put(firmware));

        Path entry = root.resolve(key + ".crlf");
        byte[] corrupt = firmware.getBytes();
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(entry, corrupt);
        try {
            cache.put(firmware);
            fail("Expected immutable collision rejection");
        } catch (IOException expected) {
        }
    }

    @Test
    public void rejectsSymlinkRootAndSymlinkEntry() throws Exception {
        Path real = temporary.newFolder("real").toPath();
        Path rootLink = temporary.getRoot().toPath().resolve("root-link");
        try {
            Files.createSymbolicLink(rootLink, real);
        } catch (UnsupportedOperationException e) {
            return;
        }
        try {
            new FirmwareCache(rootLink);
            fail("Expected symlink root rejection");
        } catch (IOException expected) {
        }

        FirmwareCache cache = new FirmwareCache(real);
        CRLFirmware firmware = firmware();
        Path outside = temporary.newFile("outside").toPath();
        Path entry = real.resolve(firmware.getFirmwareHashHex() + ".crlf");
        Files.createSymbolicLink(entry, outside);
        try {
            cache.get(firmware.getFirmwareHashHex());
            fail("Expected symlink entry rejection");
        } catch (IOException expected) {
        }
    }

    @Test
    public void missingAndMalformedKeysAreExplicit() throws Exception {
        FirmwareCache cache = new FirmwareCache(temporary.newFolder("empty").toPath());
        assertFalse(cache.get(new byte[32]).isPresent());
        try {
            cache.get("../escape");
            fail("Expected malformed key rejection");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void mapsCompilationInputsToVerifiedFirmwareWithoutConflatingMainTabs() throws Exception {
        Path root = temporary.newFolder("compiled-cache").toPath();
        FirmwareCache cache = new FirmwareCache(root);
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<String, byte[]>();
        files.put("A.ino", "void setup(){}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put("B.ino", "void loop(){}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SourceBundle a = new SourceBundle("A", files);
        SourceBundle b = new SourceBundle("B", files);
        assertFalse(CompilationKey.from(a).equals(CompilationKey.from(b)));
        CRLFirmware firmware = CRLFirmware.create(a, new byte[] { 1, 2, 3, 4 });
        cache.put(CompilationKey.from(a), firmware);
        assertArrayEquals(firmware.getBytes(), cache.get(CompilationKey.from(a)).get().getBytes());
        assertFalse(cache.get(CompilationKey.from(b)).isPresent());
    }

    private static CRLFirmware firmware() {
        return CRLFirmware.create(new byte[32], new byte[] { 1, 2, 3, 4 });
    }
}
