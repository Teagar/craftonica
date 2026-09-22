package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Physical axle, bearing, spur gear, conventional wheel or passive caster. */
public final class BlockMechanicalComponent extends Block implements IRotatableElectricalBlock {
    public enum Type { AXLE, BEARING, GEAR, SERVO, WHEEL, CASTER, TRACK }
    private final Type type;

    public BlockMechanicalComponent(Type type, String name, String texture) {
        super(type == Type.WHEEL || type == Type.TRACK ? Material.cloth : Material.iron);
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type; setBlockName(name); setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.4F); setResistance(4.0F);
    }
    public Type getType() { return type; }
    @Override public void onBlockPlacedBy(World world, int x, int y, int z,
            EntityLivingBase placer, ItemStack stack) {
        if (type != Type.CASTER) world.setBlockMetadataWithNotify(x, y, z,
                HorizontalRotation.placementSideMetadata(placer.rotationYaw, 0), 2);
    }
    @Override public int rotateMetadata(int metadata) {
        return type == Type.CASTER ? metadata : HorizontalRotation.rotateSideMetadata(metadata);
    }
    @Override public int getPlacementMetadata(float yaw, int metadata) {
        return type == Type.CASTER ? metadata : HorizontalRotation.placementSideMetadata(yaw, metadata);
    }
}
