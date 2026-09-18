package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

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
}
