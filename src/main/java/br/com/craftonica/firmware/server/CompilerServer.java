package br.com.craftonica.firmware.server;

import br.com.craftonica.firmware.BoundedCompilationService;
import br.com.craftonica.firmware.CompilationHandle;
import br.com.craftonica.firmware.CompilationRequest;
import br.com.craftonica.firmware.FirmwareCache;
import br.com.craftonica.firmware.ProcessCompilerSupervisor;

import java.io.IOException;

/** Owns the single-worker compiler service and has no in-process compiler fallback. */
public final class CompilerServer {
    private static BoundedCompilationService service;
    private static boolean startAttempted;
    private static boolean stopping;
    private static String unavailableCode = "COMPILER_NOT_STARTED";

    private CompilerServer() {}

    public static synchronized void start() {
        if (service != null || startAttempted || stopping) return;
        startAttempted = true;
        try {
            CompilerEnvironment environment = CompilerEnvironment.discover();
            FirmwareCache cache = new FirmwareCache(environment.cache);
            service = new BoundedCompilationService(
                    new ProcessCompilerSupervisor(environment.script, environment.work, cache), 1);
            unavailableCode = "";
        } catch (IOException unavailable) {
            unavailableCode = "COMPILER_ARTIFACTS_MISSING";
        } catch (RuntimeException invalid) {
            unavailableCode = "COMPILER_ENVIRONMENT_INVALID";
        }
    }

    public static synchronized CompilationHandle submit(CompilationRequest request) {
        if (service == null && !stopping) start();
        return service == null ? null : service.submit(request);
    }

    public static synchronized String getUnavailableCode() { return unavailableCode; }

    public static synchronized void stop() {
        final BoundedCompilationService closing = service;
        service = null;
        if (closing == null) {
            startAttempted = false;
            unavailableCode = "COMPILER_NOT_STARTED";
            return;
        }
        stopping = true;
        unavailableCode = "COMPILER_NOT_STARTED";
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                closing.close();
                synchronized (CompilerServer.class) {
                    stopping = false;
                    startAttempted = false;
                }
            }
        }, "craftonica-compiler-stop");
        thread.setDaemon(true);
        thread.start();
    }
}
