package br.com.craftonica.robot.modular.sensor;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.drive.*;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.*;
import br.com.craftonica.robot.modular.physics.articulated.*;
import br.com.craftonica.runtime.core.AvrInputs;
import org.junit.Test;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

import static org.junit.Assert.*;

public final class MobileMotionSensorSystemTest {
    private final ComponentCatalog catalog=StandardComponentCatalog.create();

    @Test public void poweredImuIsQuantizedDeterministicAndSaturatesInsteadOfReportingPerfectValue(){
        ModularRobotManifest manifest=singleSensor(StandardComponentCatalog.IMU,imuTerminals(true));
        Fixture fixture=new Fixture(manifest);TerrestrialRigidBodyModel.State stationary=body(0,0,0);
        MobileMotionSensorSystem.Sample first=fixture.sample(stationary,CLEAR,123);
        MobileMotionSensorSystem.Sample repeat=fixture.sample(stationary,CLEAR,123);
        assertEquals(first.inputs.getAnalogMicrovolts(0),repeat.inputs.getAnalogMicrovolts(0));
        assertTrue(StrictMath.abs(first.inputs.getAnalogMicrovolts(0)-2500000)<30000);
        MobileMotionSensorSystem.Sample saturated=fixture.sample(body(0,0,20),CLEAR,124);
        assertEquals(MobileMotionSensorSystem.Status.SATURATED,saturated.status);
        assertTrue(saturated.inputs.getAnalogMicrovolts(0)>4900000);
    }

    @Test public void unpoweredSensorReturnsAbsentReading(){
        Fixture fixture=new Fixture(singleSensor(StandardComponentCatalog.IMU,imuTerminals(false)));
        MobileMotionSensorSystem.Sample sample=fixture.sample(body(2,3,1),CLEAR,10);
        assertEquals(MobileMotionSensorSystem.Status.UNAVAILABLE,sample.status);
        assertArrayEquals(new int[6],sample.inputs.getAnalogMicrovolts());
    }

    @Test public void limitSwitchReadsPhysicalLoadedContactAndFailsClosedAtBoundary(){
        Fixture fixture=new Fixture(singleSensor(StandardComponentCatalog.LIMIT_SWITCH,limitTerminals()));
        MobileMotionSensorSystem.Sample clear=fixture.sample(body(0,0,0),CLEAR,1);
        MobileMotionSensorSystem.Sample pressed=fixture.sample(body(0,0,0),COLLIDING,2);
        MobileMotionSensorSystem.Sample unloaded=fixture.sample(body(0,0,0),UNLOADED,3);
        assertFalse(clear.inputs.isDigitalHigh(4));assertTrue(pressed.inputs.isDigitalHigh(4));
        assertFalse(unloaded.inputs.isDigitalHigh(4));assertEquals(MobileMotionSensorSystem.Status.UNLOADED,unloaded.status);
    }

    @Test public void encoderFollowsMechanicalShaftWithBoundedQuadratureEdgesAndPersists(){
        ModularRobotManifest manifest=encoderManifest(true);Fixture fixture=new Fixture(manifest);
        assertEquals(1,fixture.system.write(fixture.state).getTagList("Encoders",10).tagCount());
        CoupledDriveLoop.ControlFrame frame=new CoupledDriveLoop.ControlFrame(0,Collections.singletonMap(p(-2,0,0),
                new DriveInput(true,5.0,255,DriveInput.Mode.FORWARD,0.0)));
        CoupledDriveLoop.Result drive=fixture.drive.step(fixture.driveState,frame,body(0,0,0),
                Collections.singletonList(0),new SimulationTickBudget(),0.05);
        fixture.driveState=drive.state;
        assertTrue(fixture.drive.motorAngularVelocity(p(0,0,0),fixture.driveState)>1.0);
        MobileMotionSensorSystem.Sample sample=fixture.sample(body(0,0,0),CLEAR,4);
        NBTTagCompound encoded=fixture.system.write(sample.state).getTagList("Encoders",10).getCompoundTagAt(0);
        assertTrue("phase",encoded.getDouble("Phase")>0.1);assertEquals(1L,encoded.getLong("Count"));
        assertTrue(sample.inputs.isDigitalHigh(2)!=sample.inputs.isDigitalHigh(3));
        MobileMotionSensorSystem.State restored=fixture.system.read(fixture.system.write(sample.state));
        assertNotNull(restored);
    }

