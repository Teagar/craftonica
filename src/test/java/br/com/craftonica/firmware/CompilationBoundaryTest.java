package br.com.craftonica.firmware;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CompilationBoundaryTest {
    @Test
    public void diagnosticsAreBoundedAndHideHostPathsAndControls() {
        List<String> raw = new ArrayList<String>();
        for (int i = 0; i < 150; i++) raw.add("/srv/secret/job" + i + "/main.ino:\u0000 error");
        CompilationDiagnostics diagnostics = CompilationDiagnostics.sanitize(raw);
        assertTrue(diagnostics.getEntries().size() <= CompilationDiagnostics.MAX_ENTRIES);
        assertTrue(diagnostics.getUtf8Bytes() <= CompilationDiagnostics.MAX_UTF8_BYTES);
        assertFalse(diagnostics.getEntries().get(0).contains("/srv/secret"));
        assertFalse(diagnostics.getEntries().get(0).contains("\u0000"));
    }

    @Test
    public void rejectsASecondPendingJobForTheSameOwnerAndCancellationIsExplicit() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        BoundedCompilationService service = new BoundedCompilationService(new CompilerSupervisor() {
            @Override
            public CompilationResult compile(CompilationRequest request) throws Exception {
                entered.countDown();
                release.await();
                return CompilationResult.failure(CompilationResult.Status.COMPILE_ERROR, "bad sketch");
            }
        }, 1);
        try {
            CompilationHandle first = service.submit(request("owner"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(CompilationResult.Status.REJECTED, service.submit(request("owner")).await().getStatus());
            assertTrue(first.cancel());
            assertEquals(CompilationResult.Status.CANCELLED, first.await().getStatus());
            assertFalse(first.cancel());
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    public void queueHasExactlyThirtyTwoWaitingSlots() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        BoundedCompilationService service = new BoundedCompilationService(new CompilerSupervisor() {
            @Override
            public CompilationResult compile(CompilationRequest request) throws Exception {
                release.await();
                return CompilationResult.failure(CompilationResult.Status.COMPILE_ERROR, "done");
            }
        }, 1);
        try {
            List<CompilationHandle> accepted = new ArrayList<CompilationHandle>();
            for (int i = 0; i < 33; i++) accepted.add(service.submit(request("owner-" + i)));
            CompilationHandle overflow = service.submit(request("overflow"));
            assertEquals(CompilationResult.Status.REJECTED, overflow.await().getStatus());
            assertFalse(accepted.get(32).isDone());
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    public void cancellationKeepsOwnerReservedUntilSupervisorTerminates() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final CountDownLatch terminated = new CountDownLatch(1);
        BoundedCompilationService service = new BoundedCompilationService(new CompilerSupervisor() {
            @Override
            public CompilationResult compile(CompilationRequest request) throws Exception {
                entered.countDown();
                try {
                    terminated.await();
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                    terminated.await();
                    throw expected;
                }
                return CompilationResult.failure(CompilationResult.Status.COMPILE_ERROR, "done");
            }
        }, 1);
        try {
            CompilationHandle first = service.submit(request("owner"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(first.cancel());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(CompilationResult.Status.REJECTED, service.submit(request("owner")).await().getStatus());
            assertFalse(first.isDone());
            terminated.countDown();
            assertEquals(CompilationResult.Status.CANCELLED, first.await().getStatus());
        } finally {
            terminated.countDown();
            service.close();
        }
    }

    private static CompilationRequest request(String owner) {
        SourceBundle sources = new SourceBundle("main", Collections.singletonMap("main.ino",
                "void setup(){} void loop(){}".getBytes(StandardCharsets.UTF_8)));
        return new CompilationRequest(owner, UUID.randomUUID(), 1, sources);
    }
}
