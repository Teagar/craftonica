package br.com.craftonica.client.render;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.EntityModularRobot;
import br.com.craftonica.robot.modular.ModularRobotState;
import br.com.craftonica.robot.modular.visual.ModularRobotVisualState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.util.List;
import br.com.craftonica.robot.modular.physics.articulated.RigidTransform3;

/** Renders only the bounded visual projection supplied by the authoritative server. */
public final class RenderModularRobot extends Render {
    private static final ResourceLocation BLOCKS = new ResourceLocation("textures/atlas/blocks.png");
    private final RenderBlocks blocks = new RenderBlocks();
    private final FloatBuffer orientationMatrix = BufferUtils.createFloatBuffer(16);
    private final DoubleBuffer bodyMatrix = BufferUtils.createDoubleBuffer(16);

    public RenderModularRobot() { shadowSize = 0.7F; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityModularRobot robot = (EntityModularRobot) entity;
        ModularRobotVisualState visual = robot.getVisualState();
        if (visual == null) return;
        List<RigidTransform3> bodyTransforms;
        try { bodyTransforms = visual.bodyTransforms(robot.getJointStates()); }
        catch (RuntimeException invalid) { return; }
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z); GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);
        bindTexture(BLOCKS); tint(robot);
        for (ModularRobotVisualState.Module module : visual.getModules()) {
            Object found = Block.blockRegistry.getObject(module.blockRegistryName);
            if (!(found instanceof Block)) continue;
            GL11.glPushMatrix();
            apply(bodyTransforms.get(module.bodyId));
            GL11.glTranslated(module.localPosition.x, module.localPosition.y + 0.5D, module.localPosition.z);
            if ((module.flags & ModularRobotVisualState.FLAG_DIRECTIONAL) != 0) orient(module);
            if ((module.flags & (ModularRobotVisualState.FLAG_WHEEL | ModularRobotVisualState.FLAG_AXLE)) != 0) {
                GL11.glRotatef(robot.getMechanicalPhaseDegrees(), 0.0F, 0.0F, 1.0F);
            }
            blocks.renderBlockAsItem((Block) found, metadata(module), 1.0F);
            GL11.glPopMatrix();
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F); GL11.glPopMatrix();
    }

    private void apply(RigidTransform3 transform) {
        bodyMatrix.clear(); bodyMatrix.put(transform.columnMajorMatrix()); bodyMatrix.flip(); GL11.glMultMatrix(bodyMatrix);
    }

    private static int metadata(ModularRobotVisualState.Module module) {
        if ((module.flags & ModularRobotVisualState.FLAG_DIRECTIONAL) == 0) return module.metadata;
        return module.metadata & ~7 | 2;
    }

    /** Maps canonical NORTH/UP block space onto the captured forward/up basis. */
    private void orient(ModularRobotVisualState.Module module) {
        Direction forward = module.orientation.getForward(), up = module.orientation.getUp();
        Direction right = module.orientation.getRight();
        orientationMatrix.clear();
        orientationMatrix.put(right.vector.x).put(right.vector.y).put(right.vector.z).put(0.0F);
        orientationMatrix.put(up.vector.x).put(up.vector.y).put(up.vector.z).put(0.0F);
        orientationMatrix.put(-forward.vector.x).put(-forward.vector.y).put(-forward.vector.z).put(0.0F);
        orientationMatrix.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
        orientationMatrix.flip(); GL11.glMultMatrix(orientationMatrix);
    }

    private static void tint(EntityModularRobot robot) {
        int status = robot.getVisualStatus(), diagnostic = robot.getVisualDiagnostics();
        if (status == ModularRobotState.Status.QUARANTINED.ordinal()
                || (diagnostic & EntityModularRobot.DIAGNOSTIC_QUARANTINE) != 0)
            GL11.glColor3f(1.0F, 0.45F, 0.42F);
        else if ((diagnostic & EntityModularRobot.DIAGNOSTIC_ARTICULATED) != 0)
            GL11.glColor3f(1.0F, 0.65F, 0.35F);
        else if ((diagnostic & EntityModularRobot.DIAGNOSTIC_THERMAL) != 0)
            GL11.glColor3f(1.0F, 0.55F, 0.2F);
        else if ((diagnostic & EntityModularRobot.DIAGNOSTIC_ELECTRICAL) != 0)
            GL11.glColor3f(1.0F, 0.9F, 0.45F);
        else if ((diagnostic & EntityModularRobot.DIAGNOSTIC_SENSOR) != 0)
            GL11.glColor3f(0.78F, 0.58F, 1.0F);
        else GL11.glColor3f(1.0F, 1.0F, 1.0F);
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return BLOCKS; }
}
