package br.com.craftonica.client.render;

import br.com.craftonica.robot.EntityMobileRobot;
import br.com.craftonica.robot.MobileRobotState;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Textured teaching model; simulation state remains entirely server-authoritative. */
public final class RenderMobileRobot extends Render {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("craftonica", "textures/entity/mobile_robot.png");
    private final RobotModel model = new RobotModel();

    public RenderMobileRobot() { shadowSize = 0.9F; }

    @Override public void doRender(Entity entity, double x, double y, double z,
                                   float yaw, float partialTicks) {
        EntityMobileRobot robot = (EntityMobileRobot) entity;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.48D, z);
        GL11.glRotatef(180.0F - yaw, 0.0F, 1.0F, 0.0F);
        bindTexture(TEXTURE);
        int status = robot.getVisualStatus();
        if (status == MobileRobotState.Status.FAULT.ordinal()
                || status == MobileRobotState.Status.QUARANTINED.ordinal()) GL11.glColor3f(1.0F, 0.58F, 0.52F);
        else if (status == MobileRobotState.Status.SUSPENDED.ordinal()) GL11.glColor3f(0.62F, 0.62F, 0.62F);
        else GL11.glColor3f(1.0F, 1.0F, 1.0F);
        model.render(0.1F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) {
        return TEXTURE;
    }

    private static final class RobotModel extends ModelBase {
        final ModelRenderer body, deck, leftWheel, rightWheel, leftMotor, rightMotor;
        final ModelRenderer board, bridge, sonarBoard, sonarLeft, sonarRight, bumper;

        RobotModel() {
            textureWidth = 64; textureHeight = 64;
            body = box(0, 0, -7, -5, -9, 14, 4, 18);
            deck = box(0, 23, -6, -7, -7, 12, 2, 14);
            leftWheel = box(52, 0, -9, -5, -6, 2, 6, 12);
            rightWheel = box(52, 0, 7, -5, -6, 2, 6, 12);
            leftMotor = box(0, 39, -7, -5, -5, 3, 3, 10);
            rightMotor = box(0, 39, 4, -5, -5, 3, 3, 10);
            board = box(26, 23, -4, -11, -4, 8, 4, 8);
            bridge = box(36, 39, -5, -10, 3, 10, 3, 5);
            sonarBoard = box(0, 52, -5, -8, -10, 10, 5, 1);
            sonarLeft = box(24, 52, -4, -7, -12, 3, 4, 3);
            sonarRight = box(24, 52, 1, -7, -12, 3, 4, 3);
            bumper = box(36, 48, -6, -3, -11, 12, 2, 2);
        }

        private ModelRenderer box(int u, int v, float x, float y, float z, int sx, int sy, int sz) {
            ModelRenderer value = new ModelRenderer(this, u, v);
            value.addBox(x, y, z, sx, sy, sz);
            return value;
        }

        void render(float scale) {
            body.render(scale); deck.render(scale); leftWheel.render(scale); rightWheel.render(scale);
            leftMotor.render(scale); rightMotor.render(scale); board.render(scale); bridge.render(scale);
            sonarBoard.render(scale); sonarLeft.render(scale); sonarRight.render(scale); bumper.render(scale);
        }
    }
}
