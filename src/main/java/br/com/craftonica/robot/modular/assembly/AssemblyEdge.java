package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.GridVector;

public final class AssemblyEdge {
    public enum Kind { STRUCTURAL, MECHANICAL }

    public final Kind kind;
    public final GridVector firstPosition;
    public final String firstPort;
    public final GridVector secondPosition;
    public final String secondPort;

    AssemblyEdge(Kind kind, GridVector firstPosition, String firstPort,
                 GridVector secondPosition, String secondPort) {
        this.kind = kind;
        this.firstPosition = firstPosition;
        this.firstPort = firstPort;
        this.secondPosition = secondPosition;
        this.secondPort = secondPort;
    }

    String canonicalKey() {
        String first = firstPosition + "/" + firstPort;
        String second = secondPosition + "/" + secondPort;
        return kind.name() + ":" + (first.compareTo(second) <= 0
                ? first + "=" + second : second + "=" + first);
    }
}
