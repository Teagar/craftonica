package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Modular motor: front positive, top negative, rear shaft and bottom rigid mount. */
public final class BlockModularDcMotor extends Block implements IElectricalBlock, IRotatableElectricalBlock {
    private IIcon body, positive, negative, shaft;
    public BlockModularDcMotor() {
        super(Material.iron);
        setBlockName("modularDcMotor"); setBlockTextureName("craftonica:dc_motor");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.5F); setResistance(5.0F);
    }

    @Override public void registerBlockIcons(IIconRegister register) {
        body = register.registerIcon("craftonica:dc_motor");
        positive = register.registerIcon("craftonica:terminal_positive");
        negative = register.registerIcon("craftonica:terminal_neutral");
        shaft = register.registerIcon("craftonica:dc_motor_active");
        blockIcon = body;
    }
    @Override public IIcon getIcon(int side, int metadata) {
        int front = normalizeFront(metadata & 7);
        if (side == front) return positive;
        if (side == 1) return negative;
        if (side == opposite(front)) return shaft;
        return body;
    }

    @Override public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        return side == 1 || side == normalizeFront(world.getBlockMetadata(x, y, z) & 7);
    }
    @Override public void onBlockPlacedBy(World world, int x, int y, int z,
                                           EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z,
                HorizontalRotation.placementSideMetadata(placer.rotationYaw, 0), 2);
    }
    @Override public int rotateMetadata(int metadata) { return HorizontalRotation.rotateSideMetadata(metadata); }
    @Override public int getPlacementMetadata(float yaw, int metadata) {
        return HorizontalRotation.placementSideMetadata(yaw, metadata);
    }
    private static int normalizeFront(int side) { return side >= 2 && side <= 5 ? side : 3; }
    private static int opposite(int side) { return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
}
