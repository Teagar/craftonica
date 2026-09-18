package br.com.craftonica.client.sketch;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class SerialSanitizerTest {
    @Test
    public void preservesPrintableUtf8AndSafeWhitespace() {
        byte[] raw = "Olá\r\nvalor:\t42".getBytes(StandardCharsets.UTF_8);
        assertEquals("Olá\r\nvalor:\t42", SerialSanitizer.sanitize(raw));
    }

    @Test
    public void replacesControlAndMalformedBytes() {
        byte[] raw = new byte[] {'A', 0, 7, (byte) 0xc3, '(', 'Z'};
        assertEquals("A???(Z", SerialSanitizer.sanitize(raw));
    }

    @Test
    public void sectionSignCannotBecomeMinecraftFormatting() {
        assertEquals("?cRED", SerialSanitizer.sanitize("§cRED".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void emptyInputsAreSafe() {
        assertEquals("", SerialSanitizer.sanitize(null));
        assertEquals("", SerialSanitizer.sanitize(new byte[0]));
    }
}
