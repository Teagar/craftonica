package br.com.craftonica.robot;

import br.com.craftonica.firmware.SourceBundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bundled, bounded Arduino source for the single-sonar navigation experiment. */
public final class AutonomousRobotSketch {
    public static final String RESOURCE = "/assets/craftonica/sketches/hc_sr04_autonomous_robot.ino";

    private AutonomousRobotSketch() { }

    public static String load() throws IOException {
        InputStream input = AutonomousRobotSketch.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IOException("autonomous sketch resource missing");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int total = 0, read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > SourceBundle.MAX_FILE_BYTES) throw new IOException("autonomous sketch exceeds source limit");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
