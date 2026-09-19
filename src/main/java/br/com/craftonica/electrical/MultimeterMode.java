package br.com.craftonica.electrical;

public enum MultimeterMode {
    VOLTAGE("voltage"),
    CURRENT("current"),
    RESISTANCE("resistance"),
    CONTINUITY("continuity");

    private final String id;

    MultimeterMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public MultimeterMode next() {
        MultimeterMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public static MultimeterMode fromId(String id) {
        for (MultimeterMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return VOLTAGE;
    }
}
