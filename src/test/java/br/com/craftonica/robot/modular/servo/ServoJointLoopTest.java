package br.com.craftonica.robot.modular.servo;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.tile.RoboBoardState;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

public final class ServoJointLoopTest {
    @Test public void validPoweredTimerPulseProducesTorqueNotPositionAssignment() throws Exception {
        ComponentCatalog catalog=StandardComponentCatalog.create();ModularRobotManifest manifest=manifest();
        KinematicAssembly kinematic=KinematicAssemblyAnalyzer.analyze(manifest,catalog);
        ServoJointLoop loop=new ServoJointLoop(manifest,catalog,kinematic);
        JointStateSet joints=JointStateSet.initial(kinematic);
        ServoJointLoop.Evaluation result=loop.evaluate(loop.initialState(),joints,boardWithPulse(1000),0.025);

        assertEquals(1,loop.channelCount());
        assertTrue(result.efforts.get(new GridVector(0,0,1)).doubleValue()<0.0);
        assertEquals(0.0,joints.getEntries().get(0).state.position,0.0);
        ServoJointLoop.State restored=loop.readState(loop.writeState(result.state));
        assertEquals(result.state.getChannels().get(0).servo.temperatureCelsius,
                restored.getChannels().get(0).servo.temperatureCelsius,0.0);
    }

    @Test public void absentPowerProducesNoTorqueAndExplicitDiagnostic() {
        ComponentCatalog catalog=StandardComponentCatalog.create();ModularRobotManifest manifest=manifest();
        KinematicAssembly kinematic=KinematicAssemblyAnalyzer.analyze(manifest,catalog);
        ServoJointLoop loop=new ServoJointLoop(manifest,catalog,kinematic);
        ServoJointLoop.Evaluation result=loop.evaluate(loop.initialState(),JointStateSet.initial(kinematic),null,0.025);
        assertEquals(0.0,result.efforts.get(new GridVector(0,0,1)).doubleValue(),0.0);
        assertEquals(ServoStep.Diagnostic.UNPOWERED,result.state.getChannels().get(0).diagnostic);
    }

    private static ModularRobotManifest manifest(){
        GridVector parent=p(0,0,0),joint=p(0,0,1),child=p(0,0,2),servo=p(-1,0,1);
        List<ModularBlockSnapshot> modules=Arrays.asList(module(StandardComponentCatalog.CHASSIS,parent,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.REVOLUTE_JOINT,joint,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,child,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.SERVO,servo,new ComponentOrientation(Direction.EAST,Direction.UP)),
                module(StandardComponentCatalog.CHASSIS,p(-1,-1,1),ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,p(-1,-1,0),ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,p(0,-1,0),ComponentOrientation.NORTH_UP));
        List<AssemblyEdge> edges=Arrays.asList(
                new AssemblyEdge(AssemblyEdge.Kind.JOINT,parent,"mount_south",joint,"parent"),
                new AssemblyEdge(AssemblyEdge.Kind.JOINT,joint,"child",child,"mount_north"),
                new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,joint,"drive",servo,"output"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,servo,"mount_down",p(-1,-1,1),"mount_up"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(-1,-1,1),"mount_north",p(-1,-1,0),"mount_south"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(-1,-1,0),"mount_east",p(0,-1,0),"mount_west"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(0,-1,0),"mount_up",parent,"mount_down"));
        List<MobileTerminal> terminals=new ArrayList<MobileTerminal>();
        terminals.add(t(servo,StandardComponentCatalog.SERVO,"vcc",0,""));terminals.add(t(p(-2,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",0,"POWER_5V"));
        terminals.add(t(servo,StandardComponentCatalog.SERVO,"gnd",1,""));terminals.add(t(p(-2,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",1,"GROUND"));
        terminals.add(t(servo,StandardComponentCatalog.SERVO,"signal",2,""));terminals.add(t(p(-2,0,2),StandardComponentCatalog.ROBO_PORT,"terminal",2,"D9"));
        return new ModularRobotManifest(new UUID(100,101),modules,edges,new MobileElectricalNetlist(terminals));
    }
    private static RoboBoardState boardWithPulse(int micros)throws Exception{
        RoboBoardState state=new RoboBoardState(new UUID(102,103));long revision=state.installVerifiedFirmware(CRLFirmware.create(new byte[32],new byte[]{1,2,3,4}),0);
        AvrMachineState machine=new AvrMachineState();Field field=AvrMachineState.class.getDeclaredField("mmio");field.setAccessible(true);byte[] mmio=(byte[])field.get(machine);
        mmio[0x86-0x20]=(byte)(39999&255);mmio[0x87-0x20]=(byte)(39999>>>8);
        state.commitRuntimeCheckpoint(state.getGeneration(),revision,AvrCheckpointCodec.encode(machine),RoboBoardState.Status.RUNNING,"",true,false,
                Collections.singletonList(new RuntimeProtocol.Gpio(0,9,true,false)),
                Collections.singletonList(new RuntimeProtocol.Pwm(0,9,1,14,8,micros*2,false)),new byte[0]);return state;
    }
    private static MobileTerminal t(GridVector p,String type,String port,int net,String role){return new MobileTerminal(p,type,port,Direction.UP,role,net);}
    private static ModularBlockSnapshot module(String type,GridVector p,ComponentOrientation o){return new ModularBlockSnapshot(type,1,p,o,type,0,null);}
    private static GridVector p(int x,int y,int z){return new GridVector(x,y,z);}
}
