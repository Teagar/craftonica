package br.com.craftonica.block;

public final class HorizontalRotation {
    private HorizontalRotation() {
    }

    public static int rotateSideMetadata(int metadata) {
        int side = metadata & 7;
        int rotated = side == 2 ? 5 : side == 5 ? 3 : side == 3 ? 4 : side == 4 ? 2 : side;
        return (metadata & ~7) | rotated;
    }

    public static int rotateAxisMetadata(int metadata) {
        return metadata ^ 1;
    }
}
