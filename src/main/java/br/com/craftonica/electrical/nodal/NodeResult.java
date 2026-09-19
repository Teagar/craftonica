package br.com.craftonica.electrical.nodal;

public final class NodeResult {
    private final NodeId node; private final double voltage; private final ValueValidity validity;
    public NodeResult(NodeId node, double voltage, ValueValidity validity) {
        this.node=node; this.voltage=voltage; this.validity=validity;
    }
    public NodeId getNode(){return node;} public double getVoltage(){return voltage;} public ValueValidity getValidity(){return validity;}
}
