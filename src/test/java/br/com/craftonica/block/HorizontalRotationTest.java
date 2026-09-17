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

    @Test
    public void predictsSideFromPlayerYaw() {
        assertEquals(3, HorizontalRotation.placementSideMetadata(0.0F, 0));
        assertEquals(4, HorizontalRotation.placementSideMetadata(90.0F, 0));
        assertEquals(2, HorizontalRotation.placementSideMetadata(180.0F, 0));
        assertEquals(5, HorizontalRotation.placementSideMetadata(270.0F, 0));
    }

    @Test
    public void predictsAxisWithoutLosingState() {
        assertEquals(2, HorizontalRotation.placementAxisMetadata(0.0F, 2));
        assertEquals(3, HorizontalRotation.placementAxisMetadata(90.0F, 2));
    }
}
