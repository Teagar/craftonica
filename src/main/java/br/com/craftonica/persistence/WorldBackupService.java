package br.com.craftonica.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.file.LinkOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates one verified pre-migration snapshot before the world reaches its first server tick. */
public final class WorldBackupService {
    public static final int WORLD_WRITER_VERSION = 1;
    private static final int CONTROL_VERSION = 1;
    private static final int MANIFEST_VERSION = 1;
    private static final byte[] CONTROL_MAGIC = new byte[] {'C', 'R', 'L', 'M'};
    private static final byte[] MANIFEST_MAGIC = new byte[] {'C', 'R', 'L', 'B'};
    private static final String CONTROL_RELATIVE = "craftonica/migration-state.bin";
    private static final String MANIFEST_NAME = "manifest.bin";
    private static final String SNAPSHOT_NAME = "world";
    private static final int BUFFER_BYTES = 65536;
    private static final int MAX_FILES = 100000;
    private static final long RESERVED_FREE_BYTES = 64L * 1024L * 1024L;

    private WorldBackupService() {}

    public static Path prepare(Path worldDirectory) throws IOException {
        return prepare(worldDirectory, FILE_STORE_SPACE);
    }

    static Path prepare(Path worldDirectory, SpaceProbe space) throws IOException {
        if (space == null) throw new IOException("Storage probe is required");
        Path world = requireDirectory(worldDirectory);
        Path control = world.resolve(CONTROL_RELATIVE);
        Path parent = world.getParent();
        if (parent == null) throw new IOException("World directory has no parent");
        Path root = parent.resolve(".craftonica-backups").resolve(safeName(world.getFileName().toString()));
        Control existing = readControl(control);
        if (existing != null) {
            if (existing.writerVersion > WORLD_WRITER_VERSION)
                throw new IOException("World was written by a newer Craftonica version");
            if (existing.writerVersion == WORLD_WRITER_VERSION) {
                Path backup = safeResolve(root, existing.backupId);
                verifyManifestIdentity(backup, existing.manifestHash);
                verifyBackup(backup);
                return backup;
            }
        }

        createSecureDirectories(root);
        String id = System.currentTimeMillis() + "-writer-0-to-" + WORLD_WRITER_VERSION + "-" + UUID.randomUUID();
        Path partial = root.resolve(id + ".partial");
        Path complete = root.resolve(id);
        Files.createDirectory(partial);
        try {
            Path snapshot = partial.resolve(SNAPSHOT_NAME);
            Files.createDirectory(snapshot);
            List<Entry> entries = copyStableWorld(world, snapshot, space);
            Path manifest = partial.resolve(MANIFEST_NAME);
            writeManifest(manifest, entries);
            forceTreeDirectories(partial);
            verifyBackup(partial);
            moveAtomic(partial, complete);
            forceDirectory(root);
            byte[] manifestHash = sha256(complete.resolve(MANIFEST_NAME));
            writeControl(control, new Control(WORLD_WRITER_VERSION, complete.getFileName().toString(), manifestHash));
            return complete;
        } catch (IOException failure) {
            deleteTree(partial);
            throw failure;
        }
    }

    /** Restores a verified snapshot into a missing or empty save directory and publishes it atomically. */
    public static Path restore(Path backupDirectory, Path targetWorldDirectory) throws IOException {
        return restore(backupDirectory, targetWorldDirectory, FILE_STORE_SPACE, NO_RESTORE_HOOK);
    }

