package br.com.craftonica.release;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class MobileVisualReleaseTest {
    @Test public void mobileAssetsHaveThePublishedPixelDimensions() throws Exception {
        assertPng("/assets/craftonica/textures/blocks/robot_chassis.png", 16, 16);
        assertPng("/assets/craftonica/textures/blocks/h_bridge.png", 16, 16);
        assertPng("/assets/craftonica/textures/blocks/ultrasonic_front.png", 16, 16);
        assertPng("/assets/craftonica/textures/entity/mobile_robot.png", 64, 64);
    }

    @Test public void readmeClassifiesEveryMobileVisualAsFinal() throws Exception {
        String readme = new String(Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
        assertFinal(readme, "Sensor de distância HC-SR04");
        assertFinal(readme, "Ponte H");
        assertFinal(readme, "Chassi robótico móvel");
        assertFinal(readme, "Rodas e motores móveis");
    }

    private static void assertPng(String resource, int width, int height) throws Exception {
        InputStream input = MobileVisualReleaseTest.class.getResourceAsStream(resource);
        assertNotNull("asset ausente: " + resource, input);
        try {
            BufferedImage image = ImageIO.read(input);
            assertNotNull("PNG inválido: " + resource, image);
            assertEquals(resource, width, image.getWidth());
            assertEquals(resource, height, image.getHeight());
        } finally { input.close(); }
    }

    private static void assertFinal(String readme, String component) {
        String prefix = "| " + component + " | **Final** | **Final** |";
        assertTrue("maturidade visual ausente: " + component, readme.contains(prefix));
    }
}
