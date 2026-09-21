package br.com.craftonica.robot.modular.physics.forge;

import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Loaded-chunk-only collision boundary for the server rigid-body model. */
public final class ForgeRigidBodyWorld implements CompoundCollisionProbe.WorldView {
    public static final int MAX_COLLISION_QUERIES_PER_SUBSTEP = 128;
    private static final double CONTACT_HALF_WIDTH = 0.03;
    private static final double CONTACT_DEPTH = 0.08;
    private final World world;
    private final Entity entity;
    private int remainingCollisionQueries = MAX_COLLISION_QUERIES_PER_SUBSTEP;

    public ForgeRigidBodyWorld(World world, Entity entity) {
        if (world == null || entity == null) throw new IllegalArgumentException("world or entity");
        this.world = world; this.entity = entity;
    }

    @Override public boolean isLoaded(AxisAlignedVolume volume) {
        if (volume.minimum.y < 0.0 || volume.maximum.y >= 256.0) return false;
        int minChunkX = MathHelper.floor_double(volume.minimum.x) >> 4;
        int maxChunkX = MathHelper.floor_double(volume.maximum.x - 1.0e-9) >> 4;
        int minChunkZ = MathHelper.floor_double(volume.minimum.z) >> 4;
        int maxChunkZ = MathHelper.floor_double(volume.maximum.z - 1.0e-9) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++)
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
                if (!world.getChunkProvider().chunkExists(cx, cz)) return false;
        return true;
    }

    @Override public boolean collides(AxisAlignedVolume volume) {
        if (!isLoaded(volume)) throw new IllegalStateException("unloaded collision query");
        if (remainingCollisionQueries <= 0) return true;
        remainingCollisionQueries--;
        return !world.getCollidingBoundingBoxes(entity, box(volume)).isEmpty();
    }

    public void beginSubstep() { remainingCollisionQueries = MAX_COLLISION_QUERIES_PER_SUBSTEP; }

    /** Returns null at an unloaded boundary so the caller can fail safe without querying it. */
    public List<Integer> supportedContacts(RigidBodyProperties body, TerrestrialRigidBodyModel.State state) {
        List<Integer> supported = new ArrayList<Integer>();
        double cosine = StrictMath.cos(state.yawRadians), sine = StrictMath.sin(state.yawRadians);
        for (int index = 0; index < body.getContacts().size(); index++) {
            RigidBodyProperties.Contact contact = body.getContacts().get(index);
            double localX = contact.pointMetres.x - 0.5, localZ = contact.pointMetres.z - 0.5;
            double x = state.x + cosine * localX + sine * localZ;
            double z = state.z - sine * localX + cosine * localZ;
            double y = state.y + contact.pointMetres.y;
            AxisAlignedVolume probe = new AxisAlignedVolume(x - CONTACT_HALF_WIDTH, y - CONTACT_DEPTH,
                    z - CONTACT_HALF_WIDTH, x + CONTACT_HALF_WIDTH, y + 0.001,
                    z + CONTACT_HALF_WIDTH);
            if (!isLoaded(probe)) return null;
            if (collides(probe)) supported.add(Integer.valueOf(index));
        }
        return supported;
    }

    private static AxisAlignedBB box(AxisAlignedVolume value) {
        return AxisAlignedBB.getBoundingBox(value.minimum.x, value.minimum.y, value.minimum.z,
                value.maximum.x, value.maximum.y, value.maximum.z);
    }
}
