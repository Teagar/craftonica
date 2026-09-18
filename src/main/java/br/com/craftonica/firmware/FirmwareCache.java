package br.com.craftonica.firmware;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class FirmwareCache {
    private static final int MAX_FIRMWARE_BYTES = CRLFirmware.HEADER_LENGTH
            + CRLFirmware.SEGMENT_ENTRY_LENGTH + ToolchainProfile.MAX_FLASH_BYTES;

    private final Path root;

    public FirmwareCache(Path root) throws IOException {
        if (root == null) throw new IllegalArgumentException("Cache root is required");
        this.root = root.toAbsolutePath().normalize();
        checkRoot();
    }

    public String put(CRLFirmware firmware) throws IOException {
        if (firmware == null) throw new IllegalArgumentException("Firmware is required");
        checkRoot();
        String key = firmware.getFirmwareHashHex();
        Path destination = entryPath(key);
        byte[] bytes = firmware.getBytes();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(destination, bytes);
            return key;
        }

        Path temporary = root.resolve("." + key + "." + UUID.randomUUID().toString() + ".tmp");
        try {
            OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE };
            try (FileChannel channel = FileChannel.open(temporary, options)) {
                ByteBuffer pending = ByteBuffer.wrap(bytes);
                while (pending.hasRemaining()) channel.write(pending);
                channel.force(true);
            }
            try {
                // A same-filesystem hard link publishes the complete inode atomically and never replaces an entry.
                Files.createLink(destination, temporary);
            } catch (FileAlreadyExistsException e) {
                verifyExisting(destination, bytes);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return key;
    }

    public String put(byte[] encoded) throws IOException {
        return put(CRLFirmware.decode(encoded));
    }

    public String put(CompilationKey compilationKey, CRLFirmware firmware) throws IOException {
        if (compilationKey == null) throw new IllegalArgumentException("Compilation key is required");
        String firmwareKey = put(firmware);
        publishImmutable(indexPath(compilationKey), firmwareKey.getBytes(StandardCharsets.US_ASCII));
        return firmwareKey;
    }

    public Optional<CRLFirmware> get(CompilationKey compilationKey) throws IOException {
        if (compilationKey == null) throw new IllegalArgumentException("Compilation key is required");
        checkRoot();
        Path index = indexPath(compilationKey);
        if (!Files.exists(index, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (Files.isSymbolicLink(index)) throw new IOException("Cache index must not be a symlink");
        byte[] indexBytes;
        try {
            indexBytes = readNoFollow(index, 64, 64);
        } catch (IOException invalid) {
            return Optional.empty();
        }
        String firmwareKey = new String(indexBytes, StandardCharsets.US_ASCII);
        if (!isHashKey(firmwareKey)) {
            return Optional.empty();
        }
        Optional<CRLFirmware> firmware = get(firmwareKey);
        return firmware;
    }

    public Optional<CRLFirmware> get(byte[] firmwareHash) throws IOException {
        if (firmwareHash == null || firmwareHash.length != 32) throw new IllegalArgumentException("Invalid firmware hash");
        return get(FirmwareHashes.hex(firmwareHash));
    }

    public Optional<CRLFirmware> get(String key) throws IOException {
        if (!isHashKey(key)) throw new IllegalArgumentException("Invalid cache key");
        checkRoot();
        Path entry = entryPath(key);
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (Files.isSymbolicLink(entry)) throw new IOException("Cache entry must not be a symlink");
        byte[] encoded;
        try {
            encoded = readNoFollow(entry, CRLFirmware.HEADER_LENGTH + CRLFirmware.SEGMENT_ENTRY_LENGTH,
                    MAX_FIRMWARE_BYTES);
        } catch (IOException invalid) {
            return Optional.empty();
        }
        CRLFirmware firmware;
        try {
            firmware = CRLFirmware.decode(encoded);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!firmware.getFirmwareHashHex().equals(key)) {
            return Optional.empty();
        }
        return Optional.of(firmware);
    }

    private void checkRoot() throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Cache root must be an existing non-symlink directory");
    }

    private Path entryPath(String key) throws IOException {
        Path result = root.resolve(key + ".crlf").normalize();
        if (!root.equals(result.getParent())) throw new IOException("Cache path escaped its root");
        return result;
    }

    private Path indexPath(CompilationKey key) throws IOException {
        Path result = root.resolve(key.asHex() + ".idx").normalize();
        if (!root.equals(result.getParent())) throw new IOException("Cache index escaped its root");
        return result;
    }

    private void publishImmutable(Path destination, byte[] bytes) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(destination, bytes);
            return;
        }
        Path temporary = root.resolve("." + destination.getFileName() + "." + UUID.randomUUID().toString() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer pending = ByteBuffer.wrap(bytes);
                while (pending.hasRemaining()) channel.write(pending);
                channel.force(true);
            }
            try {
                Files.createLink(destination, temporary);
            } catch (FileAlreadyExistsException e) {
                verifyExisting(destination, bytes);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void verifyExisting(Path destination, byte[] expected) throws IOException {
        byte[] actual = readNoFollow(destination, expected.length, expected.length);
        if (!Arrays.equals(actual, expected)) throw new IOException("Immutable cache entry collision");
    }

    private static byte[] readNoFollow(Path path, int minimum, int maximum) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < minimum || size > maximum) throw new IOException("Invalid cache entry length");
            byte[] bytes = new byte[(int) size];
            ByteBuffer target = ByteBuffer.wrap(bytes);
            while (target.hasRemaining()) {
                if (channel.read(target) < 0) throw new IOException("Cache entry ended early");
            }
            if (channel.read(ByteBuffer.allocate(1)) >= 0) throw new IOException("Cache entry grew while reading");
            return bytes;
        }
    }

    private static boolean isHashKey(String key) {
        if (key == null || key.length() != 64) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }
}
