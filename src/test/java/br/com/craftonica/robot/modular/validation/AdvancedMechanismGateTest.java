package br.com.craftonica.robot.modular.validation;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.ComponentType;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.JointPort;
import br.com.craftonica.robot.modular.MechanicalPort;
import br.com.craftonica.robot.modular.ModularRobotPersistence;
import br.com.craftonica.robot.modular.ModularRobotState;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.StructuralPort;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.assembly.MechanicalAssemblyAnalyzer;
import br.com.craftonica.robot.modular.assembly.TrackAssembly;
import br.com.craftonica.robot.modular.assembly.TrackAssemblyAnalyzer;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedMechanism;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedSolver;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedTickBudget;
import br.com.craftonica.robot.modular.physics.articulated.RigidTransform3;
import br.com.craftonica.robot.modular.servo.ServoJointLoop;
import br.com.craftonica.robot.modular.servo.ServoStep;
import br.com.craftonica.robot.modular.visual.ModularRobotVisualState;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.tile.RoboBoardState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public final class AdvancedMechanismGateTest {
    private final ComponentCatalog catalog=StandardComponentCatalog.create();

    @Test public void armAndIndependentGripperUseOneTreeAndFourPublicServos(){
        ModularRobotManifest manifest=AdvancedMechanismFixtures.armWithGripper();
        KinematicAssembly kinematic=KinematicAssemblyAnalyzer.analyze(manifest,catalog);
        assertTrue(kinematic.getDiagnostics().toString(),kinematic.isValid());
        assertEquals(5,kinematic.getBodies().size());assertEquals(4,kinematic.getJoints().size());
        assertTrue(MobileElectricalEvaluator.evaluate(manifest.getElectricalNetlist()).getDiagnostics().isEmpty());
        ServoJointLoop loop=new ServoJointLoop(manifest,catalog,kinematic);
        assertEquals(4,loop.channelCount());
        ServoJointLoop.Evaluation off=loop.evaluate(loop.initialState(),JointStateSet.initial(kinematic),null,0.025);
        assertEquals(4,off.efforts.size());
        for(ServoJointLoop.ChannelState channel:off.state.getChannels())
            assertEquals(ServoStep.Diagnostic.UNPOWERED,channel.diagnostic);
        for(Double effort:off.efforts.values())assertEquals(0.0,effort.doubleValue(),0.0);
    }

    @Test public void everyDocumentedMechanicalEdgeCanBeBuiltOnAdjacentPublicFaces(){
        for(ModularRobotManifest manifest:Arrays.asList(AdvancedMechanismFixtures.armWithGripper(),
                AdvancedMechanismFixtures.linearMechanism(),AdvancedMechanismFixtures.trackedBase(),
                AdvancedMechanismFixtures.mecanumBase())){
            Map<GridVector,ModularBlockSnapshot> modules=new LinkedHashMap<GridVector,ModularBlockSnapshot>();
            for(ModularBlockSnapshot module:manifest.getModules())modules.put(module.localPosition,module);
            for(AssemblyEdge edge:manifest.getEdges()){
                ModularBlockSnapshot first=modules.get(edge.firstPosition),second=modules.get(edge.secondPosition);
                assertNotNull(first);assertNotNull(second);
                Direction towardSecond=direction(edge.secondPosition.subtract(edge.firstPosition));
                assertEquals(towardSecond,port(first,edge.firstPort,edge.kind));
                assertEquals(towardSecond.opposite(),port(second,edge.secondPort,edge.kind));
            }
        }
    }

    @Test public void prismaticFixtureMovesByForceAndContainsCollisionAndUnload() throws Exception {
        ModularRobotManifest manifest=AdvancedMechanismFixtures.linearMechanism();
        ArticulatedMechanism mechanism=new ArticulatedMechanism(manifest,catalog);
        ServoJointLoop actuator=new ServoJointLoop(manifest,catalog,mechanism.getKinematic());
        assertEquals(1,actuator.channelCount());
        assertEquals(ServoStep.Diagnostic.UNPOWERED,actuator.evaluate(actuator.initialState(),
                JointStateSet.initial(mechanism.getKinematic()),null,0.025).state.getChannels().get(0).diagnostic);
        JointStateSet initial=JointStateSet.initial(mechanism.getKinematic());GridVector joint=new GridVector(0,0,1);
        ServoJointLoop.Evaluation powered=actuator.evaluate(actuator.initialState(),initial,boardWithPulse(2000),0.025);
        Map<GridVector,Double> effort=powered.efforts;assertTrue(effort.get(joint).doubleValue()>0.0);
        ArticulatedSolver.Result moving=step(mechanism,initial,effort,CLEAR);
        assertEquals(ArticulatedSolver.Status.ADVANCED,moving.status);
        assertTrue(moving.state.getEntries().get(0).state.position>0.0);
        assertTrue(moving.state.getEntries().get(0).state.position<0.5);
        ArticulatedSolver.Result blocked=step(mechanism,initial,effort,COLLIDING);
        assertEquals(ArticulatedSolver.Status.WORLD_COLLISION,blocked.status);
        assertEquals(0.0,blocked.state.getEntries().get(0).state.position,0.0);
        ArticulatedSolver.Result unloaded=step(mechanism,initial,effort,UNLOADED);
        assertEquals(ArticulatedSolver.Status.UNLOADED_BOUNDARY,unloaded.status);
        assertTrue(unloaded.delayed);assertSame(initial,unloaded.state);
    }

    @Test public void trackAndMecanumBasesUseTheSameDriveAndElectricalInfrastructure(){
        ModularRobotManifest tracked=AdvancedMechanismFixtures.trackedBase();
        TrackAssembly tracks=TrackAssemblyAnalyzer.analyze(tracked,catalog);
        assertTrue(tracks.getDiagnostics().toString(),tracks.isValid());assertEquals(2,tracks.getUnits().size());
        assertEquals(2,MechanicalAssemblyAnalyzer.analyze(tracked,catalog).getDrives().size());
        assertEquals(2,new CoupledDriveLoop(tracked,catalog).initialState().getChannels().size());
        assertTrue(MobileElectricalEvaluator.evaluate(tracked.getElectricalNetlist()).getDiagnostics().isEmpty());

        ModularRobotManifest mecanum=AdvancedMechanismFixtures.mecanumBase();
        RigidBodyProperties body=RigidBodyProperties.derive(mecanum,catalog);
        assertEquals(4,body.getContacts().size());
        assertEquals(4,MechanicalAssemblyAnalyzer.analyze(mecanum,catalog).getDrives().size());
        assertEquals(4,new CoupledDriveLoop(mecanum,catalog).initialState().getChannels().size());
        assertTrue(MobileElectricalEvaluator.evaluate(mecanum.getElectricalNetlist()).getDiagnostics().isEmpty());
    }

    @Test public void articulatedStateRoundTripsWithServoFeedbackAndChecksum(){
        ModularRobotManifest manifest=AdvancedMechanismFixtures.armWithGripper();
        ModularRobotState robot=new ModularRobotState(new UUID(191,10),new UUID(191,11),GridVector.ZERO,
                ComponentOrientation.NORTH_UP,manifest);
        TerrestrialRigidBodyModel.State dynamics=new TerrestrialRigidBodyModel.State(0,64,0,0,0,0,0,0);
        CoupledDriveLoop drive=new CoupledDriveLoop(manifest,catalog);CoupledDriveLoop.State driveState=drive.initialState();
        KinematicAssembly kinematic=KinematicAssemblyAnalyzer.analyze(manifest,catalog);
        JointStateSet joints=JointStateSet.initial(kinematic);ServoJointLoop servos=new ServoJointLoop(manifest,catalog,kinematic);
        net.minecraft.nbt.NBTTagCompound envelope=ModularRobotPersistence.write(robot,dynamics,drive,driveState,
                new RoboBoardState(new UUID(191,12)),27,joints,servos,servos.initialState());
        ModularRobotPersistence.Snapshot restored=ModularRobotPersistence.read(envelope,catalog);
        assertEquals(4,restored.joints.getEntries().size());assertEquals(4,restored.servoLoop.channelCount());
        assertEquals(27,restored.sensorCounter);
    }

    @Test public void deterministicSoakStaysWithinBudgetsAndFeedsTwoPassiveClients(){
        ArticulatedMechanism mechanism=new ArticulatedMechanism(AdvancedMechanismFixtures.armWithGripper(),catalog);
        JointStateSet a=JointStateSet.initial(mechanism.getKinematic()),b=a;
        for(int tick=0;tick<4000;tick++){
            ArticulatedSolver.Result first=step(mechanism,a,Collections.<GridVector,Double>emptyMap(),CLEAR);
            ArticulatedSolver.Result repeat=step(mechanism,b,Collections.<GridVector,Double>emptyMap(),CLEAR);
            assertEquals(first.status,repeat.status);a=first.state;b=repeat.state;
        }
        for(int i=0;i<a.getEntries().size();i++){
            assertEquals(Double.doubleToLongBits(a.getEntries().get(i).state.position),
                    Double.doubleToLongBits(b.getEntries().get(i).state.position));
            assertTrue(finite(a.getEntries().get(i).state.position));
        }
        for(ModularRobotManifest manifest:Arrays.asList(AdvancedMechanismFixtures.armWithGripper(),
                AdvancedMechanismFixtures.linearMechanism(),AdvancedMechanismFixtures.trackedBase(),
                AdvancedMechanismFixtures.mecanumBase()))assertTwoClients(manifest);
    }

    private static ArticulatedSolver.Result step(ArticulatedMechanism mechanism,JointStateSet state,
            Map<GridVector,Double> efforts,CompoundCollisionProbe.WorldView world){
        ArticulatedTickBudget budget=new ArticulatedTickBudget();ArticulatedSolver.Result result=ArticulatedSolver.step(
                mechanism,state,efforts,RigidTransform3.identity(),world,budget,0.025);
        assertTrue(budget.getBodySteps()<=ArticulatedTickBudget.MAX_BODY_STEPS);
        assertTrue(budget.getJointSteps()<=ArticulatedTickBudget.MAX_JOINT_STEPS);
        assertTrue(budget.getCollisionTests()<=ArticulatedTickBudget.MAX_COLLISION_TESTS);return result;
    }
    private static void assertTwoClients(ModularRobotManifest manifest){
        ModularRobotVisualState visual=ModularRobotVisualState.fromManifest(manifest);ByteBuf encoded=Unpooled.buffer();visual.write(encoded);
        byte[] bytes=new byte[encoded.readableBytes()];encoded.readBytes(bytes);
        ModularRobotVisualState first=ModularRobotVisualState.read(Unpooled.wrappedBuffer(bytes));
        ModularRobotVisualState second=ModularRobotVisualState.read(Unpooled.wrappedBuffer(bytes));
        assertEquals(first.getModules().size(),second.getModules().size());assertEquals(first.getBodyCount(),second.getBodyCount());
        assertTrue(bytes.length<=ModularRobotVisualState.MAX_PAYLOAD_BYTES);
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    private Direction port(ModularBlockSnapshot module,String id,AssemblyEdge.Kind kind){
        ComponentType type=catalog.require(module.componentTypeId);Direction local=null;
        if(kind==AssemblyEdge.Kind.STRUCTURAL)for(StructuralPort value:type.getStructuralPorts())if(value.id.equals(id))local=value.pose.face;
        if(kind==AssemblyEdge.Kind.MECHANICAL)for(MechanicalPort value:type.getMechanicalPorts())if(value.id.equals(id))local=value.pose.face;
        if(kind==AssemblyEdge.Kind.JOINT){
            for(JointPort value:type.getJointPorts())if(value.id.equals(id))local=value.pose.face;
            for(StructuralPort value:type.getStructuralPorts())if(value.id.equals(id))local=value.pose.face;
        }
        assertNotNull(module.componentTypeId+"/"+id,local);return module.localOrientation.toWorld(local);
    }
    private static Direction direction(GridVector delta){
        for(Direction value:Direction.values())if(value.vector.equals(delta))return value;
        fail("edge is not face-adjacent: "+delta);return Direction.UP;
    }
    private static RoboBoardState boardWithPulse(int micros)throws Exception{
        RoboBoardState state=new RoboBoardState(new UUID(191,20));long revision=state.installVerifiedFirmware(
                CRLFirmware.create(new byte[32],new byte[]{1,2,3,4}),0);AvrMachineState machine=new AvrMachineState();
        Field field=AvrMachineState.class.getDeclaredField("mmio");field.setAccessible(true);byte[] mmio=(byte[])field.get(machine);
        mmio[0x86-0x20]=(byte)(39999&255);mmio[0x87-0x20]=(byte)(39999>>>8);
        state.commitRuntimeCheckpoint(state.getGeneration(),revision,AvrCheckpointCodec.encode(machine),
                RoboBoardState.Status.RUNNING,"",true,false,
                Collections.singletonList(new RuntimeProtocol.Gpio(0,9,true,false)),
                Collections.singletonList(new RuntimeProtocol.Pwm(0,9,1,14,8,micros*2,false)),new byte[0]);
        return state;
    }
    private static final CompoundCollisionProbe.WorldView CLEAR=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return true;}public boolean collides(AxisAlignedVolume v){return false;}};
    private static final CompoundCollisionProbe.WorldView COLLIDING=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return true;}public boolean collides(AxisAlignedVolume v){return true;}};
    private static final CompoundCollisionProbe.WorldView UNLOADED=new CompoundCollisionProbe.WorldView(){
        public boolean isLoaded(AxisAlignedVolume v){return false;}public boolean collides(AxisAlignedVolume v){throw new AssertionError();}};
}
