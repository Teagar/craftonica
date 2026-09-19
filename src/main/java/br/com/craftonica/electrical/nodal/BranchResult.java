package br.com.craftonica.electrical.nodal;

public final class BranchResult {
    private final BranchId branch; private final double voltage, current, absorbedPower; private final ValueValidity validity;
    public BranchResult(BranchId branch,double voltage,double current,double absorbedPower,ValueValidity validity){
        this.branch=branch;this.voltage=voltage;this.current=current;this.absorbedPower=absorbedPower;this.validity=validity;
    }
    public BranchId getBranch(){return branch;} public double getVoltage(){return voltage;} public double getCurrent(){return current;}
    public double getAbsorbedPower(){return absorbedPower;} public ValueValidity getValidity(){return validity;}
}
