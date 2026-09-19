package br.com.craftonica.runtime.server;

import java.io.IOException;

/** Owns the process supervisor for the lifetime of a loaded server world. */
public final class RuntimeServer {
    private static final long WORKER_TIMEOUT_MILLIS = 3000L;
    private static RuntimeSupervisor supervisor;
    private static String unavailableReason = "RUNTIME_NOT_STARTED";
    private static boolean startAttempted;

    private RuntimeServer() {}

    public static synchronized void start() {
        if (supervisor != null || startAttempted) return;
        startAttempted = true;
        try {
            RuntimeEnvironment environment = RuntimeEnvironment.discover();
            supervisor = new RuntimeSupervisor(environment.launcher, environment.javaHome,
                    environment.workerJar, WORKER_TIMEOUT_MILLIS);
            unavailableReason = "";
        } catch (IOException unavailable) {
            unavailableReason = "RUNTIME_ARTIFACTS_MISSING";
        } catch (RuntimeException unavailable) {
            unavailableReason = "RUNTIME_ENVIRONMENT_INVALID";
        }
    }

    public static synchronized RuntimeSupervisor get() { return supervisor; }

    public static synchronized String getUnavailableReason() { return unavailableReason; }

    public static synchronized void stop() {
        if (supervisor != null) supervisor.close();
        supervisor = null;
        startAttempted = false;
        unavailableReason = "RUNTIME_NOT_STARTED";
    }
}
