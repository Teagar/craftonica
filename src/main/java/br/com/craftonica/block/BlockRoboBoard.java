package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.Craftonica;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import br.com.craftonica.sketch.server.SketchServer;

/** Programmable board shell. Electrical terminals are intentionally deferred to CRL-32. */
public final class BlockRoboBoard extends BlockContainer {
    public BlockRoboBoard() {
        super(Material.iron);
        setBlockName("roboBoard");
        setBlockTextureName("craftonica:roboboard");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(1.5F);
        setResistance(6.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityRoboBoard();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            Craftonica.proxy.prepareEditorOpen(world.provider.dimensionId, x, y, z);
            return true;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (player instanceof EntityPlayerMP && tile instanceof TileEntityRoboBoard)
            SketchServer.openEditor((EntityPlayerMP) player, (TileEntityRoboBoard) tile);
        return true;
    }
}
