package br.com.craftonica.firmware;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class CompilationKey {
    private static final byte[] DOMAIN = "craftonica-compilation-key-1\0".getBytes(StandardCharsets.US_ASCII);

    private final String hex;

    private CompilationKey(String hex) { this.hex = hex; }

    public static CompilationKey from(SourceBundle sources) {
        if (sources == null) throw new IllegalArgumentException("Sources are required");
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        write(value, DOMAIN);
        write(value, ToolchainProfile.profileHash());
        write(value, FirmwareHashes.fromHex(ToolchainProfile.TOOLCHAIN_TREE_HASH_HEX));
        write(value, FirmwareHashes.fromHex(ToolchainProfile.CORE_TREE_HASH_HEX));
        write(value, FirmwareHashes.fromHex(CompilerManifest.HASH_HEX));
        byte[] main = sources.getMainPath().getBytes(StandardCharsets.UTF_8);
        value.write(main.length & 0xff);
        value.write((main.length >>> 8) & 0xff);
        write(value, main);
        write(value, sources.getSourceHash());
        return new CompilationKey(FirmwareHashes.hex(FirmwareHashes.sha256(value.toByteArray())));
    }

    public String asHex() { return hex; }

    @Override public String toString() { return hex; }
    @Override public int hashCode() { return hex.hashCode(); }
    @Override public boolean equals(Object other) {
        return other instanceof CompilationKey && hex.equals(((CompilationKey) other).hex);
    }

    private static void write(ByteArrayOutputStream output, byte[] bytes) {
        output.write(bytes, 0, bytes.length);
    }
}
