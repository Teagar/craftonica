package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.render.CraftonicaRenderIds;
import br.com.craftonica.tile.TileEntityElectricalWire;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class BlockElectricalWire extends BlockContainer implements IElectricalBlock {
    public BlockElectricalWire() {
        super(Material.circuits);
        setBlockName("electricalWire");
        setBlockTextureName("craftonica:electrical_wire");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(0.2F);
        setStepSound(soundTypeCloth);
    }

    @Override
    public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        if (side < 0 || side >= 6) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        return !(tile instanceof TileEntityElectricalWire)
                || ((TileEntityElectricalWire) tile).canConnect(side);
    }

    @Override public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityElectricalWire();
    }

    public int getConnectionMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (!canConnectOnSide(world, x, y, z, direction.ordinal())) continue;
            Block neighbor = world.getBlock(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
            if (neighbor instanceof IElectricalBlock && ((IElectricalBlock) neighbor).canConnectOnSide(
                    world,
                    x + direction.offsetX,
                    y + direction.offsetY,
                    z + direction.offsetZ,
                    direction.getOpposite().ordinal())) {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int mask = getConnectionMask(world, x, y, z);
        float minX = (mask & 1 << 4) != 0 ? 0.0F : 0.375F;
        float maxX = (mask & 1 << 5) != 0 ? 1.0F : 0.625F;
        float minY = (mask & 1) != 0 ? 0.0F : 0.375F;
        float maxY = (mask & 1 << 1) != 0 ? 1.0F : 0.625F;
        float minZ = (mask & 1 << 2) != 0 ? 0.0F : 0.375F;
        float maxZ = (mask & 1 << 3) != 0 ? 1.0F : 0.625F;
        setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
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

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return WireColor.rgb(world.getBlockMetadata(x, y, z));
    }

    @Override
    public int getRenderColor(int metadata) {
        return WireColor.rgb(metadata);
    }

    @Override
    public int damageDropped(int metadata) {
        return metadata & 15;
    }

    @Override
    public int getDamageValue(World world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) & 15;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() == ModItems.WIRE_ROUTER) return false;
        if (held == null || held.getItem() != Items.dye) {
            return false;
        }
        int color = held.getItemDamage() & 15;
        if (!world.isRemote && world.getBlockMetadata(x, y, z) != color) {
            world.setBlockMetadataWithNotify(x, y, z, color, 3);
            if (!player.capabilities.isCreativeMode) {
                held.stackSize--;
                if (held.stackSize == 0) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
            }
        }
        return true;
    }
}
