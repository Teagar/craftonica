package br.com.craftonica.robot.modular.validation;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Public-component-only fixtures. Production code never recognizes these layouts by name. */
final class AdvancedMechanismFixtures {
    private AdvancedMechanismFixtures() { }

    static ModularRobotManifest armWithGripper() {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        List<MobileTerminal> terminals = new ArrayList<MobileTerminal>();
        GridVector root=p(0,0,0), shoulder=p(0,0,1), upper=p(0,0,2), elbow=p(0,0,3);
        GridVector forearm=p(0,0,4),palm=p(0,0,5);
        modules.add(module(StandardComponentCatalog.CHASSIS,root,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.REVOLUTE_JOINT,shoulder,verticalHinge()));
        modules.add(module(StandardComponentCatalog.CHASSIS,upper,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.REVOLUTE_JOINT,elbow,verticalHinge()));
        modules.add(module(StandardComponentCatalog.CHASSIS,forearm,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.CHASSIS,palm,ComponentOrientation.NORTH_UP));
        edges.add(joint(root,shoulder));edges.add(jointChild(shoulder,upper));
        edges.add(joint(upper,elbow));edges.add(jointChild(elbow,forearm));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,forearm,"mount_south",palm,"mount_north"));
        addServo(0,p(-1,0,1),new ComponentOrientation(Direction.EAST,Direction.UP),shoulder,
                new GridVector[]{p(-1,-1,1),p(-1,-1,0),p(0,-1,0),root},modules,edges,terminals);
        addServo(1,p(-1,0,3),new ComponentOrientation(Direction.EAST,Direction.UP),elbow,
                new GridVector[]{p(-1,-1,3),p(-1,-1,2),p(0,-1,2),upper},modules,edges,terminals);

        GridVector leftJoint=p(-1,0,5),leftFinger=p(-2,0,5),rightJoint=p(1,0,5),rightFinger=p(2,0,5);
        modules.add(module(StandardComponentCatalog.REVOLUTE_JOINT,leftJoint,new ComponentOrientation(Direction.EAST,Direction.UP)));
        modules.add(module(StandardComponentCatalog.CHASSIS,leftFinger,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.REVOLUTE_JOINT,rightJoint,new ComponentOrientation(Direction.WEST,Direction.UP)));
        modules.add(module(StandardComponentCatalog.CHASSIS,rightFinger,ComponentOrientation.NORTH_UP));
        edges.add(joint(palm,leftJoint));edges.add(jointChild(leftJoint,leftFinger));
        edges.add(joint(palm,rightJoint));edges.add(jointChild(rightJoint,rightFinger));
        addServo(2,p(-1,0,4),new ComponentOrientation(Direction.SOUTH,Direction.UP),leftJoint,
                new GridVector[]{p(-1,-1,4),p(0,-1,4),forearm},modules,edges,terminals);
        addServo(3,p(1,0,6),ComponentOrientation.NORTH_UP,rightJoint,
                new GridVector[]{p(1,-1,6),p(0,-1,6),p(0,-1,5),palm},modules,edges,terminals);
        return new ModularRobotManifest(new UUID(191,1),modules,edges,new MobileElectricalNetlist(terminals));
    }

    static ModularRobotManifest linearMechanism() {
        GridVector root=p(0,0,0),joint=p(0,0,1),carriage=p(0,0,2),limit=p(0,1,0),actuator=p(-1,0,1);
        List<ModularBlockSnapshot> modules=Arrays.asList(
                module(StandardComponentCatalog.CHASSIS,root,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.PRISMATIC_JOINT,joint,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,carriage,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.LIMIT_SWITCH,limit,ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.LINEAR_SERVO,actuator,new ComponentOrientation(Direction.EAST,Direction.UP)),
                module(StandardComponentCatalog.CHASSIS,p(-1,-1,1),ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,p(-1,-1,0),ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.CHASSIS,p(0,-1,0),ComponentOrientation.NORTH_UP));
        List<AssemblyEdge> edges=Arrays.asList(joint(root,joint),jointChild(joint,carriage),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,root,"mount_up",limit,"mount_down"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,actuator,"mount_down",p(-1,-1,1),"mount_up"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(-1,-1,1),"mount_north",p(-1,-1,0),"mount_south"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(-1,-1,0),"mount_east",p(0,-1,0),"mount_west"),
                new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,p(0,-1,0),"mount_up",root,"mount_down"),
                new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,joint,"drive",actuator,"output"));
        List<MobileTerminal> terminals=new ArrayList<MobileTerminal>();
        sensorPower(terminals,limit,StandardComponentCatalog.LIMIT_SWITCH);
        terminals.add(t(limit,StandardComponentCatalog.LIMIT_SWITCH,"signal",2,""));
        terminals.add(t(p(4,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",2,"D4"));
        terminals.add(t(actuator,StandardComponentCatalog.LINEAR_SERVO,"vcc",0,""));
        terminals.add(t(actuator,StandardComponentCatalog.LINEAR_SERVO,"gnd",1,""));
        terminals.add(t(actuator,StandardComponentCatalog.LINEAR_SERVO,"signal",3,""));
        terminals.add(t(p(4,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",3,"D9"));
        return new ModularRobotManifest(new UUID(191,2),modules,edges,new MobileElectricalNetlist(terminals));
    }

    static ModularRobotManifest trackedBase() {
        return driveBase(new String[] {StandardComponentCatalog.TRACK_MODULE,StandardComponentCatalog.TRACK_MODULE},
                new int[][] {{-2,0,0},{2,0,0}},new ComponentOrientation[] {
                        new ComponentOrientation(Direction.EAST,Direction.UP),
                        new ComponentOrientation(Direction.WEST,Direction.UP)},true,new UUID(191,3));
    }

    static ModularRobotManifest mecanumBase() {
        return driveBase(new String[] {StandardComponentCatalog.MECANUM_LEFT,StandardComponentCatalog.MECANUM_RIGHT,
                        StandardComponentCatalog.MECANUM_RIGHT,StandardComponentCatalog.MECANUM_LEFT},
                new int[][] {{-2,0,-2},{2,0,-2},{-2,0,2},{2,0,2}},new ComponentOrientation[] {
                        wheelOrientation(),wheelOrientation(),wheelOrientation(),wheelOrientation()},false,new UUID(191,4));
    }

    private static ModularRobotManifest driveBase(String[] contacts,int[][] positions,
            ComponentOrientation[] orientations,boolean tracks,UUID id) {
        List<ModularBlockSnapshot> modules=new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges=new ArrayList<AssemblyEdge>();List<MobileTerminal> terminals=new ArrayList<MobileTerminal>();
        GridVector root=p(0,-2,0);modules.add(module(StandardComponentCatalog.CHASSIS,root,ComponentOrientation.NORTH_UP));
        terminals.add(t(p(9,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",0,"POWER_5V"));
        terminals.add(t(p(9,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",1,"GROUND"));
        for(int i=0;i<contacts.length;i++){
            GridVector contact=p(positions[i][0],positions[i][1],positions[i][2]);
            Direction hub=orientations[i].toWorld(Direction.WEST);
            ComponentOrientation shaftOrientation=new ComponentOrientation(hub,Direction.UP);
            GridVector axle=contact.add(hub.vector),motor=axle.add(hub.vector),bridge=p(-6+i*3,3,6);
            modules.add(module(contacts[i],contact,orientations[i]));modules.add(module(StandardComponentCatalog.AXLE,axle,shaftOrientation));
            modules.add(module(StandardComponentCatalog.DC_MOTOR,motor,shaftOrientation));modules.add(module(StandardComponentCatalog.H_BRIDGE,bridge,ComponentOrientation.NORTH_UP));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,motor,"shaft",axle,"shaft_in"));
            edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,axle,"shaft_out",contact,tracks?"sprocket":"hub"));
            if(tracks){GridVector mount=contact.add(Direction.UP.vector);addChassis(modules,mount);
                edges.add(new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,contact,"mount_up",mount,"mount_down"));}
            connectMotorMount(motor,root,modules,edges);
            terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"vcc",0,""));terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"gnd",1,""));
            int base=2+i*4;terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"pwm",base,""));
            terminals.add(t(p(8,i,0),StandardComponentCatalog.ROBO_PORT,"terminal",base,"D"+(2+i*2)));
            terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"direction",base+1,""));
            terminals.add(t(p(8,i,1),StandardComponentCatalog.ROBO_PORT,"terminal",base+1,"D"+(3+i*2)));
            terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"out_a",base+2,""));
            terminals.add(t(motor,StandardComponentCatalog.DC_MOTOR,"motor_positive",base+2,""));
            terminals.add(t(bridge,StandardComponentCatalog.H_BRIDGE,"out_b",base+3,""));
            terminals.add(t(motor,StandardComponentCatalog.DC_MOTOR,"motor_negative",base+3,""));
        }
        return new ModularRobotManifest(id,modules,edges,new MobileElectricalNetlist(terminals));
    }

    private static void addServo(int index,GridVector servo,ComponentOrientation orientation,GridVector joint,
            GridVector[] supportPath,
            List<ModularBlockSnapshot> modules,List<AssemblyEdge> edges,List<MobileTerminal> terminals){
        modules.add(module(StandardComponentCatalog.SERVO,servo,orientation));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,joint,"drive",servo,"output"));
        GridVector previous=servo;
        for(int i=0;i<supportPath.length;i++){
            GridVector support=supportPath[i];
            if(i<supportPath.length-1)modules.add(module(StandardComponentCatalog.CHASSIS,support,ComponentOrientation.NORTH_UP));
            edges.add(i==0
                    ?new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,previous,"mount_down",support,"mount_up")
                    :new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,previous,mountPort(previous,support),
                            support,mountPort(support,previous)));
            previous=support;
        }
        terminals.add(t(servo,StandardComponentCatalog.SERVO,"vcc",0,""));
        terminals.add(t(p(7,index,0),StandardComponentCatalog.ROBO_PORT,"terminal",0,"POWER_5V"));
        terminals.add(t(servo,StandardComponentCatalog.SERVO,"gnd",1,""));
        terminals.add(t(p(7,index,1),StandardComponentCatalog.ROBO_PORT,"terminal",1,"GROUND"));
        int network=2+index;terminals.add(t(servo,StandardComponentCatalog.SERVO,"signal",network,""));
        terminals.add(t(p(7,index,2),StandardComponentCatalog.ROBO_PORT,"terminal",network,"D"+(9+index)));
    }
    private static void sensorPower(List<MobileTerminal> terminals,GridVector sensor,String type){
        terminals.add(t(sensor,type,"vcc",0,""));terminals.add(t(p(5,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",0,"POWER_5V"));
        terminals.add(t(sensor,type,"gnd",1,""));terminals.add(t(p(5,0,1),StandardComponentCatalog.ROBO_PORT,"terminal",1,"GROUND"));
    }
    private static AssemblyEdge joint(GridVector parent,GridVector joint){return new AssemblyEdge(AssemblyEdge.Kind.JOINT,
            parent,mountPort(parent,joint),joint,"parent");}
    private static AssemblyEdge jointChild(GridVector joint,GridVector child){return new AssemblyEdge(AssemblyEdge.Kind.JOINT,
            joint,"child",child,mountPort(child,joint));}
    private static ComponentOrientation verticalHinge(){return ComponentOrientation.NORTH_UP;}
    private static ComponentOrientation wheelOrientation(){return new ComponentOrientation(Direction.EAST,Direction.UP);}
    private static void connectMotorMount(GridVector motor,GridVector root,List<ModularBlockSnapshot> modules,
            List<AssemblyEdge> edges){
        GridVector support=motor.add(Direction.DOWN.vector),cursor=support.add(Direction.DOWN.vector);addChassis(modules,support);addChassis(modules,cursor);
        addEdge(edges,new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,motor,"mount_down",support,"mount_up"));
        addEdge(edges,new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,support,"mount_down",cursor,"mount_up"));
        while(cursor.x!=root.x){GridVector next=cursor.add(p(cursor.x<root.x?1:-1,0,0));addChassis(modules,next);
            addEdge(edges,new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,cursor,mountPort(cursor,next),next,mountPort(next,cursor)));cursor=next;}
        while(cursor.z!=root.z){GridVector next=cursor.add(p(0,0,cursor.z<root.z?1:-1));addChassis(modules,next);
            addEdge(edges,new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,cursor,mountPort(cursor,next),next,mountPort(next,cursor)));cursor=next;}
    }
    private static void addChassis(List<ModularBlockSnapshot> modules,GridVector position){
        for(ModularBlockSnapshot module:modules)if(module.localPosition.equals(position))return;
        modules.add(module(StandardComponentCatalog.CHASSIS,position,ComponentOrientation.NORTH_UP));
    }
    private static void addEdge(List<AssemblyEdge> edges,AssemblyEdge value){
        for(AssemblyEdge edge:edges)if(edge.kind==value.kind&&edge.firstPosition.equals(value.firstPosition)
                &&edge.secondPosition.equals(value.secondPosition))return;edges.add(value);
    }
    private static String mountPort(GridVector from,GridVector to){
        GridVector delta=to.subtract(from);
        for(Direction direction:Direction.values())if(direction.vector.equals(delta))return "mount_"+direction.name().toLowerCase();
        throw new IllegalArgumentException("non-adjacent support path");
    }
    private static MobileTerminal t(GridVector p,String type,String port,int network,String role){return new MobileTerminal(p,type,port,Direction.UP,role,network);}
    private static ModularBlockSnapshot module(String type,GridVector p,ComponentOrientation orientation){return new ModularBlockSnapshot(type,1,p,orientation,type,0,null);}
    private static GridVector p(int x,int y,int z){return new GridVector(x,y,z);}
}
