package br.com.craftonica.block;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class BlockElectricalButton extends BlockTwoTerminal {
    private static final int CLOSED_BIT = 2;

    public BlockElectricalButton() {
        super("electricalButton", "minecraft:stone");
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            world.setBlockMetadataWithNotify(x, y, z, world.getBlockMetadata(x, y, z) ^ CLOSED_BIT, 3);
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        }
        return true;
    }

    public boolean isClosed(World world, int x, int y, int z) {
        return (world.getBlockMetadata(x, y, z) & CLOSED_BIT) != 0;
    }
}
