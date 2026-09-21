package br.com.craftonica.client.render;

import br.com.craftonica.registry.ModBlocks;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Temporary visible anchor marker; CRL-81 will render every manifest module. */
public final class RenderModularRobot extends Render {
    private static final ResourceLocation BLOCKS = new ResourceLocation("textures/atlas/blocks.png");
    private final RenderBlocks blocks = new RenderBlocks();

    public RenderModularRobot() { shadowSize = 0.5F; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        GL11.glPushMatrix(); GL11.glTranslated(x, y + 0.5, z); bindTexture(BLOCKS);
        blocks.renderBlockAsItem(ModBlocks.ROBOT_CHASSIS, 3, 1.0F); GL11.glPopMatrix();
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return BLOCKS; }
}
