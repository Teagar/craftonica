package br.com.craftonica.robot.modular.physics.articulated;

import br.com.craftonica.robot.modular.Vector3;

/** Immutable deterministic rigid transform, represented by a 3x3 rotation and translation. */
public strictfp final class RigidTransform3 {
    private final double[] r;
    public final Vector3 translation;

    private RigidTransform3(double[] rotation, Vector3 translation) {
        if (rotation == null || rotation.length != 9 || translation == null)
            throw new IllegalArgumentException("rigid transform");
        for (double value : rotation) if (!finite(value)) throw new IllegalArgumentException("rotation");
        this.r = rotation.clone(); this.translation = translation;
    }

    public static RigidTransform3 identity() {
        return new RigidTransform3(new double[] { 1,0,0, 0,1,0, 0,0,1 }, Vector3.ZERO);
    }

    public static RigidTransform3 translation(Vector3 value) {
        return new RigidTransform3(new double[] { 1,0,0, 0,1,0, 0,0,1 }, value);
    }

    public static RigidTransform3 rotation(Vector3 unitAxis, double radians, Vector3 pivot) {
        if (unitAxis == null || pivot == null || !finite(radians)) throw new IllegalArgumentException("rotation");
        double length = StrictMath.sqrt(dot(unitAxis, unitAxis));
        if (StrictMath.abs(length - 1.0) > 1.0e-9) throw new IllegalArgumentException("unit axis");
        double x = unitAxis.x, y = unitAxis.y, z = unitAxis.z;
        double c = StrictMath.cos(radians), s = StrictMath.sin(radians), t = 1.0 - c;
        double[] matrix = { t*x*x+c, t*x*y-s*z, t*x*z+s*y,
                t*x*y+s*z, t*y*y+c, t*y*z-s*x,
                t*x*z-s*y, t*y*z+s*x, t*z*z+c };
        Vector3 rotatedPivot = multiply(matrix, pivot);
        return new RigidTransform3(matrix, subtract(pivot, rotatedPivot));
    }

    /** Returns this ∘ before: before is applied first, then this transform. */
    public RigidTransform3 after(RigidTransform3 before) {
        if (before == null) throw new IllegalArgumentException("before");
        double[] matrix = multiply(r, before.r);
        return new RigidTransform3(matrix, add(direction(before.translation), translation));
    }

    public Vector3 point(Vector3 value) { return add(direction(value), translation); }
    public Vector3 direction(Vector3 value) { return multiply(r, value); }
    public Vector3 inverseDirection(Vector3 value) {
        return new Vector3(r[0]*value.x+r[3]*value.y+r[6]*value.z,
                r[1]*value.x+r[4]*value.y+r[7]*value.z,
                r[2]*value.x+r[5]*value.y+r[8]*value.z);
    }

    /** OpenGL-compatible column-major homogeneous matrix. */
    public double[] columnMajorMatrix() {
        return new double[] { r[0],r[3],r[6],0.0, r[1],r[4],r[7],0.0,
                r[2],r[5],r[8],0.0, translation.x,translation.y,translation.z,1.0 };
    }

    public static Vector3 add(Vector3 a, Vector3 b) { return new Vector3(a.x+b.x, a.y+b.y, a.z+b.z); }
    public static Vector3 subtract(Vector3 a, Vector3 b) { return new Vector3(a.x-b.x, a.y-b.y, a.z-b.z); }
    public static Vector3 scale(Vector3 a, double value) { return new Vector3(a.x*value, a.y*value, a.z*value); }
    public static double dot(Vector3 a, Vector3 b) { return a.x*b.x+a.y*b.y+a.z*b.z; }
    public static Vector3 cross(Vector3 a, Vector3 b) {
        return new Vector3(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x);
    }

    private static Vector3 multiply(double[] m, Vector3 v) {
        return new Vector3(m[0]*v.x+m[1]*v.y+m[2]*v.z,
                m[3]*v.x+m[4]*v.y+m[5]*v.z, m[6]*v.x+m[7]*v.y+m[8]*v.z);
    }
    private static double[] multiply(double[] a, double[] b) {
        double[] value = new double[9];
        for (int row=0; row<3; row++) for (int column=0; column<3; column++)
            value[row*3+column] = a[row*3]*b[column]
                    + a[row*3+1]*b[3+column] + a[row*3+2]*b[6+column];
        return value;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
