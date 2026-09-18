package br.com.craftonica.firmware;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class CRLFirmwareTest {
    @Test
    public void emitsAndReadsThePackedCanonicalFormat() {
        SourceBundle sources = new SourceBundle("main", Collections.singletonMap("main.ino",
                "void setup(){} void loop(){}".getBytes(StandardCharsets.UTF_8)));
        byte[] flash = { 0x0c, (byte) 0x94, 0x00, 0x00 };
        CRLFirmware firmware = CRLFirmware.create(sources, flash);
        byte[] encoded = firmware.getBytes();
        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(168, encoded.length);
        assertEquals(CRLFirmware.VERSION, header.getShort(4) & 0xffff);
        assertEquals(CRLFirmware.HEADER_LENGTH, header.getShort(6) & 0xffff);
        assertEquals(168, header.getInt(8));
        assertEquals(1, encoded[148]);
        assertEquals(164, header.getInt(160));
        assertArrayEquals(flash, CRLFirmware.decode(encoded).getFlash());
        assertArrayEquals(sources.getSourceHash(), CRLFirmware.decode(encoded).getSourceHash());
    }

    @Test
    public void matchesTheVersionedMinimalFixture() {
        String fixture = "Q1JMRgEAlACoAAAAAQAAAAEAAQDRgbh9gH3N3EKcEwaZJFz7OMQQ/HwYmDI+TSY/u+nvNuVY+hT+aUOos3j9nqvbxl8pbHSOeoBm8mdR97aHhratwEeNNaOlHfvtqnIhTn76cByh+RKBb6KauPMorgIMjelixD+X5bGVyk++26QNjolnwbBbW6+lWuFj052lxpJnlAEAAAAAAAAABAAAAKQAAAAMlAAA";
        byte[] expected = Base64.getDecoder().decode(fixture);
        SourceBundle sources = new SourceBundle("main", Collections.singletonMap("main.ino",
                "void setup(){} void loop(){}".getBytes(StandardCharsets.UTF_8)));
        CRLFirmware generated = CRLFirmware.create(sources, new byte[] { 0x0c, (byte) 0x94, 0, 0 });
        assertArrayEquals(expected, generated.getBytes());
        assertEquals("62c43f97e5b195ca4fbedba40d8e8967c1b05b5bafa55ae163d39da5c6926794",
                generated.getFirmwareHashHex());
    }

    @Test
    public void rejectsMutationsMalformedRangesAndTrailingData() {
        byte[] valid = firmware().getBytes();
        reject(changed(valid, 20));
        reject(changed(valid, 84));
        reject(changed(valid, 116));
        reject(changed(valid, 148));
        reject(changed(valid, 165));

        byte[] trailing = new byte[valid.length + 1];
        System.arraycopy(valid, 0, trailing, 0, valid.length);
        reject(trailing);

        byte[] oddLength = valid.clone();
        ByteBuffer.wrap(oddLength).order(ByteOrder.LITTLE_ENDIAN).putInt(156, 3);
        reject(oddLength);
    }

    @Test
    public void enforcesFlashLimitParityAndCopies() {
        tryCreate(new byte[1]);
        tryCreate(new byte[ToolchainProfile.MAX_FLASH_BYTES + 2]);
        byte[] flash = new byte[] { 1, 2 };
        CRLFirmware firmware = CRLFirmware.create(new byte[32], flash);
        flash[0] = 9;
        byte[] returned = firmware.getFlash();
        returned[1] = 9;
        assertArrayEquals(new byte[] { 1, 2 }, firmware.getFlash());
    }

    private static CRLFirmware firmware() {
        return CRLFirmware.create(new byte[32], new byte[] { 1, 2, 3, 4 });
    }

    private static byte[] changed(byte[] original, int offset) {
        byte[] changed = original.clone();
        changed[offset] ^= 1;
        return changed;
    }

    private static void reject(byte[] bytes) {
        try {
            CRLFirmware.decode(bytes);
            fail("Expected malformed firmware rejection");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void tryCreate(byte[] flash) {
        try {
            CRLFirmware.create(new byte[32], flash);
            fail("Expected invalid FLASH rejection");
        } catch (IllegalArgumentException expected) {
        }
    }
}
