package br.com.craftonica.firmware.server;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Discovers only administrator-owned repo or installed Prism compiler layouts. */
final class CompilerEnvironment {
    final Path script;
    final Path work;
    final Path cache;

    private CompilerEnvironment(Path script, Path work, Path cache) {
        this.script = script;
        this.work = work;
        this.cache = cache;
    }

    static CompilerEnvironment discover() throws IOException {
        Path script = explicit();
        if (script == null) {
            for (Path root : roots()) {
                Path direct = root.resolve("scripts/firmware/compile-sketch.sh");
                Path installed = root.resolve("craftonica-compiler/scripts/firmware/compile-sketch.sh");
                if (regular(direct)) { script = direct; break; }
                if (regular(installed)) { script = installed; break; }
            }
        }
        if (!regular(script)) throw new IOException("Compiler script is unavailable");
        script = script.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isExecutable(script)) throw new IOException("Compiler script is not executable");
        Path root = script.getParent().getParent().getParent();
        Path state = configuredStateRoot();
        if (state == null) state = root.resolve("state");
        createSafeDirectory(state);
        Path work = state.resolve("work");
        Path cache = state.resolve("cache");
        createSafeDirectory(work);
        createSafeDirectory(cache);
        return new CompilerEnvironment(script, work.toRealPath(LinkOption.NOFOLLOW_LINKS),
                cache.toRealPath(LinkOption.NOFOLLOW_LINKS));
    }

    private static Path explicit() {
        String value = System.getProperty("craftonica.compiler.script");
        if (value == null || value.trim().isEmpty()) value = System.getenv("CRAFTONICA_COMPILER_SCRIPT");
        return value == null || value.trim().isEmpty() ? null : new File(value.trim()).toPath();
    }

    private static Path configuredStateRoot() {
        String value = System.getProperty("craftonica.compiler.stateRoot");
        if (value == null || value.trim().isEmpty()) value = System.getenv("CRAFTONICA_COMPILER_STATE_ROOT");
        return value == null || value.trim().isEmpty() ? null : new File(value.trim()).toPath();
    }

    private static List<Path> roots() throws IOException {
        List<Path> roots = new ArrayList<Path>();
        addAncestors(roots, new File(System.getProperty("user.dir", ".")).toPath(), 7);
        try {
            URL location = CompilerEnvironment.class.getProtectionDomain().getCodeSource().getLocation();
            if ("file".equals(location.getProtocol())) {
                File source = new File(location.toURI());
                addAncestors(roots, (source.isDirectory() ? source : source.getParentFile()).toPath(), 9);
            }
        } catch (URISyntaxException invalid) {
            throw new IOException("Cannot resolve compiler code source", invalid);
        }
        return roots;
    }

    private static void addAncestors(List<Path> roots, Path start, int limit) {
        Path current = start.toAbsolutePath().normalize();
        for (int i = 0; current != null && i < limit; i++, current = current.getParent())
            if (!roots.contains(current)) roots.add(current);
    }

    private static boolean regular(Path path) {
        return path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void createSafeDirectory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(absolute) || !Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Unsafe compiler state directory");
            return;
        }
        Path parent = absolute.getParent();
        if (parent == null || Files.isSymbolicLink(parent)) throw new IOException("Unsafe compiler state parent");
        Files.createDirectories(absolute);
        if (Files.isSymbolicLink(absolute)) throw new IOException("Unsafe compiler state directory");
    }
}
