package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.network.ElectricalFeedback;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Two-terminal rearmable protection branch. Its open state is not redstone state. */
public final class BlockCircuitBreaker extends BlockContainer implements IElectricalBlock, IRotatableElectricalBlock {
    private IIcon bodyIcon;
    private IIcon terminalIcon;
    public BlockCircuitBreaker() { super(Material.iron); setBlockName("circuitBreaker"); setBlockTextureName("craftonica:power_source"); setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.2F); }
    @Override public void registerBlockIcons(IIconRegister r) { bodyIcon=r.registerIcon("craftonica:power_source"); terminalIcon=r.registerIcon("craftonica:terminal_neutral"); blockIcon=bodyIcon; }
    @Override public IIcon getIcon(int side,int metadata) { int axis=metadata&1; return axis==0 ? (side==2||side==3?terminalIcon:bodyIcon) : (side==4||side==5?terminalIcon:bodyIcon); }
    @Override public TileEntity createNewTileEntity(World world,int metadata) { return new TileEntityCircuitBreaker(); }
    @Override public boolean canConnectOnSide(IBlockAccess w,int x,int y,int z,int side) { return (w.getBlockMetadata(x,y,z)&1)==0 ? side==2||side==3 : side==4||side==5; }
    @Override public void setBlockBoundsBasedOnState(IBlockAccess w,int x,int y,int z) { if((w.getBlockMetadata(x,y,z)&1)==0)setBlockBounds(.1875F,.1875F,0,.8125F,.875F,1);else setBlockBounds(0,.1875F,.1875F,1,.875F,.8125F); }
    @Override public void onBlockPlacedBy(World w,int x,int y,int z,net.minecraft.entity.EntityLivingBase p,ItemStack s){w.setBlockMetadataWithNotify(x,y,z,getPlacementMetadata(p.rotationYaw,w.getBlockMetadata(x,y,z)),2);}
    @Override public boolean onBlockActivated(World w,int x,int y,int z,EntityPlayer p,int side,float hx,float hy,float hz){
        if (w.isRemote) return true;
        TileEntity tile=w.getTileEntity(x,y,z);
        if(tile instanceof TileEntityCircuitBreaker && ((TileEntityCircuitBreaker)tile).isTripped()) {
            if (((TileEntityCircuitBreaker)tile).rearm())
                ElectricalFeedback.switched(w, new BlockPosition(x, y, z), true);
        }
        return true;
    }
    @Override public int rotateMetadata(int metadata){return HorizontalRotation.rotateAxisMetadata(metadata);}
    @Override public int getPlacementMetadata(float yaw,int metadata){return HorizontalRotation.placementAxisMetadata(yaw,metadata);}
    @Override public boolean isOpaqueCube(){return false;}
    @Override public boolean renderAsNormalBlock(){return false;}
    @Override public int getRenderType(){return br.com.craftonica.render.CraftonicaRenderIds.ELECTRICAL_COMPONENT;}
}
