package br.com.craftonica.robot.modular;

/** Port location on a component cell; offsets are local metres within the cell. */
public final class PortPose {
    public final GridVector cell;
    public final Direction face;
    public final Vector3 offsetMetres;

    public PortPose(GridVector cell, Direction face, Vector3 offsetMetres) {
        if (cell == null || face == null || offsetMetres == null) throw new IllegalArgumentException("port pose");
        if (offsetMetres.x < 0.0 || offsetMetres.x > 1.0 || offsetMetres.y < 0.0
                || offsetMetres.y > 1.0 || offsetMetres.z < 0.0 || offsetMetres.z > 1.0)
            throw new IllegalArgumentException("port offset");
        if ((face == Direction.DOWN && offsetMetres.y != 0.0)
                || (face == Direction.UP && offsetMetres.y != 1.0)
                || (face == Direction.NORTH && offsetMetres.z != 0.0)
                || (face == Direction.SOUTH && offsetMetres.z != 1.0)
                || (face == Direction.WEST && offsetMetres.x != 0.0)
                || (face == Direction.EAST && offsetMetres.x != 1.0))
            throw new IllegalArgumentException("port is not on face");
        this.cell = cell;
        this.face = face;
        this.offsetMetres = offsetMetres;
    }

    public static PortPose center(Direction face) {
        if (face == null) throw new IllegalArgumentException("face");
        return new PortPose(GridVector.ZERO, face, new Vector3(
                face == Direction.WEST ? 0.0 : face == Direction.EAST ? 1.0 : 0.5,
                face == Direction.DOWN ? 0.0 : face == Direction.UP ? 1.0 : 0.5,
                face == Direction.NORTH ? 0.0 : face == Direction.SOUTH ? 1.0 : 0.5));
    }
}
