package br.com.craftonica.sensor;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;

/** Empirical response classes. Values describe tendencies, not prerecorded measurements. */
public enum AcousticMaterialProfile {
    MDF("mdf", 0.10, 0.11, 0.995, 0x8B5A32),
    RIGID_PLASTIC("plastic", 0.25, 0.11, 0.990, 0xD8D8D2),
    STYROFOAM("styrofoam", 0.88, 0.32, 0.900, 0xF3F5E8),
    FOAM("foam", 3.92, 2.05, 0.620, 0x6B4B7A),
    RIGID_WORLD("rigid", 0.35, 0.18, 0.970, 0x929292),
    ABSORBENT_WORLD("absorbent", 2.50, 1.25, 0.720, 0x82748A);

    private final String id;
    private final double biasAt50Cm;
    private final double sigmaAt50Cm;
    private final double reflectivity;
    private final int color;

    AcousticMaterialProfile(String id, double biasAt50Cm, double sigmaAt50Cm,
                            double reflectivity, int color) {
        this.id = id;
        this.biasAt50Cm = biasAt50Cm;
        this.sigmaAt50Cm = sigmaAt50Cm;
        this.reflectivity = reflectivity;
        this.color = color;
    }

    public String getId() { return id; }
    public double getBiasAt50Cm() { return biasAt50Cm; }
    public double getSigmaAt50Cm() { return sigmaAt50Cm; }
    public double getReflectivity() { return reflectivity; }
    public int getColor() { return color; }

    public static AcousticMaterialProfile forBlock(Block block) {
        if (block == null) return RIGID_WORLD;
        if (block == Blocks.wool || block == Blocks.sponge || block == Blocks.carpet
                || block.getMaterial() == Material.cloth) return ABSORBENT_WORLD;
        if (block == Blocks.planks || block == Blocks.log || block == Blocks.log2
                || block.getMaterial() == Material.wood) return MDF;
        if (block == Blocks.glass || block == Blocks.stained_glass) return RIGID_PLASTIC;
        return RIGID_WORLD;
    }
}
