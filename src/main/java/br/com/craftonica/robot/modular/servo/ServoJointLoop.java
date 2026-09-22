package br.com.craftonica.robot.modular.servo;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.joint.JointState;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedSolver;
import br.com.craftonica.tile.RoboBoardState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;

/** Couples powered Servo.h outputs to physical revolute coordinates through torque only. */
public strictfp final class ServoJointLoop {
    public static final int MAX_CHANNELS = 16;
    private final List<Channel> channels;
    private final ServoParameters parameters = ServoParameters.educational();

    public ServoJointLoop(ModularRobotManifest manifest, ComponentCatalog catalog, KinematicAssembly kinematic) {
        if (manifest==null||catalog==null||kinematic==null||!kinematic.isValid())
            throw new IllegalArgumentException("servo joint loop");
        Map<GridVector,ComponentType> components=new HashMap<GridVector,ComponentType>();
        for(br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot module:manifest.getModules())
            components.put(module.localPosition,catalog.require(module.componentTypeId));
        MobileElectricalEvaluation electrical=MobileElectricalEvaluator.evaluate(manifest.getElectricalNetlist());
        List<Channel> values=new ArrayList<Channel>();
        for(KinematicAssembly.Joint joint:kinematic.getJoints()) {
            GridVector drive=joint.driveConnectionPosition;
            if(drive==null||joint.kind!=JointProfile.Kind.REVOLUTE) continue;
            ComponentType type=components.get(drive);
            MobileElectricalEvaluation.ServoBinding binding=electrical.getServos().get(drive);
            if(type==null||type.getActuator()==null||type.getActuator().kind!=ActuatorProfile.Kind.SERVO||binding==null) continue;
            values.add(new Channel(joint,binding));
        }
        if(values.size()>MAX_CHANNELS) throw new IllegalArgumentException("maximum servo joint channels");
        channels=Collections.unmodifiableList(values);
    }

    public State initialState() {
        List<ChannelState> values=new ArrayList<ChannelState>();
        for(Channel ignored:channels) values.add(new ChannelState(ServoState.ambient(parameters),0.0,0.0,ServoStep.Diagnostic.NONE));
        return new State(values);
    }

    public Evaluation evaluate(State previous, JointStateSet joints, RoboBoardState board, double seconds) {
        if(previous==null||joints==null||previous.channels.size()!=channels.size()) throw new IllegalArgumentException("servo state");
        Map<GridVector,JointState> jointStates=new HashMap<GridVector,JointState>();
        for(JointStateSet.Entry entry:joints.getEntries()) jointStates.put(entry.position,entry.state);
        Map<GridVector,Double> efforts=new LinkedHashMap<GridVector,Double>();
        List<ChannelState> next=new ArrayList<ChannelState>();
        for(int i=0;i<channels.size();i++) {
            Channel channel=channels.get(i); ChannelState old=previous.channels.get(i);
            JointState joint=jointStates.get(channel.joint.modulePosition);
            if(joint==null) throw new IllegalArgumentException("missing servo joint");
            double scale=(parameters.maximumAngleRadians-parameters.minimumAngleRadians)
                    /(channel.joint.maximumPosition-channel.joint.minimumPosition);
            double servoPosition=parameters.minimumAngleRadians+(joint.position-channel.joint.minimumPosition)*scale;
            ServoState feedback=new ServoState(servoPosition,joint.velocity*scale,old.servo.targetRadians,
                    old.servo.temperatureCelsius,old.servo.secondsSinceValidSignal,old.servo.hasTarget,old.servo.thermalShutdown);
            Integer pulse=channel.binding.enabled?ServoSignalDecoder.pulseWidthMicros(board,channel.binding.signalRole):null;
            boolean running=board!=null&&board.isRunning();
            ServoStep step=ServoModel.step(parameters,feedback,new ServoInput(channel.binding.enabled,
                    channel.binding.enabled&&running,5.0,pulse,ServoInput.SignalLossPolicy.HOLD,old.loadTorqueNm),seconds);
            efforts.put(channel.joint.modulePosition,Double.valueOf(step.appliedTorqueNm*scale));
            next.add(new ChannelState(step.state,old.loadTorqueNm,step.currentAmps,step.diagnostic));
        }
        return new Evaluation(new State(next),efforts);
    }

    public State applyReactions(State evaluated,List<ArticulatedSolver.JointReaction> reactions) {
        if(evaluated==null||reactions==null||evaluated.channels.size()!=channels.size()) throw new IllegalArgumentException("servo feedback");
        Map<GridVector,ArticulatedSolver.JointReaction> byJoint=new HashMap<GridVector,ArticulatedSolver.JointReaction>();
        for(ArticulatedSolver.JointReaction reaction:reactions) byJoint.put(reaction.jointPosition,reaction);
        List<ChannelState> next=new ArrayList<ChannelState>();
        for(int i=0;i<channels.size();i++) {
            Channel channel=channels.get(i);ChannelState state=evaluated.channels.get(i);
            ArticulatedSolver.JointReaction reaction=byJoint.get(channel.joint.modulePosition);
            double scale=(parameters.maximumAngleRadians-parameters.minimumAngleRadians)
                    /(channel.joint.maximumPosition-channel.joint.minimumPosition);
            double load=reaction==null?0.0:(-reaction.gravityEffort-reaction.stopReaction)/scale;
            double limit=channel.joint.profile.maximumStopReaction/scale;
            load=StrictMath.max(-limit,StrictMath.min(limit,load));
            next.add(new ChannelState(state.servo,load,state.currentAmps,state.diagnostic));
        }
        return new State(next);
    }

    public NBTTagCompound writeState(State state) {
        if(state==null||state.channels.size()!=channels.size()) throw new IllegalArgumentException("servo state");
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);NBTTagList list=new NBTTagList();
        for(ChannelState channel:state.channels){NBTTagCompound value=new NBTTagCompound();
            value.setDouble("Position",channel.servo.positionRadians);value.setDouble("Velocity",channel.servo.velocityRadPerSecond);
            value.setDouble("Target",channel.servo.targetRadians);value.setDouble("Temperature",channel.servo.temperatureCelsius);
            value.setDouble("SignalAge",channel.servo.secondsSinceValidSignal);value.setBoolean("HasTarget",channel.servo.hasTarget);
            value.setBoolean("ThermalShutdown",channel.servo.thermalShutdown);value.setDouble("Load",channel.loadTorqueNm);
            value.setDouble("Current",channel.currentAmps);value.setByte("Diagnostic",(byte)channel.diagnostic.ordinal());list.appendTag(value);}
        tag.setTag("Channels",list);return tag;
    }

    public State readState(NBTTagCompound tag) {
        if(tag==null||tag.getInteger("Schema")!=1) throw new IllegalArgumentException("servo state schema");
        NBTTagList list=tag.getTagList("Channels",10);if(list.tagCount()!=channels.size())throw new IllegalArgumentException("servo channel count");
        List<ChannelState> values=new ArrayList<ChannelState>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound value=list.getCompoundTagAt(i);int diagnostic=value.getByte("Diagnostic");
            if(diagnostic<0||diagnostic>=ServoStep.Diagnostic.values().length)throw new IllegalArgumentException("servo diagnostic");
            ServoState servo=new ServoState(value.getDouble("Position"),value.getDouble("Velocity"),value.getDouble("Target"),
                    value.getDouble("Temperature"),value.getDouble("SignalAge"),value.getBoolean("HasTarget"),value.getBoolean("ThermalShutdown"));
            double load=value.getDouble("Load"),current=value.getDouble("Current");
            if(!finite(load)||StrictMath.abs(load)>1000.0||!finite(current)||current<0.0||current>parameters.maximumCurrentAmps
                    ||servo.positionRadians<parameters.minimumAngleRadians||servo.positionRadians>parameters.maximumAngleRadians
                    ||servo.targetRadians<parameters.minimumAngleRadians||servo.targetRadians>parameters.maximumAngleRadians
                    ||StrictMath.abs(servo.velocityRadPerSecond)>parameters.maximumVelocityRadPerSecond
                    ||servo.temperatureCelsius<parameters.ambientTemperatureCelsius||servo.temperatureCelsius>10000.0
                    ||servo.secondsSinceValidSignal>1000000.0)throw new IllegalArgumentException("servo feedback");
            values.add(new ChannelState(servo,load,current,ServoStep.Diagnostic.values()[diagnostic]));}
        return new State(values);
    }

    public int channelCount(){return channels.size();}
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    private static final class Channel {final KinematicAssembly.Joint joint;final MobileElectricalEvaluation.ServoBinding binding;
        Channel(KinematicAssembly.Joint joint,MobileElectricalEvaluation.ServoBinding binding){this.joint=joint;this.binding=binding;}}
    public static final class ChannelState {public final ServoState servo;public final double loadTorqueNm,currentAmps;public final ServoStep.Diagnostic diagnostic;
        ChannelState(ServoState servo,double load,double current,ServoStep.Diagnostic diagnostic){this.servo=servo;this.loadTorqueNm=load;this.currentAmps=current;this.diagnostic=diagnostic;}}
    public static final class State {private final List<ChannelState> channels;State(List<ChannelState> values){channels=Collections.unmodifiableList(new ArrayList<ChannelState>(values));}
        public List<ChannelState> getChannels(){return channels;}}
    public static final class Evaluation {public final State state;public final Map<GridVector,Double> efforts;
        Evaluation(State state,Map<GridVector,Double> efforts){this.state=state;this.efforts=Collections.unmodifiableMap(new LinkedHashMap<GridVector,Double>(efforts));}}
}
