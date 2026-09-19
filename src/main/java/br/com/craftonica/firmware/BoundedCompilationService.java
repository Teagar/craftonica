package br.com.craftonica.firmware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded scheduling only. Compiler process isolation belongs to {@link CompilerSupervisor}. */
public final class BoundedCompilationService implements AutoCloseable {
    public static final int GLOBAL_QUEUE_CAPACITY = 32;

    private final CompilerSupervisor supervisor;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> owners = new ConcurrentHashMap<String, Job>();
    private final Object lifecycle = new Object();
    private boolean closed;

    public BoundedCompilationService(CompilerSupervisor supervisor, int workers) {
        if (supervisor == null || workers < 1) throw new IllegalArgumentException("A supervisor and worker are required");
        this.supervisor = supervisor;
        final AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "craftonica-compiler-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(GLOBAL_QUEUE_CAPACITY), threads, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompilationHandle submit(CompilationRequest request) {
        if (request == null) throw new IllegalArgumentException("Compilation request is required");
        Job job = new Job(request);
        synchronized (lifecycle) {
            if (closed) {
                job.complete(CompilationResult.failure(CompilationResult.Status.REJECTED,
                        "The compilation service is closed"));
                return job;
            }
            Job existing = owners.putIfAbsent(request.getOwnerId(), job);
            if (existing != null) {
                job.complete(CompilationResult.failure(CompilationResult.Status.REJECTED,
                        "This owner already has a pending compilation"));
                return job;
            }
            try {
                executor.execute(job);
            } catch (RejectedExecutionException e) {
                owners.remove(request.getOwnerId(), job);
                job.complete(CompilationResult.failure(CompilationResult.Status.REJECTED,
                        "The compilation queue is full or closed"));
            }
        }
        return job;
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            for (Job job : new ArrayList<Job>(owners.values())) job.cancel();
            for (Runnable pending : executor.shutdownNow()) ((Job) pending).cancelQueued();
        }
        try {
            executor.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Job implements Runnable, CompilationHandle {
        private final CompilationRequest request;
        private CompilationResult result;
        private Thread runningThread;
        private boolean cancellationRequested;

        private Job(CompilationRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            synchronized (this) {
                if (result != null) return;
                runningThread = Thread.currentThread();
            }
            CompilationResult produced;
            try {
                produced = supervisor.compile(request);
                if (produced == null) produced = CompilationResult.failure(CompilationResult.Status.COMPILER_UNAVAILABLE,
                        "Compiler supervisor returned no result");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                produced = CompilationResult.failure(CompilationResult.Status.CANCELLED, "Compilation cancelled");
            } catch (Exception e) {
                produced = CompilationResult.failure(CompilationResult.Status.COMPILER_UNAVAILABLE,
                        "Compiler supervisor failed: " + e.getClass().getSimpleName());
            }
            synchronized (this) {
                if (cancellationRequested) produced = CompilationResult.failure(
                        CompilationResult.Status.CANCELLED, "Compilation cancelled");
            }
            complete(produced);
        }

        @Override
        public synchronized CompilationResult await() throws InterruptedException {
            while (result == null) wait();
            return result;
        }

        @Override
        public boolean cancel() {
            Thread interrupt;
            synchronized (this) {
                if (result != null || cancellationRequested) return false;
                cancellationRequested = true;
                interrupt = runningThread;
            }
            if (interrupt == null && executor.remove(this)) cancelQueued();
            else if (interrupt != null) interrupt.interrupt();
            return true;
        }

        @Override
        public synchronized boolean isDone() { return result != null; }

        private void complete(CompilationResult value) {
            synchronized (this) {
                if (result != null) return;
                result = value;
                runningThread = null;
                notifyAll();
            }
            owners.remove(request.getOwnerId(), this);
        }

        private void cancelQueued() {
            complete(CompilationResult.failure(CompilationResult.Status.CANCELLED, "Compilation cancelled"));
        }
    }
}
