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

    public static int placementSideMetadata(float rotationYaw, int metadata) {
        int facing = (int) Math.floor(rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int side = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
        return (metadata & ~7) | side;
    }

    public static int placementAxisMetadata(float rotationYaw, int metadata) {
        int facing = (int) Math.floor(rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int axis = facing == 0 || facing == 2 ? 0 : 1;
        return (metadata & ~1) | axis;
    }
}
