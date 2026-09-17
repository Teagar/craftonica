package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.render.CraftonicaRenderIds;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;

public class BlockSingleTerminal extends Block implements IElectricalBlock, IRotatableElectricalBlock {
    private IIcon bodyIcon;
    private IIcon terminalIcon;

    protected BlockSingleTerminal(String name, String texture) {
        super(Material.iron);
        setBlockName(name);
        setBlockTextureName(texture);
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.5F);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        bodyIcon = register.registerIcon(getTextureName());
        terminalIcon = register.registerIcon(getTerminalTexture());
        blockIcon = bodyIcon;
    }

    protected String getTerminalTexture() {
        return "craftonica:terminal_positive";
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        return side == (metadata & 7) ? terminalIcon : bodyIcon;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int side = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
        world.setBlockMetadataWithNotify(x, y, z, side, 2);
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        return (world.getBlockMetadata(x, y, z) & 7) == side;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int side = world.getBlockMetadata(x, y, z) & 7;
        setBlockBounds(
                side == 4 ? 0.0F : 0.125F,
                0.125F,
                side == 2 ? 0.0F : 0.125F,
                side == 5 ? 1.0F : 0.875F,
                0.875F,
                side == 3 ? 1.0F : 0.875F);
    }

    public IIcon getBodyIcon() {
        return bodyIcon;
    }

    public IIcon getTerminalIcon() {
        return terminalIcon;
    }

    @Override
    public int rotateMetadata(int metadata) {
        return HorizontalRotation.rotateSideMetadata(metadata);
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
