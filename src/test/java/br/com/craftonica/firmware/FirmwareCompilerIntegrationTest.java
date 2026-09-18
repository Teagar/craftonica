package br.com.craftonica.firmware;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FirmwareCompilerIntegrationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void compilesAndIndependentlyCanonicalizesBlink() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("craftonica.firmware.integration"));
        Path script = java.nio.file.Paths.get("scripts/firmware/compile-sketch.sh").toAbsolutePath();
        Path work = temporary.newFolder("jobs").toPath();
        FirmwareCache cache = new FirmwareCache(temporary.newFolder("cache").toPath());
        ProcessCompilerSupervisor supervisor = new ProcessCompilerSupervisor(script, work, cache);
        SourceBundle sources = new SourceBundle("Blink", Collections.singletonMap("Blink.ino", (
                "void setup(){pinMode(LED_BUILTIN,OUTPUT);}\n"
                        + "void loop(){digitalWrite(LED_BUILTIN,HIGH);delay(500);"
                        + "digitalWrite(LED_BUILTIN,LOW);delay(500);}\n").getBytes(StandardCharsets.UTF_8)));
        CompilationRequest firstRequest = new CompilationRequest("integration-a", UUID.randomUUID(), 1, sources);
        CompilationRequest secondRequest = new CompilationRequest("integration-b", UUID.randomUUID(), 1, sources);

        CompilationResult first = supervisor.compile(firstRequest);
        CompilationResult second = supervisor.compile(secondRequest);

        assertEquals(first.getDiagnostics().getEntries().toString(), CompilationResult.Status.SUCCESS, first.getStatus());
        assertEquals(second.getDiagnostics().getEntries().toString(), CompilationResult.Status.SUCCESS, second.getStatus());
        assertArrayEquals(first.getFirmware().getBytes(), second.getFirmware().getBytes());
        assertArrayEquals(sources.getSourceHash(), first.getFirmware().getSourceHash());
        assertTrue(first.getFirmware().getFlash().length > 0);
        assertTrue(second.getDiagnostics().getEntries().contains("Verified firmware cache hit"));
    }

    @Test
    public void cancellationLeavesNoCompilerSystemdUnit() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("craftonica.firmware.integration"));
        Path script = java.nio.file.Paths.get("scripts/firmware/compile-sketch.sh").toAbsolutePath();
        Path work = temporary.newFolder("cancel-jobs").toPath();
        ProcessCompilerSupervisor supervisor = new ProcessCompilerSupervisor(script, work);
        SourceBundle sources = new SourceBundle("Cancel", Collections.singletonMap("Cancel.ino", (
                "template<int N> struct Slow { enum { value = Slow<N-1>::value + 1 }; };\n"
                        + "template<> struct Slow<0> { enum { value = 0 }; };\n"
                        + "volatile int result = Slow<800>::value;\n"
                        + "void setup(){} void loop(){}\n").getBytes(StandardCharsets.UTF_8)));
        BoundedCompilationService service = new BoundedCompilationService(supervisor, 1);
        try {
            CompilationHandle handle = service.submit(new CompilationRequest("cancel-owner", UUID.randomUUID(), 1, sources));
            assertTrue("Compiler service never became active", waitForCompilerUnit());
            handle.cancel();
            assertEquals(CompilationResult.Status.CANCELLED, handle.await().getStatus());
            Process check = new ProcessBuilder("/usr/bin/systemctl", "--user", "list-units", "--all", "--plain",
                    "--no-legend", "craftonica-compile-*.service").redirectErrorStream(true).start();
            byte[] output = readAll(check.getInputStream());
            assertTrue(check.waitFor(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(new String(output, StandardCharsets.UTF_8), 0, check.exitValue());
            assertTrue(new String(output, StandardCharsets.UTF_8).trim().isEmpty());
        } finally {
            service.close();
        }
    }

    private static byte[] readAll(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static boolean waitForCompilerUnit() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Process check = new ProcessBuilder("/usr/bin/systemctl", "--user", "list-units", "--all", "--plain",
                    "--no-legend", "craftonica-compile-*.service").redirectErrorStream(true).start();
            String output = new String(readAll(check.getInputStream()), StandardCharsets.UTF_8);
            check.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            if (check.exitValue() == 0 && output.contains("craftonica-compile-")) return true;
            Thread.sleep(20);
        }
        return false;
    }
}
