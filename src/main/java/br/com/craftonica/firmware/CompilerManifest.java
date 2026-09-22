package br.com.craftonica.firmware;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class CompilerManifest {
    static final String HASH_HEX = "56eb8ebcb1b1592dbe00261ec0ee09463310ccf16062b0b7b982108fbcd88965";
    private static final String MANIFEST_PATH = "firmware/compiler-manifest-v1.txt";

    private CompilerManifest() {
    }

    static void verify(Path repositoryRoot) throws IOException {
        Path manifest = safeResolve(repositoryRoot, MANIFEST_PATH);
        byte[] manifestBytes = readRegular(manifest, 64 * 1024);
        if (!FirmwareHashes.hex(FirmwareHashes.sha256(manifestBytes)).equals(HASH_HEX))
            throw new IOException("Compiler manifest digest mismatch");
        String[] lines = new String(manifestBytes, StandardCharsets.US_ASCII).split("\\n");
        for (String line : lines) {
            if (line.isEmpty() || line.indexOf('=') >= 0) continue;
            int separator = line.indexOf(' ');
            if (separator != 64) throw new IOException("Malformed compiler manifest entry");
            String expected = line.substring(0, separator);
            String relative = line.substring(separator + 1);
            Path file = relative.startsWith("host:")
                    ? java.nio.file.Paths.get(relative.substring(5)) : safeResolve(repositoryRoot, relative);
            if (Files.isSymbolicLink(file)) throw new IOException("Compiler component must not be a symlink");
            byte[] bytes = readRegular(file, 8 * 1024 * 1024);
            if (!FirmwareHashes.hex(FirmwareHashes.sha256(bytes)).equals(expected))
                throw new IOException("Compiler component digest mismatch: " + relative);
        }
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path)) throw new IOException("Unsafe compiler manifest path");
        return path;
    }

    private static byte[] readRegular(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing compiler component");
        long size = Files.size(path);
        if (size < 1 || size > maximum) throw new IOException("Compiler component size is invalid");
        return Files.readAllBytes(path);
    }
}
