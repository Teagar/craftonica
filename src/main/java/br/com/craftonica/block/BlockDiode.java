package br.com.craftonica.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/** Polarized two-terminal DC diode; its electrical model lives in electrical.nodal. */
public final class BlockDiode extends BlockTwoTerminal {
    private IIcon anodeIcon;
    private IIcon cathodeIcon;

    public BlockDiode() { super("diode", "craftonica:diode_off"); }

    @Override public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        anodeIcon = register.registerIcon("craftonica:diode_anode");
        cathodeIcon = register.registerIcon("craftonica:diode_cathode");
    }

    public int getAnodeSide(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) & 7;
    }

    @Override public IIcon getIcon(int side, int metadata) {
        int anode = metadata & 7;
        return side == anode ? anodeIcon : side == opposite(anode) ? cathodeIcon : bodyIcon;
    }

    @Override public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return getIcon(side, world.getBlockMetadata(x, y, z));
    }

    @Override public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int anode = getAnodeSide(world, x, y, z);
        return side == anode || side == opposite(anode);
    }

    @Override public int rotateMetadata(int metadata) { return HorizontalRotation.rotateSideMetadata(metadata); }
    @Override public int getPlacementMetadata(float yaw, int metadata) { return HorizontalRotation.placementSideMetadata(yaw, metadata); }
    public IIcon getAnodeIcon() { return anodeIcon; }
    public IIcon getCathodeIcon() { return cathodeIcon; }

    private int opposite(int side) { return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
}
