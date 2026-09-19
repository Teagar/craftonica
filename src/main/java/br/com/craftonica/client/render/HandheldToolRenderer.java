package br.com.craftonica.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

/** Gives flat tool sprites a sturdy handheld body without introducing an external model format. */
public final class HandheldToolRenderer implements IItemRenderer {
    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.EQUIPPED
                || type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY && helper == ItemRendererHelper.ENTITY_BOBBING;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        IIcon icon = stack.getIconIndex();
        if (icon == null) return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);
        GL11.glPushMatrix();
        if (type == ItemRenderType.INVENTORY) {
            GL11.glTranslatef(-0.5F, -0.5F, 0.0F);
        } else if (type == ItemRenderType.ENTITY) {
            GL11.glTranslatef(-0.5F, -0.25F, 0.0F);
            GL11.glScalef(0.75F, 0.75F, 0.75F);
            GL11.glRotatef(20.0F, 0, 1, 0);
        } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            GL11.glTranslatef(0.35F, 0.12F, 0.15F);
            GL11.glRotatef(-25.0F, 1, 0, 0);
            GL11.glRotatef(35.0F, 0, 1, 0);
            GL11.glScalef(0.68F, 0.68F, 0.68F);
        } else {
            GL11.glTranslatef(0.0F, 0.05F, 0.05F);
            GL11.glRotatef(-20.0F, 1, 0, 0);
            GL11.glRotatef(25.0F, 0, 1, 0);
        }
        GL11.glColor4f(1, 1, 1, 1);
        ItemRenderer.renderItemIn2D(Tessellator.instance, icon.getMaxU(), icon.getMinV(),
                icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.125F);
        GL11.glPopMatrix();
    }
}
