package br.com.craftonica.robot.modular.physics.articulated;

import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.Vector3;
import br.com.craftonica.robot.modular.JointProfile;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.joint.JointConstraintModel;
import br.com.craftonica.robot.modular.joint.JointState;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.joint.JointStep;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;

import java.util.*;

/** Bounded server-authoritative generalized-coordinate solver for a joint tree. */
public strictfp final class ArticulatedSolver {
    public static final double GRAVITY = 9.80665;
    private static final double CONTACT_EPSILON = 1.0e-7;
    private ArticulatedSolver() { }

    public static Result step(ArticulatedMechanism mechanism, JointStateSet before,
            Map<GridVector, Double> requestedEfforts, RigidTransform3 root,
            CompoundCollisionProbe.WorldView world, ArticulatedTickBudget budget, double seconds) {
        if (mechanism == null || before == null || requestedEfforts == null || root == null
                || world == null || budget == null || !finite(seconds) || seconds <= 0.0
                || seconds > JointConstraintModel.MAX_STEP_SECONDS)
            throw new IllegalArgumentException("articulated step");
        KinematicAssembly kinematic = mechanism.getKinematic();
        if (before.getEntries().size() != kinematic.getJoints().size())
            throw new IllegalArgumentException("joint state count");
        ArticulatedPose oldPose = mechanism.pose(before, root);
        int collisionTests = collisionTests(mechanism, oldPose);
        if (!budget.reserve(mechanism.getBodies().size(), kinematic.getJoints().size(), collisionTests))
            return Result.delayed(before, oldPose, Status.BUDGET_EXCEEDED);

        Map<GridVector, JointState> states = states(before);
        List<JointStateSet.Entry> nextEntries = new ArrayList<JointStateSet.Entry>();
        List<JointReaction> reactions = new ArrayList<JointReaction>();
        for (int index=0; index<kinematic.getJoints().size(); index++) {
            KinematicAssembly.Joint joint = kinematic.getJoints().get(index);
            Double requested = requestedEfforts.get(joint.modulePosition);
            double effort = requested == null ? 0.0 : requested.doubleValue();
            if (!finite(effort)) throw new IllegalArgumentException("joint effort");
            double gravityEffort = mechanism.gravityEffort(index, oldPose, GRAVITY);
            JointProfile p=joint.profile;
            JointProfile dynamic=new JointProfile(p.kind,p.axis,p.minimumPosition,p.maximumPosition,
                    p.maximumVelocity,p.maximumEffort,p.viscousFriction,
                    mechanism.effectiveMassOrInertia(index,oldPose),p.maximumStopReaction);
            JointStep step = JointConstraintModel.step(dynamic, states.get(joint.modulePosition),
                    effort, -gravityEffort, seconds);
            nextEntries.add(new JointStateSet.Entry(joint.modulePosition, step.state));
            reactions.add(new JointReaction(joint.modulePosition, gravityEffort,
                    step.appliedEffort, step.stopReaction, step.diagnostic));
        }
        JointStateSet proposed = new JointStateSet(nextEntries);
        ArticulatedPose proposedPose = mechanism.pose(proposed, root);
        if (selfCollision(mechanism, oldPose, proposedPose))
            return Result.blocked(before, oldPose, reactions, Status.SELF_COLLISION);
        for (int bodyIndex=0;bodyIndex<proposedPose.getWorldVolumes().size();bodyIndex++)
            for (int volumeIndex=0;volumeIndex<proposedPose.getWorldVolumes().get(bodyIndex).size();volumeIndex++) {
            AxisAlignedVolume volume=union(oldPose.getWorldVolumes().get(bodyIndex).get(volumeIndex),
                    proposedPose.getWorldVolumes().get(bodyIndex).get(volumeIndex));
            if (!world.isLoaded(volume)) return Result.delayed(before, oldPose, Status.UNLOADED_BOUNDARY);
            if (world.collides(volume)) return Result.blocked(before, oldPose, reactions, Status.WORLD_COLLISION);
        }
        return new Result(proposed, proposedPose, reactions, Status.ADVANCED, false);
    }

    public static RigidTransform3 rootTransform(double x, double y, double z, double yawRadians) {
        if (!finite(x)||!finite(y)||!finite(z)||!finite(yawRadians)) throw new IllegalArgumentException("root pose");
        RigidTransform3 rotation = RigidTransform3.rotation(new Vector3(0,1,0), -yawRadians,
                new Vector3(0.5,0,0.5));
        return RigidTransform3.translation(new Vector3(x-0.5,y,z-0.5)).after(rotation);
    }

    private static Map<GridVector, JointState> states(JointStateSet set) {
        Map<GridVector, JointState> values = new HashMap<GridVector, JointState>();
        for (JointStateSet.Entry entry : set.getEntries()) values.put(entry.position, entry.state);
        return values;
    }
    private static int collisionTests(ArticulatedMechanism mechanism, ArticulatedPose pose) {
        long count=0; List<List<AxisAlignedVolume>> volumes=pose.getWorldVolumes();
        for(List<AxisAlignedVolume> body:volumes) count+=body.size();
        for(int a=0;a<volumes.size();a++)for(int b=a+1;b<volumes.size();b++)
            if(!mechanism.adjacent(a,b)) count+=(long)volumes.get(a).size()*volumes.get(b).size();
        return count>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)count;
    }
    private static boolean selfCollision(ArticulatedMechanism mechanism, ArticulatedPose before, ArticulatedPose after) {
        List<List<AxisAlignedVolume>> volumes=after.getWorldVolumes();
        for(int a=0;a<volumes.size();a++)for(int b=a+1;b<volumes.size();b++) {
            if(mechanism.adjacent(a,b)) continue;
            for(int ai=0;ai<volumes.get(a).size();ai++)for(int bi=0;bi<volumes.get(b).size();bi++)
                if(overlaps(union(before.getWorldVolumes().get(a).get(ai),volumes.get(a).get(ai)),
                        union(before.getWorldVolumes().get(b).get(bi),volumes.get(b).get(bi)))) return true;
        }
        return false;
    }
    private static AxisAlignedVolume union(AxisAlignedVolume a,AxisAlignedVolume b){
        return new AxisAlignedVolume(StrictMath.min(a.minimum.x,b.minimum.x),StrictMath.min(a.minimum.y,b.minimum.y),
                StrictMath.min(a.minimum.z,b.minimum.z),StrictMath.max(a.maximum.x,b.maximum.x),
                StrictMath.max(a.maximum.y,b.maximum.y),StrictMath.max(a.maximum.z,b.maximum.z));
    }
    private static boolean overlaps(AxisAlignedVolume a, AxisAlignedVolume b) {
        return a.maximum.x>b.minimum.x+CONTACT_EPSILON&&b.maximum.x>a.minimum.x+CONTACT_EPSILON
                &&a.maximum.y>b.minimum.y+CONTACT_EPSILON&&b.maximum.y>a.minimum.y+CONTACT_EPSILON
                &&a.maximum.z>b.minimum.z+CONTACT_EPSILON&&b.maximum.z>a.minimum.z+CONTACT_EPSILON;
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}

    public enum Status { ADVANCED, BUDGET_EXCEEDED, UNLOADED_BOUNDARY, SELF_COLLISION, WORLD_COLLISION }
    public static final class JointReaction {
        public final GridVector jointPosition; public final double gravityEffort, appliedEffort, stopReaction;
        public final JointStep.Diagnostic diagnostic;
        JointReaction(GridVector position,double gravity,double applied,double stop,JointStep.Diagnostic diagnostic){
            this.jointPosition=position;this.gravityEffort=gravity;this.appliedEffort=applied;
            this.stopReaction=stop;this.diagnostic=diagnostic;
        }
    }
    public static final class Result {
        public final JointStateSet state; public final ArticulatedPose pose;
        public final List<JointReaction> reactions; public final Status status; public final boolean delayed;
        Result(JointStateSet state,ArticulatedPose pose,List<JointReaction> reactions,Status status,boolean delayed){
            this.state=state;this.pose=pose;this.reactions=Collections.unmodifiableList(new ArrayList<JointReaction>(reactions));
            this.status=status;this.delayed=delayed;
        }
        static Result delayed(JointStateSet state,ArticulatedPose pose,Status status){
            return new Result(state,pose,Collections.<JointReaction>emptyList(),status,true);
        }
        static Result blocked(JointStateSet state,ArticulatedPose pose,List<JointReaction> reactions,Status status){
            List<JointStateSet.Entry> stopped=new ArrayList<JointStateSet.Entry>();
            for(JointStateSet.Entry entry:state.getEntries()) stopped.add(new JointStateSet.Entry(
                    entry.position,new JointState(entry.state.position,0.0)));
            return new Result(new JointStateSet(stopped),pose,reactions,status,false);
        }
    }
}