    static Path restore(Path backupDirectory, Path targetWorldDirectory, SpaceProbe space,
            RestoreHook hook) throws IOException {
        if (space == null || hook == null || targetWorldDirectory == null)
            throw new IOException("Restore arguments are required");
        Path backup = requireDirectory(backupDirectory);
        verifyBackup(backup);
        Path target = targetWorldDirectory.toAbsolutePath().normalize();
        if (target.startsWith(backup) || backup.startsWith(target))
            throw new IOException("Restore target must not overlap the backup");
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Restore target has no parent");
        parent = requireDirectory(parent);
        rejectSymlinkAncestors(target);
        boolean emptyTarget = false;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Restore target must be a missing or empty directory");
            DirectoryStream<Path> entries = Files.newDirectoryStream(target);
            try { emptyTarget = !entries.iterator().hasNext(); } finally { entries.close(); }
            if (!emptyTarget) throw new IOException("Restore target is not empty");
        }
        Path partial = parent.resolve(".craftonica-restore-" + safeName(target.getFileName().toString())
                + "-" + UUID.randomUUID() + ".partial");
        Files.createDirectory(partial);
        try {
            List<Entry> manifest = readManifest(backup.resolve(MANIFEST_NAME));
            long totalBytes = 0L;
            for (Entry entry : manifest) {
                if (Long.MAX_VALUE - totalBytes < entry.size) throw new IOException("Backup size overflow");
                totalBytes += entry.size;
            }
            requireSpace(space, partial, totalBytes);
            Path snapshot = backup.resolve(SNAPSHOT_NAME);
            for (Entry entry : manifest) {
                Path source = safeResolve(snapshot, entry.relative), destination = safeResolve(partial, entry.relative);
                Path destinationParent = destination.getParent();
                if (destinationParent != null) Files.createDirectories(destinationParent);
                byte[] copied = copyAndHash(source, destination);
                if (Files.size(destination) != entry.size || !Arrays.equals(copied, entry.hash))
                    throw new IOException("Restored entry failed verification: " + entry.relative);
            }
            if (!listSnapshotFiles(partial).equals(relativePaths(manifest)))
                throw new IOException("Restored tree differs from backup manifest");
            forceTreeDirectories(partial);
            hook.beforePublish(partial, target);
            if (emptyTarget) Files.delete(target);
            moveAtomic(partial, target);
            forceDirectory(parent);
            return target;
        } catch (IOException failure) {
            deleteTree(partial);
            if (emptyTarget && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(target);
            throw failure;
        }
    }

    public static void verifyBackup(Path backupDirectory) throws IOException {
        Path backup = requireDirectory(backupDirectory);
        List<Entry> entries = readManifest(backup.resolve(MANIFEST_NAME));
        Set<String> seen = new HashSet<String>();
        Path snapshot = backup.resolve(SNAPSHOT_NAME);
        requireDirectory(snapshot);
        for (Entry entry : entries) {
            if (!seen.add(entry.relative)) throw new IOException("Duplicate backup manifest path");
            Path file = safeResolve(snapshot, entry.relative);
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
                throw new IOException("Backup entry is not a regular file: " + entry.relative);
            rejectHardLink(file);
            if (Files.size(file) != entry.size || !Arrays.equals(sha256(file), entry.hash))
                throw new IOException("Backup entry failed verification: " + entry.relative);
        }
        if (!listSnapshotFiles(snapshot).equals(seen))
            throw new IOException("Backup tree differs from its manifest");
    }

