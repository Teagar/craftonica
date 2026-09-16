package br.com.craftonica.electrical;

public interface ElectricalComponent {
    String getId();

    ComponentKind getKind();

    double getResistanceOhms();

    double getSourceVoltage();

    double getForwardVoltage();

    boolean isClosed();

    String getAnodeNeighborId();
}
