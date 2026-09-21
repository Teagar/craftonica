package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.render.CraftonicaRenderIds;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Physical chassis or dual H-bridge module used by the bounded robot assembly. */
public class BlockRobotModule extends Block implements IRotatableElectricalBlock {
    public enum Type { CHASSIS, H_BRIDGE }
    private final Type type;
    public BlockRobotModule(Type type, String name, String texture) {
        super(Material.iron); this.type = type; setBlockName(name); setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(2.0F); setResistance(6.0F);
    }
    public Type getType() { return type; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int getRenderType() { return CraftonicaRenderIds.ELECTRICAL_COMPONENT; }
    @Override public int rotateMetadata(int metadata) { return HorizontalRotation.rotateSideMetadata(metadata); }
    @Override public int getPlacementMetadata(float rotationYaw, int metadata) {
        return HorizontalRotation.placementSideMetadata(rotationYaw, metadata);
    }
    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z,
                HorizontalRotation.placementSideMetadata(placer.rotationYaw, 0), 2);
    }
}
