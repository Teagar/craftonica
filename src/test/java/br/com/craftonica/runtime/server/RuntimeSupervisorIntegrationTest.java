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
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RuntimeSupervisorIntegrationTest {
    @Test
    public void sandboxedWorkerRunsMinimalJmpForOneBoundedQuantumAndCanBeReused() throws Exception {
        File launcher = requiredFile("craftonica.runtime.launcher");
        File workerJar = requiredFile("craftonica.runtime.workerJar");
        File javaHome = requiredFile("craftonica.runtime.javaHome");
        Assume.assumeTrue(new File("/usr/bin/bwrap").canExecute());
        Assume.assumeTrue(sandboxNamespacesAvailable());

        RuntimeSupervisor supervisor = new RuntimeSupervisor(launcher, javaHome, workerJar, 5000);
        try {
            byte[] flash = { 0x0c, (byte) 0x94, 0, 0 }; // JMP 0, three cycles.
            CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);
            byte[] checkpoint = AvrCheckpointCodec.encode(new AvrMachineState());
            RuntimeProtocol.Identity firstIdentity = new RuntimeProtocol.Identity(0, 10, 64, 10, 1);
            RuntimeProtocol.Request first = new RuntimeProtocol.Request(firstIdentity, 49998,
                    firmware.getBytes(), checkpoint, AvrInputs.allLow());
            RuntimeProtocol.Result result = await(supervisor.submit(first, 799968));
            assertTrue(result.completedAtCycle >= 799968 && result.completedAtCycle <= 799970);
            assertEquals(1, result.identity.generation);
            assertFalse(result.d13High);

            RuntimeProtocol.Identity nextIdentity = new RuntimeProtocol.Identity(0, 10, 64, 10, 2);
            RuntimeProtocol.Request next = new RuntimeProtocol.Request(nextIdentity, 849966,
                    firmware.getBytes(), result.checkpoint, AvrInputs.allLow());
            RuntimeSupervisor.Submission submission = supervisor.submit(next, 1599936);
            result = await(submission);
            assertTrue(result.completedAtCycle >= 1599936 && result.completedAtCycle <= 1599938);
            assertEquals(2, result.identity.generation);

            RuntimeProtocol.Identity mobileIdentity = RuntimeProtocol.Identity.mobile(0, new UUID(55L, 89L), 3L);
            RuntimeProtocol.Request mobile = new RuntimeProtocol.Request(mobileIdentity, 49998,
                    firmware.getBytes(), checkpoint, AvrInputs.allLow());
            result = await(supervisor.submit(mobile));
            assertEquals(RuntimeProtocol.Identity.MOBILE_ROBOT, result.identity.kind);
            assertEquals(mobileIdentity.hostId, result.identity.hostId);
        } finally {
            supervisor.close();
        }
    }

    @Test
    public void timeoutKillsTheWorkerAndTheNextSubmissionGetsAReplacement() throws Exception {
        File workerJar = requiredFile("craftonica.runtime.workerJar");
        File javaHome = requiredFile("craftonica.runtime.javaHome");
        File directory = new File(System.getProperty("java.io.tmpdir"), "crl-runtime-" + System.nanoTime());
        Assume.assumeTrue(directory.mkdir());
        File marker = new File(directory, "launched");
        File launcher = new File(directory, "launcher.sh");
        FileWriter writer = new FileWriter(launcher);
        try {
            writer.write("#!/bin/sh\n");
            writer.write("if [ ! -e '" + marker.getAbsolutePath() + "' ]; then touch '" + marker.getAbsolutePath() + "'; exec sleep 30; fi\n");
            writer.write("exec \"$1/bin/java\" -jar \"$2\"\n");
        } finally {
            writer.close();
        }
        Assume.assumeTrue(launcher.setExecutable(true));

        RuntimeSupervisor supervisor = new RuntimeSupervisor(launcher, javaHome, workerJar, 3000);
        try {
            RuntimeProtocol.Request request = request(77);
            try {
                await(supervisor.submit(request));
                fail("expected timeout");
            } catch (TimeoutException expected) { }
            RuntimeProtocol.Result replacement = await(supervisor.submit(request(78)));
            assertEquals(49998, replacement.completedAtCycle);
            assertEquals(78, replacement.identity.generation);
        } finally {
            supervisor.close();
            launcher.delete(); marker.delete(); directory.delete();
        }
    }

    private static File requiredFile(String property) {
        String value = System.getProperty(property);
        Assume.assumeNotNull(value);
        File file = new File(value);
        Assume.assumeTrue(file.exists());
        return file;
    }

    private static boolean sandboxNamespacesAvailable() {
        try {
            Process probe = new ProcessBuilder("/usr/bin/bwrap", "--unshare-all", "--ro-bind", "/", "/",
                    "--", "/bin/true").start();
            return probe.waitFor() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static RuntimeProtocol.Request request(long generation) {
        byte[] flash = { 0x0c, (byte) 0x94, 0, 0 };
        CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);
        return new RuntimeProtocol.Request(new RuntimeProtocol.Identity(0, 10, 64, 10, generation), 49998,
                firmware.getBytes(), AvrCheckpointCodec.encode(new AvrMachineState()), AvrInputs.allLow());
    }

    private static RuntimeProtocol.Result await(RuntimeSupervisor.Submission submission) throws Exception {
        long deadline = System.currentTimeMillis() + 7000L;
        while (submission.poll() == null && System.currentTimeMillis() < deadline) Thread.sleep(10L);
        RuntimeSupervisor.Completion completion = submission.poll();
        if (completion == null) throw new TimeoutException("test did not observe a completion");
        if (completion.failure instanceof Exception) throw (Exception) completion.failure;
        if (completion.failure != null) throw new AssertionError(completion.failure);
        return completion.result;
    }
}
