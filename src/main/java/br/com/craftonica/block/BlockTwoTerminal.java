package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.render.CraftonicaRenderIds;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;

public class BlockTwoTerminal extends Block implements IElectricalBlock, IRotatableElectricalBlock {
    protected IIcon bodyIcon;
    protected IIcon terminalIcon;

    protected BlockTwoTerminal(String name, String texture) {
        super(Material.iron);
        setBlockName(name);
        setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.0F);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        bodyIcon = register.registerIcon(getTextureName());
        terminalIcon = register.registerIcon("craftonica:terminal_neutral");
        blockIcon = bodyIcon;
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        int axis = metadata & 1;
        boolean terminal = axis == 0 ? side == 2 || side == 3 : side == 4 || side == 5;
        return terminal ? terminalIcon : bodyIcon;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int metadata = world.getBlockMetadata(x, y, z);
        world.setBlockMetadataWithNotify(x, y, z, getPlacementMetadata(placer.rotationYaw, metadata), 2);
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int axis = world.getBlockMetadata(x, y, z) & 1;
        return axis == 0 ? side == 2 || side == 3 : side == 4 || side == 5;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if ((world.getBlockMetadata(x, y, z) & 1) == 0) {
            setBlockBounds(0.1875F, 0.1875F, 0.0F, 0.8125F, 0.875F, 1.0F);
        } else {
            setBlockBounds(0.0F, 0.1875F, 0.1875F, 1.0F, 0.875F, 0.8125F);
        }
    }

    public IIcon getBodyIcon(int metadata) {
        return bodyIcon;
    }

    public IIcon getTerminalIcon() {
        return terminalIcon;
    }

    @Override
    public int rotateMetadata(int metadata) {
        return HorizontalRotation.rotateAxisMetadata(metadata);
    }

    @Override
    public int getPlacementMetadata(float rotationYaw, int metadata) {
        return HorizontalRotation.placementAxisMetadata(rotationYaw, metadata);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return CraftonicaRenderIds.ELECTRICAL_COMPONENT;
    }
}
