package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

/** Six-axis placeable one-degree-of-freedom joint with integrated hard stops. */
public final class BlockJointComponent extends Block implements IRotatableElectricalBlock {
    public enum Type { REVOLUTE, PRISMATIC }
    private final Type type;

    public BlockJointComponent(Type type, String name, String texture) {
        super(Material.iron);
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type; setBlockName(name); setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.8F); setResistance(5.0F);
    }

    public Type getType() { return type; }

    @Override public int onBlockPlaced(World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ, int metadata) {
        return side >= 0 && side <= 5 ? side : 2;
    }

    @Override public int rotateMetadata(int metadata) {
        switch (metadata & 7) {
            case 2: return 5;
            case 5: return 3;
            case 3: return 4;
            case 4: return 2;
            default: return metadata & 7;
        }
    }

    @Override public int getPlacementMetadata(float yaw, int metadata) { return metadata & 7; }
}