    private final class Fixture{
        final ModularRobotManifest manifest;final ArticulatedMechanism mechanism;final MobileMotionSensorSystem system;
        final CoupledDriveLoop drive;CoupledDriveLoop.State driveState;MobileMotionSensorSystem.State state;
        Fixture(ModularRobotManifest manifest){this.manifest=manifest;mechanism=new ArticulatedMechanism(manifest,catalog);
            system=new MobileMotionSensorSystem(manifest,catalog,mechanism);drive=new CoupledDriveLoop(manifest,catalog);
            driveState=drive.initialState();state=system.initialState();}
        MobileMotionSensorSystem.Sample sample(TerrestrialRigidBodyModel.State body,CompoundCollisionProbe.WorldView world,long seed){
            ArticulatedPose pose=mechanism.pose(JointStateSet.initial(mechanism.getKinematic()),RigidTransform3.identity());
            MobileMotionSensorSystem.Sample value=system.sample(state,AvrInputs.allLow(),drive,driveState,body,pose,world,seed);state=value.state;return value;}
    }
    private static ModularRobotManifest singleSensor(String type,List<MobileTerminal> terminals){
        return new ModularRobotManifest(new UUID(30,type.hashCode()),Collections.singletonList(module(type,p(0,0,0))),
                Collections.<AssemblyEdge>emptyList(),new MobileElectricalNetlist(terminals));}
    private static ModularRobotManifest encoderManifest(boolean powered){
        GridVector motor=p(0,0,0),encoder=p(0,0,1),axle=p(0,0,2),wheel=p(0,0,3),bridge=p(-2,0,0);
        List<ModularBlockSnapshot> modules=Arrays.asList(module(StandardComponentCatalog.DC_MOTOR,motor),
                module(StandardComponentCatalog.ENCODER,encoder),module(StandardComponentCatalog.AXLE,axle),
                new ModularBlockSnapshot(StandardComponentCatalog.WHEEL,1,wheel,new ComponentOrientation(Direction.EAST,Direction.UP),StandardComponentCatalog.WHEEL,0,null));
        List<AssemblyEdge> edges=Arrays.asList(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,motor,"shaft",encoder,"shaft_in"),
                new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,encoder,"shaft_out",axle,"shaft_in"),
                new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,axle,"shaft_out",wheel,"hub"));
        List<MobileTerminal> t=driveTerminals(bridge,motor);t.addAll(sensorPower(encoder,StandardComponentCatalog.ENCODER,powered));
        t.add(term(encoder,StandardComponentCatalog.ENCODER,"channel_a",8,""));t.add(term(p(8,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",8,"D2"));
        t.add(term(encoder,StandardComponentCatalog.ENCODER,"channel_b",9,""));t.add(term(p(8,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",9,"D3"));
        return new ModularRobotManifest(new UUID(31,32),modules,edges,new MobileElectricalNetlist(t));
    }
    private static List<MobileTerminal> imuTerminals(boolean powered){GridVector position=p(0,0,0);List<MobileTerminal> t=sensorPower(position,StandardComponentCatalog.IMU,powered);
        t.add(term(position,StandardComponentCatalog.IMU,"gyro_z",2,""));t.add(term(p(3,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",2,"A0"));
        t.add(term(position,StandardComponentCatalog.IMU,"accel_x",3,""));t.add(term(p(3,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",3,"A1"));
        t.add(term(position,StandardComponentCatalog.IMU,"accel_z",4,""));t.add(term(p(3,0,2),StandardComponentCatalog.ROBO_PORT,"terminal",4,"A2"));return t;}
    private static List<MobileTerminal> limitTerminals(){GridVector position=p(0,0,0);List<MobileTerminal> t=sensorPower(position,StandardComponentCatalog.LIMIT_SWITCH,true);
        t.add(term(position,StandardComponentCatalog.LIMIT_SWITCH,"signal",2,""));t.add(term(p(4,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",2,"D4"));return t;}
    private static List<MobileTerminal> sensorPower(GridVector position,String type,boolean powered){List<MobileTerminal> t=new ArrayList<MobileTerminal>();
        t.add(term(position,type,"vcc",0,""));if(powered)t.add(term(p(9,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",0,"POWER_5V"));
        t.add(term(position,type,"gnd",1,""));if(powered)t.add(term(p(9,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",1,"GROUND"));return t;}
    private static List<MobileTerminal> driveTerminals(GridVector b,GridVector m){List<MobileTerminal> t=new ArrayList<MobileTerminal>();
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"vcc",2,""));t.add(term(p(-4,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",2,"POWER_5V"));
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"gnd",3,""));t.add(term(p(-4,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",3,"GROUND"));
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"pwm",4,""));t.add(term(p(-4,0,2),StandardComponentCatalog.ROBO_PORT,"terminal",4,"D5"));
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"direction",5,""));t.add(term(p(-4,0,3),StandardComponentCatalog.ROBO_PORT,"terminal",5,"D8"));
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"out_a",6,""));t.add(term(m,StandardComponentCatalog.DC_MOTOR,"motor_positive",6,""));
        t.add(term(b,StandardComponentCatalog.H_BRIDGE,"out_b",7,""));t.add(term(m,StandardComponentCatalog.DC_MOTOR,"motor_negative",7,""));return t;}
    private static TerrestrialRigidBodyModel.State body(double vx,double vz,double yawRate){return new TerrestrialRigidBodyModel.State(0,0,0,0,vx,0,vz,yawRate);}
    private static ModularBlockSnapshot module(String type,GridVector p){return new ModularBlockSnapshot(type,1,p,ComponentOrientation.NORTH_UP,type,0,null);}
    private static MobileTerminal term(GridVector p,String type,String port,int network,String role){return new MobileTerminal(p,type,port,Direction.UP,role,network);}
    private static GridVector p(int x,int y,int z){return new GridVector(x,y,z);}
    private static final CompoundCollisionProbe.WorldView CLEAR=new CompoundCollisionProbe.WorldView(){public boolean isLoaded(AxisAlignedVolume v){return true;}public boolean collides(AxisAlignedVolume v){return false;}};
    private static final CompoundCollisionProbe.WorldView COLLIDING=new CompoundCollisionProbe.WorldView(){public boolean isLoaded(AxisAlignedVolume v){return true;}public boolean collides(AxisAlignedVolume v){return true;}};
    private static final CompoundCollisionProbe.WorldView UNLOADED=new CompoundCollisionProbe.WorldView(){public boolean isLoaded(AxisAlignedVolume v){return false;}public boolean collides(AxisAlignedVolume v){throw new AssertionError();}};
}
