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
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

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
        for (int index = 0; index < body.getContacts().size(); index++) {
            RigidBodyProperties.Contact contact = body.getContacts().get(index);
            AxisAlignedVolume probe = contactProbe(contact, state);
            if (!isLoaded(probe)) return null;
            if (collides(probe)) supported.add(Integer.valueOf(index));
        }
        return supported;
    }

    /** Surface grip is sampled only under already loaded contact patches. */
    public Map<Integer, Double> surfaceFrictionMultipliers(RigidBodyProperties body,
            TerrestrialRigidBodyModel.State state, List<Integer> supported) {
        Map<Integer, Double> values = new LinkedHashMap<Integer, Double>();
        for (Integer index : supported) {
            if (index == null || index.intValue() < 0 || index.intValue() >= body.getContacts().size())
                throw new IllegalArgumentException("supported contact");
            AxisAlignedVolume probe = contactProbe(body.getContacts().get(index.intValue()), state);
            if (!isLoaded(probe)) continue;
            int minX = MathHelper.floor_double(probe.minimum.x), maxX = MathHelper.floor_double(probe.maximum.x - 1.0e-9);
            int minZ = MathHelper.floor_double(probe.minimum.z), maxZ = MathHelper.floor_double(probe.maximum.z - 1.0e-9);
            int y = MathHelper.floor_double(probe.maximum.y - 0.002); double sum = 0.0; int samples = 0;
            for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) {
                samples++; Block block = world.getBlock(x,y,z);
                if (block == null || block == Blocks.air) continue;
                sum += frictionMultiplier(block.slipperiness);
            }
            values.put(index, Double.valueOf(samples == 0 ? 0.0 : sum / samples));
        }
        return values;
    }

    public static double frictionMultiplier(double slipperiness) {
        if (Double.isNaN(slipperiness) || Double.isInfinite(slipperiness) || slipperiness <= 0.0) return 1.0;
        return StrictMath.max(0.2, StrictMath.min(1.5, 0.6 / slipperiness));
    }

    private static AxisAlignedVolume contactProbe(RigidBodyProperties.Contact contact,
            TerrestrialRigidBodyModel.State state) {
        double cosine = StrictMath.cos(state.yawRadians), sine = StrictMath.sin(state.yawRadians);
        double localX = contact.pointMetres.x - 0.5, localZ = contact.pointMetres.z - 0.5;
        double centerX = state.x + cosine * localX + sine * localZ;
        double centerZ = state.z - sine * localX + cosine * localZ;
        double halfLength = contact.contactLengthMetres > 0.0 ? contact.contactLengthMetres * 0.5 : CONTACT_HALF_WIDTH;
        double halfWidth = contact.contactWidthMetres > 0.0 ? contact.contactWidthMetres * 0.5 : CONTACT_HALF_WIDTH;
        double dx = contact.rollingDirection.x, dz = contact.rollingDirection.z;
        double worldDx = cosine * dx + sine * dz, worldDz = -sine * dx + cosine * dz;
        double lateralX = -worldDz, lateralZ = worldDx;
        double extentX = StrictMath.abs(worldDx) * halfLength + StrictMath.abs(lateralX) * halfWidth;
        double extentZ = StrictMath.abs(worldDz) * halfLength + StrictMath.abs(lateralZ) * halfWidth;
        if (dx == 0.0 && dz == 0.0) extentX = extentZ = CONTACT_HALF_WIDTH;
        double y = state.y + contact.pointMetres.y;
        return new AxisAlignedVolume(centerX-extentX,y-CONTACT_DEPTH,centerZ-extentZ,
                centerX+extentX,y+0.001,centerZ+extentZ);
    }

    private static AxisAlignedBB box(AxisAlignedVolume value) {
        return AxisAlignedBB.getBoundingBox(value.minimum.x, value.minimum.y, value.minimum.z,
                value.maximum.x, value.maximum.y, value.maximum.z);
    }
}
