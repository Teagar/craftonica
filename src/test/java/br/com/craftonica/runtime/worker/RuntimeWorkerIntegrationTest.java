package br.com.craftonica.runtime.worker;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RuntimeWorkerIntegrationTest {
    @Test
    public void persistentWorkerExecutesMinimalJmpAndRejectsMalformedFrames() throws Exception {
        File javaHome = requiredFile("craftonica.runtime.javaHome");
        File workerJar = requiredFile("craftonica.runtime.workerJar");
        byte[] flash = { 0x0c, (byte) 0x94, 0, 0 };
        CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);

        Process worker = start(javaHome, workerJar);
        try {
            byte[] checkpoint = AvrCheckpointCodec.encode(new AvrMachineState());
            RuntimeProtocol.Result first = exchange(worker, firmware, checkpoint, 49998, 1);
            assertEquals(49998, first.completedAtCycle);
            RuntimeProtocol.Result second = exchange(worker, firmware, first.checkpoint, 99996, 2);
            assertEquals(99996, second.completedAtCycle);
            assertEquals(2, second.identity.generation);
            assertFalse(second.d13High);
        } finally {
            worker.destroyForcibly();
        }

        Process malformed = start(javaHome, workerJar);
        malformed.getOutputStream().write(new byte[] { 'B', 'A', 'D', '!' });
        malformed.getOutputStream().close();
        assertTrue(malformed.waitFor(5, TimeUnit.SECONDS));
        assertEquals(70, malformed.exitValue());
        assertEquals(-1, malformed.getInputStream().read());
    }

    private static RuntimeProtocol.Result exchange(Process worker, CRLFirmware firmware, byte[] checkpoint,
                                                   long target, long generation) throws Exception {
        RuntimeProtocol.Request request = new RuntimeProtocol.Request(
                new RuntimeProtocol.Identity(0, 4, 5, 6, generation), target,
                firmware.getBytes(), checkpoint, AvrInputs.allLow());
        RuntimeProtocol.writeRequest(worker.getOutputStream(), request);
        return RuntimeProtocol.readResult(worker.getInputStream());
    }

    private static Process start(File javaHome, File workerJar) throws Exception {
        return new ProcessBuilder(new File(javaHome, "bin/java").getAbsolutePath(), "-jar", workerJar.getAbsolutePath()).start();
    }

    private static File requiredFile(String property) {
        String value = System.getProperty(property);
        Assume.assumeNotNull(value);
        File file = new File(value);
        Assume.assumeTrue(file.exists());
        return file;
    }
}
