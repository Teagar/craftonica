package br.com.craftonica.client;

import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.block.IRotatableElectricalBlock;
import br.com.craftonica.client.render.ElectricalBlockRenderer;
import br.com.craftonica.client.sketch.SketchClientController;
import br.com.craftonica.block.BlockRoboBoard;
import br.com.craftonica.block.BlockMechanicalComponent;
import br.com.craftonica.registry.ModItems;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.opengl.GL11;

public final class ClientEventHandler {
    private final ElectricalBlockRenderer renderer;

    public ClientEventHandler(ElectricalBlockRenderer renderer) {
        this.renderer = renderer;
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.itemStack;
        if (stack == null || !hasEducationalTooltip(stack)) {
            return;
        }
        String prefix = stack.getUnlocalizedName() + ".tooltip.";
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(
                minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int maxWidth = Math.max(80, resolution.getScaledWidth() - 32);
        for (int line = 0; StatCollector.canTranslate(prefix + line); line++) {
            event.toolTip.addAll(minecraft.fontRenderer.listFormattedStringToWidth(
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal(prefix + line), maxWidth));
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && event.world != null
                && event.world.isRemote && event.world.getBlock(event.x, event.y, event.z) instanceof BlockRoboBoard
                && (event.entityPlayer.getHeldItem() == null
                || event.entityPlayer.getHeldItem().getItem() != ModItems.ROBO_PORT_CONFIGURATOR)) {
            SketchClientController.INSTANCE.allowOpen(event.world.provider.dimensionId, event.x, event.y, event.z);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        World world = minecraft.theWorld;
        ItemStack stack = player == null ? null : player.getHeldItem();
        if (world == null || stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        if (!(block instanceof IRotatableElectricalBlock)) {
            return;
        }
        PlacementTarget target = getPlacementTarget(world, player, stack, block, minecraft.objectMouseOver);
        if (target == null) {
            return;
        }

        ItemBlock item = (ItemBlock) stack.getItem();
        int metadata = item.getMetadata(stack.getItemDamage());
        metadata = block.onBlockPlaced(world, target.x, target.y, target.z, target.side,
                target.hitX, target.hitY, target.hitZ, metadata);
        metadata = ((IRotatableElectricalBlock) block).getPlacementMetadata(player.rotationYaw, metadata);
        renderPreview(minecraft, event.partialTicks, target, block, metadata);
    }

    private boolean hasEducationalTooltip(ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.getItem());
        return block instanceof IElectricalBlock
                || block instanceof BlockMechanicalComponent
                || block instanceof BlockRoboBoard
                || stack.getItem() == ModItems.MULTIMETER
                || stack.getItem() == ModItems.WRENCH
                || stack.getItem() == ModItems.ROBO_PORT_CONFIGURATOR
                || stack.getItem() == ModItems.WIRE_ROUTER
                || stack.getItem() == ModItems.MANUAL;
    }

    private PlacementTarget getPlacementTarget(World world, EntityPlayer player, ItemStack stack, Block block,
                                               MovingObjectPosition hit) {
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        int side = hit.sideHit;
        Block clicked = world.getBlock(x, y, z);

        if (clicked == Blocks.snow_layer && (world.getBlockMetadata(x, y, z) & 7) < 1) {
            side = 1;
        } else if (clicked != Blocks.vine && clicked != Blocks.tallgrass && clicked != Blocks.deadbush
                && !clicked.isReplaceable(world, x, y, z)) {
            if (side == 0) {
                y--;
            } else if (side == 1) {
                y++;
            } else if (side == 2) {
                z--;
            } else if (side == 3) {
                z++;
            } else if (side == 4) {
                x--;
            } else if (side == 5) {
                x++;
            }
        }

        if (stack.stackSize == 0 || !player.canPlayerEdit(x, y, z, side, stack)
                || y == 255 && block.getMaterial().isSolid()
                || !world.canPlaceEntityOnSide(block, x, y, z, false, side, player, stack)) {
            return null;
        }
        return new PlacementTarget(x, y, z, side,
                (float) hit.hitVec.xCoord - hit.blockX,
                (float) hit.hitVec.yCoord - hit.blockY,
                (float) hit.hitVec.zCoord - hit.blockZ);
    }

    private void renderPreview(Minecraft minecraft, float partialTicks, PlacementTarget target,
                               Block block, int metadata) {
        Entity view = minecraft.renderViewEntity;
        double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(target.x - cameraX, target.y - cameraY, target.z - cameraZ);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            renderer.renderPlacementPreview(block, metadata);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static final class PlacementTarget {
        private final int x;
        private final int y;
        private final int z;
        private final int side;
        private final float hitX;
        private final float hitY;
        private final float hitZ;

        private PlacementTarget(int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
    }
}
