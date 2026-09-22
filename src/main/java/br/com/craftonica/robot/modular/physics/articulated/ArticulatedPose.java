package br.com.craftonica.robot.modular.physics.articulated;

import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Derived pose and conservative world AABBs; never a second persisted authority. */
public final class ArticulatedPose {
    private final List<RigidTransform3> bodyTransforms;
    private final List<List<AxisAlignedVolume>> worldVolumes;

    ArticulatedPose(List<RigidTransform3> transforms, List<List<AxisAlignedVolume>> volumes) {
        bodyTransforms = Collections.unmodifiableList(new ArrayList<RigidTransform3>(transforms));
        List<List<AxisAlignedVolume>> copy = new ArrayList<List<AxisAlignedVolume>>();
        for (List<AxisAlignedVolume> value : volumes)
            copy.add(Collections.unmodifiableList(new ArrayList<AxisAlignedVolume>(value)));
        worldVolumes = Collections.unmodifiableList(copy);
    }
    public List<RigidTransform3> getBodyTransforms() { return bodyTransforms; }
    public List<List<AxisAlignedVolume>> getWorldVolumes() { return worldVolumes; }
}
