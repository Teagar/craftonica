package br.com.craftonica.robot.modular.sensor;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.MechanicalAssembly;
import br.com.craftonica.robot.modular.assembly.MechanicalAssemblyAnalyzer;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedMechanism;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedPose;
import br.com.craftonica.robot.modular.physics.articulated.RigidTransform3;
import br.com.craftonica.runtime.core.AvrInputs;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;

/** Bounded server-only encoder, contact-switch and educational IMU sampler. */
public strictfp final class MobileMotionSensorSystem {
    public static final int MAX_SENSORS=32;
    private static final double SAMPLE_SECONDS=0.05;
    private final List<Encoder> encoders; private final List<Limit> limits; private final List<Imu> imus;

    public MobileMotionSensorSystem(ModularRobotManifest manifest,ComponentCatalog catalog,
            ArticulatedMechanism mechanism){
        if(manifest==null||catalog==null||mechanism==null)throw new IllegalArgumentException("motion sensors");
        MobileElectricalNetlist netlist=manifest.getElectricalNetlist();
        MechanicalAssembly mechanical=MechanicalAssemblyAnalyzer.analyze(manifest,catalog);
        Map<GridVector,MechanicalAssembly.EncoderTap> taps=new HashMap<GridVector,MechanicalAssembly.EncoderTap>();
        Map<GridVector,GridVector> motors=new HashMap<GridVector,GridVector>();
        for(MechanicalAssembly.DrivePath path:mechanical.getDrives())for(MechanicalAssembly.EncoderTap tap:path.getEncoderTaps()){
            if(taps.put(tap.position,tap)!=null)throw new IllegalArgumentException("encoder multiple paths");motors.put(tap.position,path.motorPosition);}
        Map<GridVector,Integer> bodies=new HashMap<GridVector,Integer>();
        for(br.com.craftonica.robot.modular.assembly.KinematicAssembly.Body body:mechanism.getKinematic().getBodies())
            for(GridVector position:body.modulePositions)bodies.put(position,Integer.valueOf(body.bodyId));
        List<Encoder> encoderValues=new ArrayList<Encoder>();List<Limit> limitValues=new ArrayList<Limit>();List<Imu> imuValues=new ArrayList<Imu>();
        for(ModularBlockSnapshot module:manifest.getModules()){
            ComponentType type=catalog.require(module.componentTypeId);SensorProfile profile=type.getSensor();if(profile==null)continue;
            if(profile.kind==SensorProfile.Kind.ROTARY_ENCODER)encoderValues.add(new Encoder(module,taps.get(module.localPosition),
                    motors.get(module.localPosition),powered(netlist,module.localPosition),role(netlist,module.localPosition,"channel_a","D"),
                    role(netlist,module.localPosition,"channel_b","D"),profile));
            if(profile.kind==SensorProfile.Kind.LIMIT_SWITCH)limitValues.add(new Limit(module,
                    body(bodies,module.localPosition),powered(netlist,module.localPosition),
                    role(netlist,module.localPosition,"signal","D"),profile));
            if(profile.kind==SensorProfile.Kind.IMU)imuValues.add(new Imu(module,body(bodies,module.localPosition),
                    powered(netlist,module.localPosition),role(netlist,module.localPosition,"gyro_z","A"),
                    role(netlist,module.localPosition,"accel_x","A"),role(netlist,module.localPosition,"accel_z","A"),profile));
        }
        if(encoderValues.size()+limitValues.size()+imuValues.size()>MAX_SENSORS)throw new IllegalArgumentException("maximum motion sensors");
        encoders=immutable(encoderValues);limits=immutable(limitValues);imus=immutable(imuValues);
    }

    public State initialState(){return new State(new double[encoders.size()],new long[encoders.size()],0,0,0,false);}

    public Sample sample(State before,AvrInputs base,CoupledDriveLoop drive,CoupledDriveLoop.State driveState,
            TerrestrialRigidBodyModel.State rigid,ArticulatedPose pose,CompoundCollisionProbe.WorldView world,long seed){
        if(before==null||base==null||drive==null||driveState==null||rigid==null||pose==null||world==null
                ||before.phase.length!=encoders.size())throw new IllegalArgumentException("motion sample");
        boolean[] digital=base.getDigitalPins();int[] analog=base.getAnalogMicrovolts();boolean[] digitalUsed=new boolean[digital.length];
        boolean[] analogUsed=new boolean[analog.length];double[] phases=before.phase.clone();long[] counts=before.count.clone();
        Status status=Status.OK;
        if(base.getUltrasonic()!=null&&base.getUltrasonic().isEnabled()){
            digitalUsed[base.getUltrasonic().getTriggerPin()]=true;
            digitalUsed[base.getUltrasonic().getEchoPin()]=true;
        }
        for(int i=0;i<encoders.size();i++){
            Encoder encoder=encoders.get(i);if(!encoder.enabled()){status=worse(status,Status.UNAVAILABLE);continue;}
            if(digitalUsed[encoder.pinA]||digitalUsed[encoder.pinB]){status=worse(status,Status.DUPLICATE_PIN);continue;}
            digitalUsed[encoder.pinA]=digitalUsed[encoder.pinB]=true;
            double velocity=encoder.tap.angularVelocity(drive.motorAngularVelocity(encoder.motor,driveState));
            if(StrictMath.abs(velocity)>encoder.maximumVelocity){velocity=StrictMath.copySign(encoder.maximumVelocity,velocity);status=worse(status,Status.SATURATED);}
            phases[i]+=velocity*SAMPLE_SECONDS;long physical=(long)StrictMath.floor(phases[i]/encoder.radiansPerEdge);
            long delta=physical-counts[i];if(StrictMath.abs(delta)>encoder.maximumEdges){delta=delta<0?-encoder.maximumEdges:encoder.maximumEdges;status=worse(status,Status.SATURATED);}
            counts[i]+=delta;int phase=(int)StrictMath.floorMod(counts[i],4L);
            digital[encoder.pinA]=phase==2||phase==3;digital[encoder.pinB]=phase==1||phase==2;
        }
        for(Limit limit:limits){
            if(!limit.enabled()){status=worse(status,Status.UNAVAILABLE);continue;}
            if(digitalUsed[limit.pin]){status=worse(status,Status.DUPLICATE_PIN);continue;}digitalUsed[limit.pin]=true;
            RigidTransform3 transform=pose.getBodyTransforms().get(limit.bodyId);
            Vector3 center=transform.point(new Vector3(limit.position.x+0.5,limit.position.y+0.5,limit.position.z+0.5));
            Vector3 direction=transform.direction(vector(limit.orientation.getForward()));
            double probeHalf=limit.travelMetres*0.5;
            Vector3 tip=RigidTransform3.add(center,RigidTransform3.scale(direction,0.5+probeHalf));
            AxisAlignedVolume probe=new AxisAlignedVolume(tip.x-probeHalf,tip.y-probeHalf,tip.z-probeHalf,
                    tip.x+probeHalf,tip.y+probeHalf,tip.z+probeHalf);
            if(!world.isLoaded(probe)){status=worse(status,Status.UNLOADED);continue;}digital[limit.pin]=world.collides(probe);
        }
        double ax=before.hasVelocity?(rigid.velocityX-before.velocityX)/SAMPLE_SECONDS:0.0;
        double az=before.hasVelocity?(rigid.velocityZ-before.velocityZ)/SAMPLE_SECONDS:0.0;
        for(Imu imu:imus){
            if(!imu.enabled()){status=worse(status,Status.UNAVAILABLE);continue;}
            int[] pins={imu.gyro,imu.accelX,imu.accelZ};boolean duplicate=false;
            for(int pin:pins)if(analogUsed[pin])duplicate=true;
            if(duplicate){status=worse(status,Status.DUPLICATE_PIN);continue;}for(int pin:pins)analogUsed[pin]=true;
            RigidTransform3 transform=pose.getBodyTransforms().get(imu.bodyId);
            Vector3 acceleration=new Vector3(ax,0.0,az);
            Vector3 angularVelocity=new Vector3(0.0,rigid.angularVelocityRadiansPerSecond,0.0);
            Vector3 right=transform.direction(vector(imu.orientation.getRight()));
            Vector3 forward=transform.direction(vector(imu.orientation.getForward()));
            Vector3 up=transform.direction(vector(imu.orientation.getUp()));
            double sensedX=RigidTransform3.dot(acceleration,right);
            double sensedZ=RigidTransform3.dot(acceleration,forward);
            double sensedGyro=RigidTransform3.dot(angularVelocity,up);
            Value gyro=encode(sensedGyro,imu.gyroRange,imu.noise,imu.levels,seed^positionSeed(imu.position));
            Value outX=encode(sensedX,imu.accelRange,imu.noise,imu.levels,seed^positionSeed(imu.position)^0x51L);
            Value outZ=encode(sensedZ,imu.accelRange,imu.noise,imu.levels,seed^positionSeed(imu.position)^0xa3L);
            analog[imu.gyro]=gyro.microvolts;analog[imu.accelX]=outX.microvolts;analog[imu.accelZ]=outZ.microvolts;
            if(gyro.saturated||outX.saturated||outZ.saturated)status=worse(status,Status.SATURATED);
        }
        State next=new State(phases,counts,rigid.velocityX,rigid.velocityZ,rigid.angularVelocityRadiansPerSecond,true);
        return new Sample(next,new AvrInputs(digital,analog,base.getUltrasonic()),status);
    }

    public NBTTagCompound write(State state){if(state==null||state.phase.length!=encoders.size())throw new IllegalArgumentException("motion state");
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);NBTTagList values=new NBTTagList();
        for(int i=0;i<state.phase.length;i++){NBTTagCompound value=new NBTTagCompound();value.setDouble("Phase",state.phase[i]);value.setLong("Count",state.count[i]);values.appendTag(value);}tag.setTag("Encoders",values);
        tag.setDouble("VelocityX",state.velocityX);tag.setDouble("VelocityZ",state.velocityZ);tag.setBoolean("HasVelocity",state.hasVelocity);return tag;}
    public State read(NBTTagCompound tag){if(tag==null||tag.getInteger("Schema")!=1)throw new IllegalArgumentException("motion state schema");NBTTagList values=tag.getTagList("Encoders",10);
        if(values.tagCount()!=encoders.size())throw new IllegalArgumentException("motion encoder count");double[] phase=new double[values.tagCount()];long[] count=new long[values.tagCount()];
        for(int i=0;i<values.tagCount();i++){NBTTagCompound value=values.getCompoundTagAt(i);phase[i]=value.getDouble("Phase");count[i]=value.getLong("Count");if(!finite(phase[i])||StrictMath.abs(phase[i])>1.0e12)throw new IllegalArgumentException("motion phase");}
        double vx=tag.getDouble("VelocityX"),vz=tag.getDouble("VelocityZ");if(!finite(vx)||!finite(vz))throw new IllegalArgumentException("motion velocity");return new State(phase,count,vx,vz,0.0,tag.getBoolean("HasVelocity"));}

    private static Value encode(double value,double range,double noise,int levels,long seed){boolean saturated=StrictMath.abs(value)>range;value=StrictMath.max(-range,StrictMath.min(range,value));
        double unit=noise(seed)*noise;double normalized=StrictMath.max(0.0,StrictMath.min(1.0,0.5+0.5*value/range+unit));int code=(int)StrictMath.round(normalized*(levels-1));
        return new Value((int)StrictMath.round(code*5000000.0/(levels-1)),saturated);}
    private static double noise(long seed){seed^=seed>>>33;seed*=0xff51afd7ed558ccdL;seed^=seed>>>33;seed*=0xc4ceb9fe1a85ec53L;seed^=seed>>>33;return ((seed>>>11)*(1.0/(1L<<53)))*2.0-1.0;}
    private static boolean powered(MobileElectricalNetlist n,GridVector p){return connected(n,p,"vcc",StandardComponentCatalog.POWER_SOURCE,"POWER_5V")&&connected(n,p,"gnd",StandardComponentCatalog.GROUND,"GROUND");}
    private static boolean connected(MobileElectricalNetlist n,GridVector p,String port,String type,String role){Integer network=n.network(p,port);if(network==null)return false;for(MobileTerminal t:n.terminalsOn(network))if(!t.modulePosition.equals(p)&&(type.equals(t.componentTypeId)||StandardComponentCatalog.ROBO_PORT.equals(t.componentTypeId)&&role.equals(t.role)))return true;return false;}
    private static int role(MobileElectricalNetlist n,GridVector p,String port,String prefix){Integer network=n.network(p,port);if(network==null)return -1;int found=-1;for(MobileTerminal t:n.terminalsOn(network)){if(!StandardComponentCatalog.ROBO_PORT.equals(t.componentTypeId)||!t.role.matches(prefix+"[0-9]+"))continue;int value=Integer.parseInt(t.role.substring(1));if(found>=0&&found!=value)return -1;found=value;}return found;}
    private static int body(Map<GridVector,Integer> bodies,GridVector position){Integer value=bodies.get(position);return value==null?0:value;}
    private static Vector3 vector(Direction d){return new Vector3(d.vector.x,d.vector.y,d.vector.z);}private static long positionSeed(GridVector p){return (long)p.x*73856093L^(long)p.y*19349663L^(long)p.z*83492791L;}
    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}private static Status worse(Status a,Status b){return a.ordinal()>=b.ordinal()?a:b;}
    private static <T> List<T> immutable(List<T> value){return Collections.unmodifiableList(new ArrayList<T>(value));}
    public enum Status{OK,UNAVAILABLE,UNLOADED,DUPLICATE_PIN,SATURATED}
    public static final class State{final double[] phase;final long[] count;final double velocityX,velocityZ,angularVelocity;final boolean hasVelocity;
        State(double[] phase,long[] count,double vx,double vz,double av,boolean has){this.phase=phase.clone();this.count=count.clone();this.velocityX=vx;this.velocityZ=vz;this.angularVelocity=av;this.hasVelocity=has;}}
    public static final class Sample{public final State state;public final AvrInputs inputs;public final Status status;Sample(State s,AvrInputs i,Status x){state=s;inputs=i;status=x;}}
    private static final class Encoder{final GridVector position,motor;final MechanicalAssembly.EncoderTap tap;final int pinA,pinB,maximumEdges;final double radiansPerEdge,maximumVelocity;
        Encoder(ModularBlockSnapshot m,MechanicalAssembly.EncoderTap t,GridVector motor,boolean power,int a,int b,SensorProfile p){position=m.localPosition;this.motor=motor;tap=t;pinA=a;pinB=b;maximumEdges=(int)param(p,"maximum_edges_per_sample");radiansPerEdge=StrictMath.PI*2.0/(param(p,"pulses_per_revolution")*4.0);maximumVelocity=param(p,"maximum_angular_velocity_rad_per_second");this.power=power;}final boolean power;boolean enabled(){return power&&tap!=null&&motor!=null&&pinA>=0&&pinA<14&&pinB>=0&&pinB<14&&pinA!=pinB;}}
    private static final class Limit{final GridVector position;final ComponentOrientation orientation;final int bodyId,pin;final boolean power;final double travelMetres;Limit(ModularBlockSnapshot m,int body,boolean power,int pin,SensorProfile profile){position=m.localPosition;orientation=m.localOrientation;bodyId=body;this.power=power;this.pin=pin;travelMetres=param(profile,"travel_metres");}boolean enabled(){return power&&pin>=0&&pin<14;}}
    private static final class Imu{final GridVector position;final ComponentOrientation orientation;final int bodyId,gyro,accelX,accelZ,levels;final boolean power;final double gyroRange,accelRange,noise;
        Imu(ModularBlockSnapshot m,int body,boolean power,int g,int x,int z,SensorProfile p){position=m.localPosition;orientation=m.localOrientation;bodyId=body;this.power=power;gyro=g;accelX=x;accelZ=z;gyroRange=param(p,"gyro_range_rad_per_second");accelRange=param(p,"acceleration_range_metres_per_second_squared");noise=param(p,"noise_fraction");levels=(int)param(p,"adc_levels");}boolean enabled(){return power&&gyro>=0&&gyro<6&&accelX>=0&&accelX<6&&accelZ>=0&&accelZ<6&&gyro!=accelX&&gyro!=accelZ&&accelX!=accelZ;}}
    private static final class Value{final int microvolts;final boolean saturated;Value(int v,boolean s){microvolts=v;saturated=s;}}
    private static double param(SensorProfile p,String key){Double v=p.getParameters().get(key);if(v==null||v<=0.0)throw new IllegalArgumentException(key);return v;}
}
