package br.com.craftonica.robot.modular;

/** Educational mechanical material profiles, not laboratory material data. */
public enum ComponentMaterial {
    STEEL(0.65), ALUMINUM(0.55), ENGINEERING_PLASTIC(0.45), RUBBER(0.90), COPPER(0.50);

    public final double nominalFriction;

    ComponentMaterial(double nominalFriction) {
        this.nominalFriction = ContractValues.positive(nominalFriction, "nominalFriction");
    }
}