    private static List<Entry> copyStableWorld(final Path world, Path destination, SpaceProbe space) throws IOException {
        final List<String> files = new ArrayList<String>();
        final long[] totalBytes = new long[1];
        Files.walkFileTree(world, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory) || !attributes.isDirectory())
                    throw new IOException("Unsupported directory in world: " + directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file) || !attributes.isRegularFile())
                    throw new IOException("Unsupported world entry: " + file);
                rejectHardLink(file);
                String relative = portable(world.relativize(file));
                if (!excluded(relative)) {
                    if (files.size() >= MAX_FILES) throw new IOException("World file count exceeds backup bound");
                    if (attributes.size() < 0 || Long.MAX_VALUE - totalBytes[0] < attributes.size())
                        throw new IOException("World size exceeds backup bound");
                    totalBytes[0] += attributes.size();
                    files.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        requireSpace(space, destination, totalBytes[0]);
        Collections.sort(files);
        List<Entry> entries = new ArrayList<Entry>(files.size());
        for (String relative : files) {
            Path source = safeResolve(world, relative);
            Path target = safeResolve(destination, relative);
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            byte[] copiedHash = copyAndHash(source, target);
            BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !sameFileKey(before, after) || !Arrays.equals(copiedHash, sha256NoFollow(source)))
                throw new IOException("World changed while backup was being created: " + relative);
            entries.add(new Entry(relative, after.size(), copiedHash));
        }
        for (Entry entry : entries) {
            Path source = safeResolve(world, entry.relative);
            BasicFileAttributes current = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!current.isRegularFile() || current.size() != entry.size
                    || !Arrays.equals(sha256NoFollow(source), entry.hash))
                throw new IOException("World changed before backup publication: " + entry.relative);
        }
        Set<String> expected = new HashSet<String>();
        for (Entry entry : entries) expected.add(entry.relative);
        if (!listSourceFiles(world).equals(expected))
            throw new IOException("World file set changed before backup publication");
        return entries;
    }

    private static void requireSpace(SpaceProbe probe, Path destination, long bytes) throws IOException {
        long usable = probe.usableSpace(destination);
        if (usable < 0L || usable < RESERVED_FREE_BYTES || bytes > usable - RESERVED_FREE_BYTES)
            throw new IOException("Insufficient free space for verified world backup or restore");
    }

    private static Set<String> relativePaths(List<Entry> entries) {
        Set<String> values = new HashSet<String>();
        for (Entry entry : entries) values.add(entry.relative);
        return values;
    }

    private static byte[] copyAndHash(Path source, Path target) throws IOException {
        rejectHardLink(source);
        MessageDigest digest = digest();
        FileChannel sourceChannel = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        InputStream input = new BufferedInputStream(Channels.newInputStream(sourceChannel));
        OutputStream output = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE));
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        } finally {
            try { output.close(); } finally { input.close(); }
        }
        force(target);
        rejectHardLink(source);
        return digest.digest();
    }

    private static void writeManifest(Path path, List<Entry> entries) throws IOException {
        DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)));
        try {
            output.write(MANIFEST_MAGIC);
            output.writeInt(MANIFEST_VERSION);
            output.writeInt(entries.size());
            for (Entry entry : entries) {
                byte[] relative = entry.relative.getBytes(StandardCharsets.UTF_8);
                if (relative.length == 0 || relative.length > 65535) throw new IOException("Backup path is too long");
                output.writeShort(relative.length);
                output.write(relative);
                output.writeLong(entry.size);
                output.write(entry.hash);
            }
        } finally {
            output.close();
        }
        force(path);
    }

    private static List<Entry> readManifest(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IOException("Backup manifest is missing");
        DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            requireMagic(input, MANIFEST_MAGIC);
            if (input.readInt() != MANIFEST_VERSION) throw new IOException("Unsupported backup manifest version");
            int count = input.readInt();
            if (count < 0 || count > MAX_FILES) throw new IOException("Invalid backup manifest count");
            List<Entry> entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                int length = input.readUnsignedShort();
                if (length == 0) throw new IOException("Empty backup path");
                byte[] encoded = new byte[length]; input.readFully(encoded);
                String relative = new String(encoded, StandardCharsets.UTF_8);
                long size = input.readLong();
                byte[] hash = new byte[32]; input.readFully(hash);
                if (size < 0) throw new IOException("Negative backup entry size");
                entries.add(new Entry(relative, size, hash));
            }
            if (input.read() != -1) throw new IOException("Trailing backup manifest data");
            return entries;
        } catch (EOFException truncated) {
            throw new IOException("Truncated backup manifest", truncated);
        } finally {
            input.close();
        }
    }

    private static Control readControl(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IOException("Invalid migration control file");
        DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            requireMagic(input, CONTROL_MAGIC);
            if (input.readInt() != CONTROL_VERSION) throw new IOException("Unsupported migration control version");
            int writer = input.readInt();
            int length = input.readUnsignedShort();
            if (length == 0) throw new IOException("Missing backup path");
            byte[] encoded = new byte[length]; input.readFully(encoded);
            byte[] hash = new byte[32]; input.readFully(hash);
            if (input.read() != -1) throw new IOException("Trailing migration control data");
            String backupId = new String(encoded, StandardCharsets.UTF_8);
            if (!backupId.matches("[A-Za-z0-9._-]+") || backupId.endsWith(".partial"))
                throw new IOException("Invalid backup identity");
            return new Control(writer, backupId, hash);
        } catch (EOFException truncated) {
            throw new IOException("Truncated migration control file", truncated);
        } finally {
            input.close();
        }
    }

    private static void writeControl(Path path, Control control) throws IOException {
        createSecureDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);
        byte[] encoded = control.backupId.getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > 65535) throw new IOException("Backup path is too long");
        DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)));
        try {
            output.write(CONTROL_MAGIC);
            output.writeInt(CONTROL_VERSION);
            output.writeInt(control.writerVersion);
            output.writeShort(encoded.length);
            output.write(encoded);
            output.write(control.manifestHash);
        } finally {
            output.close();
        }
        force(temporary);
        moveAtomic(temporary, path);
        forceDirectory(path.getParent());
    }

    private static void verifyManifestIdentity(Path backup, byte[] expectedHash) throws IOException {
        Path directory = requireDirectory(backup.toAbsolutePath().normalize());
        if (!Arrays.equals(sha256(directory.resolve(MANIFEST_NAME)), expectedHash))
            throw new IOException("Migration backup manifest changed");
    }

    private static Path requireDirectory(Path directory) throws IOException {
        if (directory == null) throw new IOException("World directory is required");
        Path normalized = directory.toAbsolutePath().normalize();
        rejectSymlinkAncestors(normalized);
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized))
            throw new IOException("Directory is unavailable or is a symbolic link: " + normalized);
        return normalized;
    }

    private static Set<String> listSnapshotFiles(final Path snapshot) throws IOException {
        final Set<String> files = new HashSet<String>();
        Files.walkFileTree(snapshot, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory) || !attributes.isDirectory())
                    throw new IOException("Unsupported backup directory: " + directory);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file) || !attributes.isRegularFile())
                    throw new IOException("Unsupported backup entry: " + file);
                rejectHardLink(file);
                String relative = portable(snapshot.relativize(file));
                if (!files.add(relative)) throw new IOException("Duplicate backup path");
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static Set<String> listSourceFiles(final Path world) throws IOException {
        final Set<String> files = new HashSet<String>();
        Files.walkFileTree(world, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory) || !attributes.isDirectory())
                    throw new IOException("Unsupported world directory: " + directory);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file) || !attributes.isRegularFile())
                    throw new IOException("Unsupported world entry: " + file);
                String relative = portable(world.relativize(file));
                if (!excluded(relative)) {
                    rejectHardLink(file);
                    if (!files.add(relative) || files.size() > MAX_FILES)
                        throw new IOException("Invalid world file set");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        Path resolved = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Backup path escaped its root");
        return resolved;
    }

    private static String portable(Path relative) {
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }

    private static boolean excluded(String relative) {
        return "session.lock".equals(relative) || CONTROL_RELATIVE.equals(relative)
                || ("craftonica/migration-state.bin.tmp").equals(relative);
    }

    private static String safeName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isEmpty() ? "world" : safe;
    }

    private static void requireMagic(DataInputStream input, byte[] expected) throws IOException {
        byte[] actual = new byte[expected.length]; input.readFully(actual);
        if (!Arrays.equals(actual, expected)) throw new IOException("Invalid persistent format magic");
    }

    private static byte[] sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        InputStream input = new BufferedInputStream(Files.newInputStream(path));
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally {
            input.close();
        }
        return digest.digest();
    }

    private static byte[] sha256NoFollow(Path path) throws IOException {
        MessageDigest digest = digest();
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        InputStream input = new BufferedInputStream(Channels.newInputStream(channel));
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        } finally {
            input.close();
        }
        return digest.digest();
    }

    private static boolean sameFileKey(BasicFileAttributes first, BasicFileAttributes second) {
        return first.fileKey() == null || second.fileKey() == null || first.fileKey().equals(second.fileKey());
    }

    private static void rejectHardLink(Path path) throws IOException {
        try {
            Object count = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            if (count instanceof Number && ((Number) count).longValue() > 1L)
                throw new IOException("Hard-linked world file is not supported: " + path);
        } catch (UnsupportedOperationException unavailable) {
            // File identity is still checked before and after copying on non-POSIX filesystems.
        }
    }

    private static void createSecureDirectories(Path path) throws IOException {
        Files.createDirectories(path);
        rejectSymlinkAncestors(path.toAbsolutePath().normalize());
        if (Files.isSymbolicLink(path)) throw new IOException("Backup directory cannot be a symbolic link");
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException unavailable) {
            // The fixed Linux target supports POSIX permissions; other filesystems keep native defaults.
        }
    }

    private static void rejectSymlinkAncestors(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current == null ? part : current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IOException("Symbolic link in persistent path: " + current);
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void force(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);
        try { channel.force(true); } finally { channel.close(); }
    }

    private static void forceDirectory(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try { channel.force(true); } finally { channel.close(); }
    }

    private static void forceTreeDirectories(final Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                forceDirectory(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("Atomic migration publication is not supported", unsupported);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.deleteIfExists(directory); return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class Entry {
        private final String relative;
        private final long size;
        private final byte[] hash;
        private Entry(String relative, long size, byte[] hash) {
            this.relative = relative; this.size = size; this.hash = hash.clone();
        }
    }

    private static final class Control {
        private final int writerVersion;
        private final String backupId;
        private final byte[] manifestHash;
        private Control(int writerVersion, String backupId, byte[] manifestHash) {
            this.writerVersion = writerVersion; this.backupId = backupId; this.manifestHash = manifestHash.clone();
        }
    }

    interface SpaceProbe { long usableSpace(Path path) throws IOException; }
    interface RestoreHook { void beforePublish(Path partial, Path target) throws IOException; }
    private static final SpaceProbe FILE_STORE_SPACE = new SpaceProbe() {
        @Override public long usableSpace(Path path) throws IOException { return Files.getFileStore(path).getUsableSpace(); }
    };
    private static final RestoreHook NO_RESTORE_HOOK = new RestoreHook() {
        @Override public void beforePublish(Path partial, Path target) { }
    };
}
