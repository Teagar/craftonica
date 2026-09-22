package br.com.craftonica.robot.modular.physics.articulated;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;

import java.util.*;

/** Immutable bounded multic body derived entirely from a captured physical manifest. */
public strictfp final class ArticulatedMechanism {
    private final KinematicAssembly kinematic;
    private final List<RigidBodyProperties> bodies;
    private final List<Set<Integer>> descendants;

    public ArticulatedMechanism(ModularRobotManifest manifest, ComponentCatalog catalog) {
        if (manifest == null || catalog == null) throw new IllegalArgumentException("manifest or catalog");
        kinematic = KinematicAssemblyAnalyzer.analyze(manifest, catalog);
        if (!kinematic.isValid()) throw new IllegalArgumentException("invalid kinematic assembly");
        Map<GridVector, ModularBlockSnapshot> modules = new HashMap<GridVector, ModularBlockSnapshot>();
        for (ModularBlockSnapshot module : manifest.getModules()) modules.put(module.localPosition, module);
        List<RigidBodyProperties> values = new ArrayList<RigidBodyProperties>();
        for (KinematicAssembly.Body body : kinematic.getBodies()) {
            List<ModularBlockSnapshot> selected = new ArrayList<ModularBlockSnapshot>();
            for (GridVector position : body.modulePositions) {
                ModularBlockSnapshot module = modules.get(position); if (module != null) selected.add(module);
            }
            if (selected.isEmpty()) throw new IllegalArgumentException("empty articulated body");
            values.add(RigidBodyProperties.derive(selected, catalog));
        }
        bodies = Collections.unmodifiableList(values);
        List<Set<Integer>> children = new ArrayList<Set<Integer>>();
        for (int i=0;i<bodies.size();i++) children.add(new LinkedHashSet<Integer>());
        for (KinematicAssembly.Joint joint : kinematic.getJoints()) children.get(joint.parentBodyId).add(joint.childBodyId);
        List<Set<Integer>> closure = new ArrayList<Set<Integer>>();
        for (int i=0;i<bodies.size();i++) { Set<Integer> set=new LinkedHashSet<Integer>(); collect(i,children,set); closure.add(Collections.unmodifiableSet(set)); }
        descendants = Collections.unmodifiableList(closure);
    }

    public KinematicAssembly getKinematic() { return kinematic; }
    public List<RigidBodyProperties> getBodies() { return bodies; }

    public ArticulatedPose pose(JointStateSet state, RigidTransform3 root) {
        if (state == null || root == null || state.getEntries().size() != kinematic.getJoints().size())
            throw new IllegalArgumentException("articulated pose state");
        Map<GridVector, Double> coordinates = new HashMap<GridVector, Double>();
        for (JointStateSet.Entry entry : state.getEntries()) coordinates.put(entry.position, entry.state.position);
        List<RigidTransform3> transforms = new ArrayList<RigidTransform3>(Collections.nCopies(bodies.size(), (RigidTransform3)null));
        transforms.set(0, root); int unresolved = kinematic.getJoints().size();
        for (int pass=0; pass<kinematic.getJoints().size()+1 && unresolved>0; pass++) {
            for (KinematicAssembly.Joint joint : kinematic.getJoints()) {
                if (transforms.get(joint.childBodyId) != null || transforms.get(joint.parentBodyId) == null) continue;
                RigidTransform3 parent = transforms.get(joint.parentBodyId);
                Vector3 pivot = parent.point(new Vector3(joint.modulePosition.x+0.5,
                        joint.modulePosition.y+0.5, joint.modulePosition.z+0.5));
                Vector3 axis = parent.direction(vector(joint.axis)); double q = coordinates.get(joint.modulePosition).doubleValue();
                RigidTransform3 delta = joint.kind == JointProfile.Kind.REVOLUTE
                        ? RigidTransform3.rotation(axis, q, pivot)
                        : RigidTransform3.translation(RigidTransform3.scale(axis, q));
                transforms.set(joint.childBodyId, delta.after(parent)); unresolved--;
            }
        }
        if (unresolved != 0) throw new IllegalArgumentException("unresolved articulated tree");
        List<List<AxisAlignedVolume>> volumes = new ArrayList<List<AxisAlignedVolume>>();
        for (int body=0; body<bodies.size(); body++) {
            List<AxisAlignedVolume> transformed = new ArrayList<AxisAlignedVolume>();
            for (AxisAlignedVolume volume : bodies.get(body).getCollisionVolumes())
                transformed.add(transform(volume, transforms.get(body)));
            volumes.add(transformed);
        }
        return new ArticulatedPose(transforms, volumes);
    }

    public double gravityEffort(int jointIndex, ArticulatedPose pose, double gravity) {
        KinematicAssembly.Joint joint = kinematic.getJoints().get(jointIndex);
        RigidTransform3 parent = pose.getBodyTransforms().get(joint.parentBodyId);
        Vector3 pivot = parent.point(new Vector3(joint.modulePosition.x+0.5,
                joint.modulePosition.y+0.5, joint.modulePosition.z+0.5));
        Vector3 axis = parent.direction(vector(joint.axis)); double effort = 0.0;
        for (Integer bodyId : descendants.get(joint.childBodyId)) {
            RigidBodyProperties body = bodies.get(bodyId.intValue());
            Vector3 center = pose.getBodyTransforms().get(bodyId.intValue()).point(body.centerOfMassMetres);
            Vector3 force = new Vector3(0.0, -body.massKg*gravity, 0.0);
            effort += joint.kind == JointProfile.Kind.REVOLUTE
                    ? RigidTransform3.dot(RigidTransform3.cross(RigidTransform3.subtract(center,pivot),force),axis)
                    : RigidTransform3.dot(force,axis);
        }
        return effort;
    }

    /** Generalized moving mass/inertia reflected at one coordinate from its complete child subtree. */
    public double effectiveMassOrInertia(int jointIndex, ArticulatedPose pose) {
        KinematicAssembly.Joint joint=kinematic.getJoints().get(jointIndex);
        RigidTransform3 parent=pose.getBodyTransforms().get(joint.parentBodyId);
        Vector3 pivot=parent.point(new Vector3(joint.modulePosition.x+0.5,
                joint.modulePosition.y+0.5,joint.modulePosition.z+0.5));
        Vector3 axis=parent.direction(vector(joint.axis));double value=joint.profile.movingInertiaOrMass;
        for(Integer bodyId:descendants.get(joint.childBodyId)){
            RigidBodyProperties body=bodies.get(bodyId.intValue());
            if(joint.kind==JointProfile.Kind.PRISMATIC){value+=body.massKg;continue;}
            RigidTransform3 transform=pose.getBodyTransforms().get(bodyId.intValue());
            Vector3 localAxis=transform.inverseDirection(axis),inertia=body.principalInertiaKgMetresSquared;
            double rotational=inertia.x*localAxis.x*localAxis.x+inertia.y*localAxis.y*localAxis.y
                    +inertia.z*localAxis.z*localAxis.z;
            Vector3 center=transform.point(body.centerOfMassMetres),offset=RigidTransform3.subtract(center,pivot);
            Vector3 perpendicular=RigidTransform3.subtract(offset,RigidTransform3.scale(axis,RigidTransform3.dot(offset,axis)));
            value+=rotational+body.massKg*RigidTransform3.dot(perpendicular,perpendicular);
        }
        return value;
    }

    public boolean adjacent(int a, int b) {
        for (KinematicAssembly.Joint joint : kinematic.getJoints())
            if (joint.parentBodyId==a && joint.childBodyId==b || joint.parentBodyId==b && joint.childBodyId==a) return true;
        return false;
    }

    private static void collect(int body, List<Set<Integer>> children, Set<Integer> result) {
        if (!result.add(Integer.valueOf(body))) return;
        for (Integer child : children.get(body)) collect(child.intValue(),children,result);
    }
    private static Vector3 vector(Direction direction) {
        return new Vector3(direction.vector.x,direction.vector.y,direction.vector.z);
    }
    private static AxisAlignedVolume transform(AxisAlignedVolume value, RigidTransform3 transform) {
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++) {
            Vector3 point=transform.point(new Vector3(x==0?value.minimum.x:value.maximum.x,
                    y==0?value.minimum.y:value.maximum.y,z==0?value.minimum.z:value.maximum.z));
            minX=StrictMath.min(minX,point.x);minY=StrictMath.min(minY,point.y);minZ=StrictMath.min(minZ,point.z);
            maxX=StrictMath.max(maxX,point.x);maxY=StrictMath.max(maxY,point.y);maxZ=StrictMath.max(maxZ,point.z);
        }
        return new AxisAlignedVolume(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
