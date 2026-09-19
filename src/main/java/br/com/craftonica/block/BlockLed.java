package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.render.CraftonicaRenderIds;
import br.com.craftonica.tile.TileEntityLed;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.ArrayList;

public final class BlockLed extends BlockContainer implements IElectricalBlock, IRotatableElectricalBlock {
    private IIcon glassIcon;
    private IIcon coreOffIcon;
    private IIcon coreOnIcon;
    private IIcon coreBurnedIcon;
    private IIcon anodeIcon;
    private IIcon cathodeIcon;

    public BlockLed() {
        super(Material.glass);
        setBlockName("led");
        setBlockTextureName("craftonica:led_glass");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.0F);
        setStepSound(soundTypeGlass);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        glassIcon = register.registerIcon("craftonica:led_glass");
        coreOffIcon = register.registerIcon("craftonica:led_core_off");
        coreOnIcon = register.registerIcon("craftonica:led_core_on");
        coreBurnedIcon = register.registerIcon("craftonica:led_core_burned");
        anodeIcon = register.registerIcon("craftonica:led_anode");
        cathodeIcon = register.registerIcon("craftonica:led_cathode");
        blockIcon = glassIcon;
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        int anode = metadata & 7;
        return side == anode ? anodeIcon : side == opposite(anode) ? cathodeIcon : glassIcon;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int anode = world.getBlockMetadata(x, y, z) & 7;
        return side == anode ? anodeIcon : side == opposite(anode) ? cathodeIcon : glassIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityLed();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int metadata = world.getBlockMetadata(x, y, z);
        world.setBlockMetadataWithNotify(x, y, z, getPlacementMetadata(placer.rotationYaw, metadata), 2);
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int anodeSide = getAnodeSide(world, x, y, z);
        return side == anodeSide || side == opposite(anodeSide);
    }

    public int getAnodeSide(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) & 7;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    public IIcon getBodyIcon(IBlockAccess world, int x, int y, int z) {
        return glassIcon;
    }

    public IIcon getBodyIcon() {
        return glassIcon;
    }

    public IIcon getCoreIcon(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world == null ? null : world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityLed)) return coreOffIcon;
        TileEntityLed led = (TileEntityLed) tile;
        return led.isBurned() ? coreBurnedIcon : led.getBrightness() > 0 ? coreOnIcon : coreOffIcon;
    }

    public IIcon getAnodeIcon() {
        return anodeIcon;
    }

    public IIcon getCathodeIcon() {
        return cathodeIcon;
    }

    @Override
    public int rotateMetadata(int metadata) {
        return HorizontalRotation.rotateSideMetadata(metadata);
    }

    @Override
    public int getPlacementMetadata(float rotationYaw, int metadata) {
        return HorizontalRotation.placementSideMetadata(rotationYaw, metadata);
    }

    public boolean isBurned(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityLed && ((TileEntityLed) tile).isBurned();
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityLed ? ((TileEntityLed) tile).getBrightness() : 0;
    }

    public int getVisualColor(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityLed)) return WireColor.rgb(1);
        TileEntityLed led = (TileEntityLed) tile;
        if (led.isBurned()) return 0x454545;
        int rgb = WireColor.rgb(led.getColor());
        return led.getBrightness() > 0 ? rgb : inactiveColor(rgb);
    }

    public int getCoreColor(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world == null ? null : world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityLed)) return 0x404040;
        TileEntityLed led = (TileEntityLed) tile;
        if (led.isBurned()) return 0x242424;
        int rgb = WireColor.rgb(led.getColor());
        return led.getBrightness() > 0 ? rgb : inactiveCoreColor(rgb);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity tile = world.getTileEntity(x, y, z);
        int color = tile instanceof TileEntityLed ? ((TileEntityLed) tile).getColor() : 1;
        drops.add(new ItemStack(this, 1, color));
        return drops;
    }

    public static int inactiveColor(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r * 0.35F + 170 * 0.65F);
        g = (int) (g * 0.35F + 170 * 0.65F);
        b = (int) (b * 0.35F + 170 * 0.65F);
        return r << 16 | g << 8 | b;
    }

    public static int inactiveCoreColor(int rgb) {
        int r = 36 + (int) (((rgb >> 16) & 255) * 0.30F);
        int g = 36 + (int) (((rgb >> 8) & 255) * 0.30F);
        int b = 36 + (int) ((rgb & 255) * 0.30F);
        return r << 16 | g << 8 | b;
    }

    private int opposite(int side) {
        return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4;
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
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public int getRenderType() {
        return CraftonicaRenderIds.ELECTRICAL_COMPONENT;
    }
}
