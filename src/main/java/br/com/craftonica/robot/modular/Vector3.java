package br.com.craftonica.robot.modular;

public strictfp final class Vector3 {
    public static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vector3(double x, double y, double z) {
        this.x = ContractValues.finite(x, "x");
        this.y = ContractValues.finite(y, "y");
        this.z = ContractValues.finite(z, "z");
    }
}
