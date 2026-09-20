package br.com.craftonica.runtime.server;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public final class RuntimeStressGateTest {
    @Test public void sixteenMobileRobotsRemainIsolatedBehindFourWorkers() throws Exception {
        File workerJar = requiredFile("craftonica.runtime.workerJar");
        File javaHome = requiredFile("craftonica.runtime.javaHome");
        File launcher = directLauncher();
        RuntimeSupervisor supervisor = new RuntimeSupervisor(launcher, javaHome, workerJar, 10000);
        try {
            List<RuntimeSupervisor.Submission> submissions = new ArrayList<RuntimeSupervisor.Submission>();
            Set<UUID> expected = new HashSet<UUID>();
            for (int i = 0; i < 16; i++) {
                UUID id = new UUID(0x43524c66L, i + 1L); expected.add(id);
                RuntimeProtocol.Request request = request(id, i + 1L);
                submissions.add(supervisor.submit(request, 799968L));
            }
            Set<UUID> actual = new HashSet<UUID>();
            for (RuntimeSupervisor.Submission submission : submissions) {
                RuntimeProtocol.Result result = await(submission, 15000L);
                actual.add(result.identity.hostId);
                assertTrue(result.completedAtCycle >= 799968L && result.completedAtCycle <= 799970L);
                assertNull(result.fault);
            }
            assertEquals(expected, actual);
            assertTrue(supervisor.workerCountForTest() <= RuntimeSupervisor.MAX_WORKERS);
            assertEquals(0, supervisor.inFlightCountForTest());
            assertEquals(0, supervisor.queuedCountForTest());
        } finally {
            supervisor.close(); launcher.delete();
        }
    }

    @Test public void queueRejectsTheSixtyNinthConcurrentRobotWithoutGrowing() throws Exception {
        File workerJar = requiredFile("craftonica.runtime.workerJar");
        File javaHome = requiredFile("craftonica.runtime.javaHome");
        File launcher = sleepingLauncher();
        RuntimeSupervisor supervisor = new RuntimeSupervisor(launcher, javaHome, workerJar, 30000);
        try {
            int capacity = RuntimeSupervisor.MAX_WORKERS + RuntimeSupervisor.MAX_QUEUED;
            for (int i = 0; i < capacity; i++)
                supervisor.submit(request(new UUID(0x5155455545L, i + 1L), i + 1L));
            try {
                supervisor.submit(request(new UUID(0x5155455545L, capacity + 1L), capacity + 1L));
                fail("unbounded runtime queue accepted");
            } catch (RejectedExecutionException expected) { }
            assertTrue(supervisor.workerCountForTest() <= RuntimeSupervisor.MAX_WORKERS);
            assertTrue(supervisor.queuedCountForTest() <= RuntimeSupervisor.MAX_QUEUED);
            assertTrue(supervisor.inFlightCountForTest() <= capacity);
        } finally {
            supervisor.close(); launcher.delete();
        }
    }

    private static RuntimeProtocol.Request request(UUID id, long generation) {
        byte[] flash = { 0x0c, (byte) 0x94, 0, 0 };
        CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);
        return new RuntimeProtocol.Request(RuntimeProtocol.Identity.mobile(0, id, generation), 49998L,
                firmware.getBytes(), AvrCheckpointCodec.encode(new AvrMachineState()), AvrInputs.allLow());
    }

    private static RuntimeProtocol.Result await(RuntimeSupervisor.Submission submission, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (submission.poll() == null && System.currentTimeMillis() < deadline) Thread.sleep(5L);
        RuntimeSupervisor.Completion completion = submission.poll();
        if (completion == null) throw new TimeoutException("stress gate did not complete");
        if (completion.failure instanceof Exception) throw (Exception) completion.failure;
        if (completion.failure != null) throw new AssertionError(completion.failure);
        return completion.result;
    }

    private static File directLauncher() throws Exception {
        File file = File.createTempFile("crl-runtime-direct-", ".sh");
        FileWriter writer = new FileWriter(file);
        try { writer.write("#!/bin/sh\nexec \"$1/bin/java\" -Xms8m -Xmx64m -XX:+UseSerialGC -jar \"$2\"\n"); }
        finally { writer.close(); }
        assertTrue(file.setExecutable(true)); return file;
    }

    private static File sleepingLauncher() throws Exception {
        File file = File.createTempFile("crl-runtime-sleep-", ".sh");
        FileWriter writer = new FileWriter(file);
        try { writer.write("#!/bin/sh\nexec sleep 30\n"); }
        finally { writer.close(); }
        assertTrue(file.setExecutable(true)); return file;
    }

    private static File requiredFile(String property) {
        String value = System.getProperty(property); Assume.assumeNotNull(value);
        File file = new File(value); Assume.assumeTrue(file.exists()); return file;
    }
}
