package br.com.craftonica.runtime.server;

import br.com.craftonica.runtime.protocol.RuntimeProtocol;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Server-facing bounded scheduler. Public values deliberately contain no Forge world objects. */
public final class RuntimeSupervisor implements Closeable {
    public static final int MAX_WORKERS = 4;
    public static final int MAX_QUEUED = 64;
    public static final int MAX_STDERR_BYTES = 8192;

    private final File launcher, javaHome, workerJar;
    private final long timeoutMillis;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService timer;
    private final BlockingQueue<WorkerProcess> idleWorkers = new ArrayBlockingQueue<WorkerProcess>(MAX_WORKERS);
    private final AtomicInteger workerCount = new AtomicInteger();
    private final Map<WorkerProcess, Boolean> allWorkers = new ConcurrentHashMap<WorkerProcess, Boolean>();
    private final Map<String, Submission> inFlight = new ConcurrentHashMap<String, Submission>();
    private volatile boolean closed;

    public RuntimeSupervisor(File launcher, File javaHome, File workerJar, long timeoutMillis) {
        if (launcher == null || javaHome == null || workerJar == null || timeoutMillis <= 0) throw new IllegalArgumentException();
        this.launcher = launcher; this.javaHome = javaHome; this.workerJar = workerJar; this.timeoutMillis = timeoutMillis;
        this.executor = new ThreadPoolExecutor(MAX_WORKERS, MAX_WORKERS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(MAX_QUEUED), named("craftonica-runtime"), new ThreadPoolExecutor.AbortPolicy());
        this.timer = new ScheduledThreadPoolExecutor(1, named("craftonica-runtime-timeout"));
    }

    public Submission submit(final RuntimeProtocol.Request request) {
        return submit(request, request.absoluteTarget);
    }

    public Submission submit(final RuntimeProtocol.Request request, final long batchTarget) {
        if (closed) throw new RejectedExecutionException("runtime supervisor is closed");
        if (batchTarget < request.absoluteTarget || batchTarget - request.absoluteTarget > 750000L)
            throw new IllegalArgumentException("runtime batch exceeds sixteen 50000-cycle quanta");
        final String key = request.identity.key();
        final Submission submission = new Submission();
        if (inFlight.putIfAbsent(key, submission) != null) throw new RejectedExecutionException("runtime key already has an in-flight quantum");
        final AtomicReference<WorkerProcess> active = new AtomicReference<WorkerProcess>();
        final FutureTask<Void> task = new FutureTask<Void>(new java.util.concurrent.Callable<Void>() {
            public Void call() {
                WorkerProcess worker = null;
                boolean reusable = false;
                try {
                    worker = acquireWorker();
                    active.set(worker);
                    RuntimeProtocol.Result result = worker.exchangeBatch(request, batchTarget);
                    reusable = submission.complete(result);
                } catch (Throwable failure) {
                    submission.fail(failure);
                } finally {
                    if (worker != null) {
                        active.compareAndSet(worker, null);
                        if (reusable && !closed && worker.isAlive()) idleWorkers.offer(worker);
                        else discard(worker);
                    }
                }
                return null;
            }
        }) {
            protected void done() { inFlight.remove(key, submission); }
            public boolean cancel(boolean mayInterruptIfRunning) {
                WorkerProcess worker = active.get();
                if (worker != null) worker.close();
                boolean cancelled = super.cancel(mayInterruptIfRunning);
                return cancelled;
            }
        };
        submission.bind(task);
        try { executor.execute(task); }
        catch (RejectedExecutionException full) { inFlight.remove(key, submission); throw full; }
        timer.schedule(new Runnable() {
            public void run() {
                if (!task.isDone()) {
                    inFlight.remove(key, submission);
                    if (submission.fail(new TimeoutException("runtime worker timed out"))) task.cancel(true);
                }
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        return submission;
    }

    public void close() {
        closed = true;
        executor.shutdownNow(); timer.shutdownNow();
        for (WorkerProcess worker : allWorkers.keySet()) discard(worker);
        idleWorkers.clear();
    }

    private WorkerProcess acquireWorker() throws IOException, InterruptedException {
        if (closed) throw new IOException("runtime supervisor is closed");
        WorkerProcess idle = idleWorkers.poll();
        if (idle != null) return idle;
        while (true) {
            int count = workerCount.get();
            if (count < MAX_WORKERS && workerCount.compareAndSet(count, count + 1)) {
                if (closed) { workerCount.decrementAndGet(); throw new IOException("runtime supervisor is closed"); }
                try {
                    WorkerProcess created = new WorkerProcess(launcher, javaHome, workerJar);
                    allWorkers.put(created, Boolean.TRUE);
                    if (closed) { discard(created); throw new IOException("runtime supervisor is closed"); }
                    return created;
                }
                catch (IOException failure) { workerCount.decrementAndGet(); throw failure; }
            }
            idle = idleWorkers.take();
            if (idle.isAlive()) return idle;
            discard(idle);
        }
    }

    private void discard(WorkerProcess worker) {
        if (allWorkers.remove(worker) != null) {
            worker.close();
            workerCount.decrementAndGet();
        }
    }

    private static ThreadFactory named(final String prefix) {
        final AtomicInteger next = new AtomicInteger();
        return new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, prefix + "-" + next.incrementAndGet());
                thread.setDaemon(true); return thread;
            }
        };
    }

