package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.render.CraftonicaRenderIds;
import br.com.craftonica.tile.TileEntityLed;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public final class BlockLed extends BlockContainer implements IElectricalBlock {
    private IIcon sideIcon;
    private IIcon onIcon;
    private IIcon burnedIcon;
    private IIcon anodeIcon;
    private IIcon cathodeIcon;

    public BlockLed() {
        super(Material.iron);
        setBlockName("led");
        setBlockTextureName("craftonica:led_off");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.0F);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        sideIcon = register.registerIcon("craftonica:led_off");
        onIcon = register.registerIcon("craftonica:led_on");
        burnedIcon = register.registerIcon("craftonica:led_burned");
        anodeIcon = register.registerIcon("craftonica:led_anode");
        cathodeIcon = register.registerIcon("craftonica:led_cathode");
        blockIcon = sideIcon;
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        int anode = metadata & 7;
        return side == anode ? anodeIcon : side == opposite(anode) ? cathodeIcon : sideIcon;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int anode = world.getBlockMetadata(x, y, z) & 7;
        if (side == anode) {
            return anodeIcon;
        }
        if (side == opposite(anode)) {
            return cathodeIcon;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityLed) {
            TileEntityLed led = (TileEntityLed) tile;
            return led.isBurned() ? burnedIcon : led.getBrightness() > 0 ? onIcon : sideIcon;
        }
        return sideIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityLed();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int anodeSide = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
        world.setBlockMetadataWithNotify(x, y, z, anodeSide, 2);
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
        int anode = getAnodeSide(world, x, y, z);
        if (anode == 2 || anode == 3) {
            setBlockBounds(0.3125F, 0.3125F, 0.0F, 0.6875F, 0.9375F, 1.0F);
        } else {
            setBlockBounds(0.0F, 0.3125F, 0.3125F, 1.0F, 0.9375F, 0.6875F);
        }
    }

    public IIcon getBodyIcon(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityLed) {
            TileEntityLed led = (TileEntityLed) tile;
            return led.isBurned() ? burnedIcon : led.getBrightness() > 0 ? onIcon : sideIcon;
        }
        return sideIcon;
    }

    public IIcon getBodyIcon() {
        return sideIcon;
    }

    public IIcon getAnodeIcon() {
        return anodeIcon;
    }

    public IIcon getCathodeIcon() {
        return cathodeIcon;
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

    @Override
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityLed && ((TileEntityLed) tile).getBrightness() == 15
                && random.nextInt(4) == 0) {
            world.spawnParticle("smoke", x + 0.5, y + 0.8, z + 0.5, 0.0, 0.02, 0.0);
        }
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
    public int getRenderType() {
        return CraftonicaRenderIds.ELECTRICAL_COMPONENT;
    }
}
