package br.com.craftonica.robot;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AutonomousRobotSketchTest {
    @Test public void bundledSketchMatchesCompilableExampleAndDocumentsSafeTimeouts() throws Exception {
        String bundled = AutonomousRobotSketch.load();
        String example = new String(Files.readAllBytes(Paths.get(
                "examples/arduino/hc_sr04_autonomous_robot/hc_sr04_autonomous_robot.ino")),
                StandardCharsets.UTF_8);
        assertEquals(example, bundled);
        assertTrue(bundled.contains("pulseIn(ECHO_PIN, HIGH, 30000UL)"));
        assertTrue(bundled.contains("if (valid < 2) return false"));
        assertTrue(bundled.contains("TIMEOUT_ALL_STOP,NA"));
        assertTrue(bundled.contains("TURN_90_MS = 1000"));
        assertTrue(bundled.contains("TURN_180_MS = 1970"));
        assertTrue(bundled.contains("const byte LEFT_PWM = 5"));
        assertTrue(bundled.contains("const byte RIGHT_PWM = 9"));
    }
}