    /** A completion cell polled by server ticks; reading it never waits. */
    public static final class Submission {
        private final AtomicReference<Completion> completion = new AtomicReference<Completion>();
        private volatile FutureTask<?> task;

        public Completion poll() { return completion.get(); }
        public void cancel() {
            FutureTask<?> value = task;
            if (value != null) value.cancel(true);
        }
        private void bind(FutureTask<?> value) { task = value; }
        private boolean complete(RuntimeProtocol.Result result) {
            return completion.compareAndSet(null, new Completion(result, null));
        }
        private boolean fail(Throwable failure) {
            return completion.compareAndSet(null, new Completion(null, failure));
        }
    }

    public static final class Completion {
        public final RuntimeProtocol.Result result;
        public final Throwable failure;

        private Completion(RuntimeProtocol.Result result, Throwable failure) {
            this.result = result;
            this.failure = failure;
        }
    }

    private static final class WorkerProcess implements Closeable {
        private final Process process;
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        WorkerProcess(File launcher, File javaHome, File workerJar) throws IOException {
            List<String> command = Collections.unmodifiableList(Arrays.asList(
                    launcher.getAbsolutePath(), javaHome.getCanonicalPath(), workerJar.getCanonicalPath()));
            process = new ProcessBuilder(command).start();
            Thread drain = new Thread(new Runnable() { public void run() { drainStderr(process.getErrorStream(), stderr); } }, "craftonica-runtime-stderr");
            drain.setDaemon(true); drain.start();
        }

        RuntimeProtocol.Result exchange(RuntimeProtocol.Request request) throws IOException {
            RuntimeProtocol.writeRequest(process.getOutputStream(), request);
            try {
                RuntimeProtocol.Result result = RuntimeProtocol.readResult(process.getInputStream());
                if (!sameIdentity(request.identity, result.identity)) throw new IOException("worker returned a mismatched identity tuple");
                return result;
            }
            catch (IOException failure) {
                throw new IOException("worker protocol failed; stderr=" + stderr.toString(), failure);
            }
        }

        RuntimeProtocol.Result exchangeBatch(RuntimeProtocol.Request request, long batchTarget) throws IOException {
            RuntimeProtocol.Result result = exchange(request);
            List<RuntimeProtocol.Gpio> gpio = new ArrayList<RuntimeProtocol.Gpio>(result.gpio);
            List<RuntimeProtocol.Pwm> pwm = new ArrayList<RuntimeProtocol.Pwm>(result.pwm);
            ByteArrayOutputStream tx = new ByteArrayOutputStream();
            tx.write(result.tx, 0, result.tx.length);
            while (result.fault == null && result.completedAtCycle < batchTarget) {
                long nextTarget = Math.min(result.completedAtCycle + 50000L, batchTarget);
                if (nextTarget <= result.completedAtCycle) throw new IOException("runtime worker made no cycle progress");
                RuntimeProtocol.Request next = new RuntimeProtocol.Request(request.identity, nextTarget,
                        request.firmware, result.checkpoint, request.inputs);
                result = exchange(next);
                gpio.addAll(result.gpio);
                pwm.addAll(result.pwm);
                if (tx.size() + result.tx.length > 4096) throw new IOException("runtime batch TX limit exceeded");
                tx.write(result.tx, 0, result.tx.length);
            }
            return new RuntimeProtocol.Result(result.identity, result.completedAtCycle, result.d13High,
                    result.checkpoint, gpio, pwm, tx.toByteArray(), result.fault);
        }

        boolean isAlive() { return process.isAlive(); }

        private static boolean sameIdentity(RuntimeProtocol.Identity left, RuntimeProtocol.Identity right) {
            return left.dimension == right.dimension && left.x == right.x && left.y == right.y
                    && left.z == right.z && left.generation == right.generation;
        }

        public void close() {
            process.destroy();
            process.destroyForcibly();
        }

        private static void drainStderr(InputStream input, ByteArrayOutputStream output) {
            byte[] buffer = new byte[512];
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    synchronized (output) {
                        int remaining = MAX_STDERR_BYTES - output.size();
                        if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
                    }
                }
            } catch (IOException ignored) { }
        }
    }
}
