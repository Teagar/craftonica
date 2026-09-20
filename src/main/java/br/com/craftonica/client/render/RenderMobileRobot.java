package br.com.craftonica.client.render;

import br.com.craftonica.robot.EntityMobileRobot;
import br.com.craftonica.robot.MobileRobotState;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.GL11;

/** Textureless prototype renderer; the final model and texture are intentionally deferred. */
public final class RenderMobileRobot extends Render {
    private final RobotModel model = new RobotModel();

    public RenderMobileRobot() { shadowSize = 0.9F; }

    @Override public void doRender(Entity entity, double x, double y, double z,
                                   float yaw, float partialTicks) {
        EntityMobileRobot robot = (EntityMobileRobot) entity;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.48D, z);
        GL11.glRotatef(180.0F - yaw, 0.0F, 1.0F, 0.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int status = robot.getVisualStatus();
        if (status == MobileRobotState.Status.FAULT.ordinal()
                || status == MobileRobotState.Status.QUARANTINED.ordinal()) GL11.glColor3f(0.75F, 0.16F, 0.12F);
        else if (status == MobileRobotState.Status.SUSPENDED.ordinal()) GL11.glColor3f(0.45F, 0.45F, 0.45F);
        else GL11.glColor3f(0.10F, 0.45F, 0.58F);
        model.body.render(0.1F);
        GL11.glColor3f(0.08F, 0.08F, 0.08F);
        model.leftWheel.render(0.1F); model.rightWheel.render(0.1F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationBlocksTexture;
    }

    private static final class RobotModel extends ModelBase {
        final ModelRenderer body = new ModelRenderer(this, 0, 0);
        final ModelRenderer leftWheel = new ModelRenderer(this, 0, 0);
        final ModelRenderer rightWheel = new ModelRenderer(this, 0, 0);

        RobotModel() {
            body.addBox(-7.0F, -5.0F, -9.0F, 14, 5, 18);
            leftWheel.addBox(-9.0F, -4.0F, -6.0F, 2, 5, 12);
            rightWheel.addBox(7.0F, -4.0F, -6.0F, 2, 5, 12);
        }
    }
}
