package br.com.craftonica.firmware;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Invokes the fail-closed Linux supervisor and independently verifies its ELF output. */
public final class ProcessCompilerSupervisor implements CompilerSupervisor {
    private static final int MAX_CAPTURED_DIAGNOSTICS = CompilationDiagnostics.MAX_UTF8_BYTES;

    private final Path compilerScript;
    private final Path workRoot;
    private final AvrElfVerifier verifier;
    private final FirmwareCache cache;

    public ProcessCompilerSupervisor(Path compilerScript, Path workRoot) throws IOException {
        this(compilerScript, workRoot, null);
    }

    public ProcessCompilerSupervisor(Path compilerScript, Path workRoot, FirmwareCache cache) throws IOException {
        if (compilerScript == null || workRoot == null) throw new IllegalArgumentException("Compiler paths are required");
        this.compilerScript = compilerScript.toAbsolutePath().normalize();
        this.workRoot = workRoot.toAbsolutePath().normalize();
        this.verifier = new AvrElfVerifier();
        this.cache = cache;
        requireRegularFile(this.compilerScript, "compiler script");
        Path repositoryRoot = this.compilerScript.getParent().getParent().getParent();
        CompilerManifest.verify(repositoryRoot);
        if (Files.isSymbolicLink(this.workRoot) || !Files.isDirectory(this.workRoot, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Compiler work root must be an existing non-symlink directory");
    }

    @Override
    public CompilationResult compile(CompilationRequest request) throws Exception {
        CompilationKey compilationKey = request.getCompilationKey();
        if (cache != null) {
            java.util.Optional<CRLFirmware> cached = cache.get(compilationKey);
            if (cached.isPresent()) return CompilationResult.success(cached.get(),
                    Collections.singletonList("Verified firmware cache hit"));
        }
        Path job = Files.createTempDirectory(workRoot, "job-");
        Path source = Files.createDirectory(job.resolve("source"));
        Path output = Files.createDirectory(job.resolve("output"));
        try {
            writeSources(source, request.getSources());
            ProcessBuilder builder = new ProcessBuilder(compilerScript.toString(), source.toString(),
                    request.getSources().getMainPath(), output.toString());
            builder.redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().put("HOME", System.getProperty("user.home", ""));
            builder.environment().put("PATH", "/usr/bin:/bin");
            String unit = "craftonica-compile-" + java.util.UUID.randomUUID().toString().replace("-", "") + ".service";
            builder.environment().put("CRAFTONICA_SYSTEMD_UNIT", unit);
            copyEnvironment(builder, "XDG_RUNTIME_DIR");
            copyEnvironment(builder, "DBUS_SESSION_BUS_ADDRESS");
            Process process = builder.start();
            DiagnosticReader reader = new DiagnosticReader(process.getInputStream());
            Thread drain = new Thread(reader, "craftonica-compiler-diagnostics");
            drain.setDaemon(true);
            drain.start();
            try {
                if (!process.waitFor(14, TimeUnit.SECONDS)) {
                    terminate(process, unit, builder.environment());
                    drain.join(1000);
                    return CompilationResult.failure(CompilationResult.Status.COMPILER_UNAVAILABLE,
                            "Compiler supervisor exceeded its wall-time boundary");
                }
            } catch (InterruptedException cancelled) {
                terminate(process, unit, builder.environment());
                drain.join(1000);
                Thread.currentThread().interrupt();
                throw cancelled;
            }
            drain.join(1000);
            List<String> diagnostics = reader.lines();
            if (process.exitValue() != 0) {
                CompilationResult.Status status;
                if (process.exitValue() == 65) status = CompilationResult.Status.REJECTED;
                else if (process.exitValue() == 66) status = CompilationResult.Status.COMPILE_ERROR;
                else if (process.exitValue() == 67) status = CompilationResult.Status.COMPILE_LIMIT;
                else status = CompilationResult.Status.COMPILER_UNAVAILABLE;
                return CompilationResult.failure(status, diagnostics);
            }
            Path elf = output.resolve("firmware.elf");
            requireRegularFile(elf, "compiler ELF output");
            byte[] bytes = readBounded(elf, AvrElfVerifier.MAX_ELF_BYTES);
            CRLFirmware firmware;
            try {
                firmware = verifier.verify(bytes, request.getSources().getSourceHash());
            } catch (IllegalArgumentException rejected) {
                return CompilationResult.failure(CompilationResult.Status.VERIFY_REJECTED,
                        Collections.singletonList(rejected.getMessage()));
            }
            if (cache != null) cache.put(compilationKey, firmware);
            return CompilationResult.success(firmware, diagnostics);
        } finally {
            deleteTree(job);
        }
    }

    private static void writeSources(Path root, SourceBundle bundle) throws IOException {
        for (String name : bundle.getFileNames()) {
            Path destination = root.resolve(name).normalize();
            if (!destination.startsWith(root)) throw new IOException("Source path escaped job root");
            Path parent = destination.getParent();
            if (!Files.exists(parent)) Files.createDirectories(parent);
            Files.write(destination, bundle.getFile(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    private static void copyEnvironment(ProcessBuilder builder, String name) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) builder.environment().put(name, value);
    }

    private static void terminate(Process process, String unit, java.util.Map<String, String> environment)
            throws InterruptedException, IOException {
        process.destroy();
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        int absentChecks = 0;
        boolean observedUnit = false;
        while (System.nanoTime() < deadline) {
            ControlResult state = runSystemctl(environment, "show", "--property=ActiveState", "--value", unit);
            if (state.exitCode != 0) {
                absentChecks = 0;
                Thread.sleep(100);
                continue;
            }
            ControlResult details = runSystemctl(environment, "show", "--property=LoadState",
                    "--property=ActiveState", "--property=MainPID", "--property=ControlGroup", unit);
            if (details.exitCode != 0) {
                absentChecks = 0;
                Thread.sleep(100);
                continue;
            }
            UnitState unitState = UnitState.parse(details.output);
            if (!unitState.loadState.equals("not-found")) {
                observedUnit = true;
                runSystemctl(environment, "kill", "--kill-whom=all", "--signal=KILL", unit);
                runSystemctl(environment, "stop", unit);
            }
            boolean stopped = unitState.loadState.equals("not-found")
                    || ((unitState.activeState.equals("inactive") || unitState.activeState.equals("failed"))
                    && unitState.mainPid == 0 && cgroupIsEmpty(unitState.controlGroup));
            if (stopped) {
                absentChecks++;
                if (absentChecks >= (observedUnit ? 3 : 10)) return;
            } else {
                absentChecks = 0;
            }
            Thread.sleep(100);
        }
        throw new IOException("Could not prove compiler cgroup termination");
    }

    private static boolean cgroupIsEmpty(String controlGroup) {
        if (controlGroup == null || controlGroup.isEmpty()) return true;
        Path processes = java.nio.file.Paths.get("/sys/fs/cgroup" + controlGroup + "/cgroup.procs");
        try {
            return !Files.exists(processes) || Files.readAllLines(processes, java.nio.charset.StandardCharsets.US_ASCII).isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static ControlResult runSystemctl(java.util.Map<String, String> environment, String... arguments)
            throws InterruptedException, IOException {
        List<String> command = new ArrayList<String>();
        command.add("/usr/bin/systemctl");
        command.add("--user");
        Collections.addAll(command, arguments);
        ProcessBuilder control = new ProcessBuilder(command);
        control.environment().clear();
        control.environment().putAll(environment);
        control.redirectErrorStream(true);
        Process process = control.start();
        DiagnosticReader drain = new DiagnosticReader(process.getInputStream());
        Thread reader = new Thread(drain, "craftonica-systemctl-drain");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(1, TimeUnit.SECONDS);
            throw new IOException("systemctl timed out while terminating compiler cgroup");
        }
        reader.join(500);
        return new ControlResult(process.exitValue(), drain.text());
    }

    private static final class ControlResult {
        private final int exitCode;
        private final String output;
        private ControlResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
    }

    private static final class UnitState {
        private String loadState = "";
        private String activeState = "";
        private String controlGroup = "";
        private long mainPid = -1;

        private static UnitState parse(String output) {
            UnitState result = new UnitState();
            for (String line : output.split("\\r?\\n")) {
                int separator = line.indexOf('=');
                if (separator < 0) continue;
                String name = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (name.equals("LoadState")) result.loadState = value;
                else if (name.equals("ActiveState")) result.activeState = value;
                else if (name.equals("ControlGroup")) result.controlGroup = value;
                else if (name.equals("MainPID")) {
                    try { result.mainPid = Long.parseLong(value); }
                    catch (NumberFormatException ignored) { result.mainPid = -1; }
                }
            }
            return result;
        }
    }

    private static byte[] readBounded(Path path, int maximum) throws IOException {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 1 || size > maximum) throw new IOException("Compiler output exceeds its bound");
            byte[] result = new byte[(int) size];
            java.nio.ByteBuffer target = java.nio.ByteBuffer.wrap(result);
            while (target.hasRemaining()) {
                if (channel.read(target) < 0) throw new IOException("Compiler output ended early");
            }
            return result;
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing or unsafe " + label);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> paths = new ArrayList<Path>();
        java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root);
        try {
            for (Path path : stream) paths.add(path);
        } finally {
            stream.close();
        }
        for (Path path : paths) {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) deleteTree(path);
            else Files.deleteIfExists(path);
        }
        Files.deleteIfExists(root);
    }

    private static final class DiagnosticReader implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private DiagnosticReader(InputStream input) { this.input = input; }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    int accepted = Math.min(count, MAX_CAPTURED_DIAGNOSTICS - captured.size());
                    if (accepted > 0) captured.write(buffer, 0, accepted);
                }
            } catch (IOException ignored) {
            }
        }

        private List<String> lines() {
            String value = text();
            String[] split = value.split("\\r?\\n");
            List<String> result = new ArrayList<String>();
            Collections.addAll(result, split);
            return result;
        }

        private String text() {
            return new String(captured.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
