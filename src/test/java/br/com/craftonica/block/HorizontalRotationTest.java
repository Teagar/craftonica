package br.com.craftonica.block;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HorizontalRotationTest {
    @Test
    public void rotatesHorizontalSidesClockwise() {
        int metadata = 2;
        metadata = HorizontalRotation.rotateSideMetadata(metadata);
        assertEquals(5, metadata);
        metadata = HorizontalRotation.rotateSideMetadata(metadata);
        assertEquals(3, metadata);
        metadata = HorizontalRotation.rotateSideMetadata(metadata);
        assertEquals(4, metadata);
        metadata = HorizontalRotation.rotateSideMetadata(metadata);
        assertEquals(2, metadata);
    }

    @Test
    public void togglesAxisWithoutLosingButtonState() {
        assertEquals(3, HorizontalRotation.rotateAxisMetadata(2));
        assertEquals(2, HorizontalRotation.rotateAxisMetadata(3));
    }

    @Test
    public void preservesUnusedMetadataBits() {
        assertEquals(13, HorizontalRotation.rotateSideMetadata(10));
    }
}
