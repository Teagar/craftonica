package br.com.craftonica.client;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ReleaseAssetInventoryTest {
    @Test public void modularMechanicalComponentsHaveDistinctPngAssets() throws Exception {
        String[] names = { "mechanical_axle", "mechanical_bearing", "spur_gear_12", "spur_gear_36",
                "educational_servo", "robot_wheel", "robot_wheel_150", "passive_caster" };
        Set<String> hashes = new HashSet<String>();
        for (String name : names) {
            byte[] png = resource("assets/craftonica/textures/blocks/" + name + ".png");
            assertTrue(name, png.length > 64);
            assertEquals((byte) 0x89, png[0]); assertEquals((byte) 'P', png[1]);
            hashes.add(hex(MessageDigest.getInstance("SHA-256").digest(png)));
        }
        assertEquals("mechanical assets must not alias placeholders", names.length, hashes.size());
    }

    private static byte[] resource(String path) throws Exception {
        InputStream input = ReleaseAssetInventoryTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(path, input); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try { byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        } finally { input.close(); }
        return bytes.toByteArray();
    }
    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte part : value) result.append(String.format("%02x", part & 255));
        return result.toString();
    }
}
