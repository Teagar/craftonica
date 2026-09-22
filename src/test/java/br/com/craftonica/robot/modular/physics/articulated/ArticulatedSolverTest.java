package br.com.craftonica.robot.modular.physics.articulated;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class ArticulatedSolverTest {
    private final ComponentCatalog catalog = StandardComponentCatalog.create();

    @Test public void gravityMovesLoadedArmReproduciblyWithoutTeleporting() {
        ArticulatedMechanism mechanism = new ArticulatedMechanism(singleHinge(), catalog);
        JointStateSet initial = JointStateSet.initial(mechanism.getKinematic());
        ArticulatedSolver.Result first = step(mechanism, initial, Collections.<GridVector,Double>emptyMap(), CLEAR);
        ArticulatedSolver.Result repeat = step(mechanism, initial, Collections.<GridVector,Double>emptyMap(), CLEAR);

        assertEquals(ArticulatedSolver.Status.ADVANCED, first.status);
        double position = first.state.getEntries().get(0).state.position;
        assertTrue(position > 0.0);
        assertTrue(position < 0.1);
        assertEquals(Double.doubleToLongBits(position),
                Double.doubleToLongBits(repeat.state.getEntries().get(0).state.position));
        assertTrue(first.reactions.get(0).gravityEffort > 0.0);
        assertTrue(mechanism.effectiveMassOrInertia(0,
                mechanism.pose(initial,RigidTransform3.identity()))
                > mechanism.getKinematic().getJoints().get(0).profile.movingInertiaOrMass);
    }

    @Test public void actuatorEffortOpposesGravityAndHardStopRemainsFinite() {
        ArticulatedMechanism mechanism = new ArticulatedMechanism(
                chain(1, new ComponentOrientation(Direction.NORTH, Direction.WEST)), catalog);
        JointStateSet initial = JointStateSet.initial(mechanism.getKinematic());
        Map<GridVector,Double> effort = Collections.singletonMap(new GridVector(0,0,1), Double.valueOf(-0.5));
        ArticulatedSolver.Result result = step(mechanism, initial, effort, CLEAR);
        assertEquals(ArticulatedSolver.Status.ADVANCED, result.status);
        assertTrue(result.state.getEntries().get(0).state.position < 0.0);
        assertEquals(-0.5, result.reactions.get(0).appliedEffort, 0.0);
    }

    @Test public void worldCollisionAndUnloadedBoundaryKeepLastAuthoritativeState() {
        ArticulatedMechanism mechanism = new ArticulatedMechanism(singleHinge(), catalog);
        JointStateSet initial = JointStateSet.initial(mechanism.getKinematic());
        ArticulatedSolver.Result blocked = step(mechanism, initial,
                Collections.singletonMap(new GridVector(0,0,1), Double.valueOf(0.5)), COLLIDING);
        assertEquals(ArticulatedSolver.Status.WORLD_COLLISION, blocked.status);
        assertEquals(initial.getEntries().get(0).state.position,blocked.state.getEntries().get(0).state.position,0.0);
        assertEquals(0.0,blocked.state.getEntries().get(0).state.velocity,0.0);
        ArticulatedSolver.Result unloaded = step(mechanism, initial,
                Collections.<GridVector,Double>emptyMap(), UNLOADED);
        assertEquals(ArticulatedSolver.Status.UNLOADED_BOUNDARY, unloaded.status);
        assertTrue(unloaded.delayed); assertSame(initial, unloaded.state);
    }

    @Test public void maximumTreeIsDelayedWhenCollisionBudgetWouldBeExceeded() {
        ArticulatedMechanism mechanism = new ArticulatedMechanism(singleHinge(), catalog);
        JointStateSet initial = JointStateSet.initial(mechanism.getKinematic());
        ArticulatedTickBudget budget = new ArticulatedTickBudget(); ArticulatedSolver.Result result = null;
        for (int i=0;i<33;i++) result = ArticulatedSolver.step(mechanism, initial,
                    Collections.<GridVector,Double>emptyMap(), RigidTransform3.identity(), CLEAR,budget,0.025);
        assertEquals(ArticulatedSolver.Status.BUDGET_EXCEEDED, result.status);
        assertTrue(result.delayed); assertSame(initial, result.state);
    }

    @Test public void revolutePoseRotatesChildAroundPhysicalJointCenter() {
        ArticulatedMechanism mechanism = new ArticulatedMechanism(singleHinge(), catalog);
        List<JointStateSet.Entry> entries = Collections.singletonList(new JointStateSet.Entry(
                new GridVector(0,0,1), new br.com.craftonica.robot.modular.joint.JointState(StrictMath.PI/2.0,0.0)));
        ArticulatedPose pose = mechanism.pose(new JointStateSet(entries), RigidTransform3.identity());
        Vector3 transformed = pose.getBodyTransforms().get(1).point(new Vector3(0.5,0.5,2.5));
        assertEquals(0.5, transformed.x, 1.0e-12);
        assertEquals(-0.5, transformed.y, 1.0e-12);
        assertEquals(1.5, transformed.z, 1.0e-12);
    }

    @Test public void foldedNonAdjacentBodyIsStoppedBySelfCollision() {
        ComponentOrientation verticalAxis=new ComponentOrientation(Direction.NORTH,Direction.WEST);
        ArticulatedMechanism mechanism=new ArticulatedMechanism(chain(4,verticalAxis),catalog);
        List<JointStateSet.Entry> entries=new ArrayList<JointStateSet.Entry>();
        for(int joint=0;joint<4;joint++)entries.add(new JointStateSet.Entry(new GridVector(0,0,joint*2+1),
                new br.com.craftonica.robot.modular.joint.JointState(StrictMath.PI/2.0,0.0)));
        JointStateSet folded=new JointStateSet(entries);
        ArticulatedSolver.Result result=ArticulatedSolver.step(mechanism,folded,
                Collections.<GridVector,Double>emptyMap(),RigidTransform3.identity(),CLEAR,
                new ArticulatedTickBudget(),0.025);
        assertEquals(ArticulatedSolver.Status.SELF_COLLISION,result.status);
        for(JointStateSet.Entry entry:result.state.getEntries())assertEquals(0.0,entry.state.velocity,0.0);
    }

    private static ArticulatedSolver.Result step(ArticulatedMechanism mechanism, JointStateSet state,
            Map<GridVector,Double> efforts, CompoundCollisionProbe.WorldView world) {
        return ArticulatedSolver.step(mechanism,state,efforts,RigidTransform3.identity(),world,
                new ArticulatedTickBudget(),0.025);
    }
    private static ModularRobotManifest singleHinge() { return chain(1, ComponentOrientation.NORTH_UP); }
    private static ModularRobotManifest chain(int jointCount) { return chain(jointCount, ComponentOrientation.NORTH_UP); }
    private static ModularRobotManifest chain(int jointCount, ComponentOrientation jointOrientation) {
        List<ModularBlockSnapshot> modules=new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges=new ArrayList<AssemblyEdge>();
        for(int body=0;body<=jointCount;body++) modules.add(module(StandardComponentCatalog.CHASSIS,0,0,body*2));
        for(int joint=0;joint<jointCount;joint++) {
            GridVector parent=new GridVector(0,0,joint*2), at=new GridVector(0,0,joint*2+1), child=new GridVector(0,0,joint*2+2);
            modules.add(new ModularBlockSnapshot(StandardComponentCatalog.REVOLUTE_JOINT,1,at,
                    jointOrientation,StandardComponentCatalog.REVOLUTE_JOINT,0,null));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.JOINT,parent,"mount_south",at,"parent"));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.JOINT,at,"child",child,"mount_north"));
        }
        return new ModularRobotManifest(new UUID(90,jointCount),modules,edges);
    }
    private static ModularBlockSnapshot module(String type,int x,int y,int z){
        return new ModularBlockSnapshot(type,1,new GridVector(x,y,z),ComponentOrientation.NORTH_UP,type,0,null);
    }
    private static final CompoundCollisionProbe.WorldView CLEAR=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return true;} public boolean collides(AxisAlignedVolume v){return false;}};
    private static final CompoundCollisionProbe.WorldView COLLIDING=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return true;} public boolean collides(AxisAlignedVolume v){return true;}};
    private static final CompoundCollisionProbe.WorldView UNLOADED=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return false;} public boolean collides(AxisAlignedVolume v){throw new AssertionError();}};
}
