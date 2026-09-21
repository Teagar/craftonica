package br.com.craftonica.robot.modular;

/** One of the 24 orthogonal block orientations, represented by front and up. */
public final class ComponentOrientation {
    public static final ComponentOrientation NORTH_UP = new ComponentOrientation(Direction.NORTH, Direction.UP);

    private final Direction forward;
    private final Direction up;
    private final Direction right;

    public ComponentOrientation(Direction forward, Direction up) {
        if (forward == null || up == null || dot(forward.vector, up.vector) != 0)
            throw new IllegalArgumentException("orientation axes");
        this.forward = forward;
        this.up = up;
        this.right = Direction.fromVector(cross(forward.vector, up.vector));
    }

    public Direction getForward() { return forward; }
    public Direction getUp() { return up; }
    public Direction getRight() { return right; }

    public Direction toWorld(Direction local) {
        if (local == null) throw new IllegalArgumentException("local");
        GridVector v = local.vector;
        GridVector world = right.vector.scale(v.x).add(up.vector.scale(v.y))
                .add(forward.vector.scale(-v.z));
        return Direction.fromVector(world);
    }

    private static int dot(GridVector a, GridVector b) { return a.x * b.x + a.y * b.y + a.z * b.z; }

    private static GridVector cross(GridVector a, GridVector b) {
        return new GridVector(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }
}
