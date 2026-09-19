package br.com.craftonica.block;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.network.ElectricalFeedback;
import br.com.craftonica.registry.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

public final class BlockElectricalButton extends BlockTwoTerminal {
    private static final int CLOSED_BIT = 2;
    private IIcon closedIcon;

    public BlockElectricalButton() {
        super("electricalButton", "craftonica:button_open");
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        closedIcon = register.registerIcon("craftonica:button_closed");
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        IIcon icon = super.getIcon(side, metadata);
        return icon == bodyIcon && (metadata & CLOSED_BIT) != 0 ? closedIcon : icon;
    }

    @Override
    public IIcon getBodyIcon(int metadata) {
        return (metadata & CLOSED_BIT) != 0 ? closedIcon : bodyIcon;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (player.getCurrentEquippedItem() != null) {
            if (player.getCurrentEquippedItem().getItem() == ModItems.MULTIMETER
                    || player.getCurrentEquippedItem().getItem() == ModItems.WRENCH) {
                return false;
            }
        }
        if (!world.isRemote) {
            boolean closed = (world.getBlockMetadata(x, y, z) & CLOSED_BIT) == 0;
            world.setBlockMetadataWithNotify(x, y, z, world.getBlockMetadata(x, y, z) ^ CLOSED_BIT, 3);
            ElectricalFeedback.switched(world, new BlockPosition(x, y, z), closed);
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
        }
        return true;
    }

    public boolean isClosed(World world, int x, int y, int z) {
        return (world.getBlockMetadata(x, y, z) & CLOSED_BIT) != 0;
    }
}
