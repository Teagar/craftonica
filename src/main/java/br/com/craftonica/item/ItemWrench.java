package br.com.craftonica.item;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.block.IRotatableElectricalBlock;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.block.BlockRobotModule;
import br.com.craftonica.robot.modular.transaction.forge.ForgeModularAssemblyService;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class ItemWrench extends Item {
    public ItemWrench() {
        setUnlocalizedName("wrench");
        setTextureName("craftonica:wrench");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setMaxStackSize(1);
        setMaxDamage(256);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        Block block = world.getBlock(x, y, z);
        if (block instanceof BlockRobotModule
                && ((BlockRobotModule) block).getType() == BlockRobotModule.Type.CHASSIS
                && !player.isSneaking()) {
            if (!world.isRemote && ForgeModularAssemblyService.assemble(world, x, y, z, player))
                stack.damageItem(1, player);
            return true;
        }
        if (!(block instanceof IRotatableElectricalBlock)) {
            return false;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int rotated = ((IRotatableElectricalBlock) block).rotateMetadata(metadata);
        if (rotated == metadata) {
            return false;
        }
        if (!world.isRemote) {
            world.setBlockMetadataWithNotify(x, y, z, rotated, 3);
            ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
            stack.damageItem(1, player);
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.anvil_use", 0.35F, 1.6F);
        }
        return true;
    }
}
