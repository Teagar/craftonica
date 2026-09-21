package br.com.craftonica.robot.modular;

final class ContractValues {
    private ContractValues() { }

    static String id(String value, String name) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException(name);
        return value;
    }

    static String token(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}"))
            throw new IllegalArgumentException(name);
        return value;
    }

    static double finite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name);
        return value;
    }

    static double positive(double value, String name) {
        finite(value, name);
        if (value <= 0.0) throw new IllegalArgumentException(name);
        return value;
    }

    static double nonNegative(double value, String name) {
        finite(value, name);
        if (value < 0.0) throw new IllegalArgumentException(name);
        return value;
    }
}
