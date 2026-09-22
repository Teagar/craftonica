package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.*;

/** Validates the educational integrated sprocket/idler/belt declaration from physical edges. */
public final class TrackAssemblyAnalyzer {
    public static final int MAX_TRACK_MODULES=8;
    private TrackAssemblyAnalyzer(){}
    public static TrackAssembly analyze(ModularRobotManifest manifest,ComponentCatalog catalog){
        if(manifest==null||catalog==null)throw new IllegalArgumentException("manifest or catalog");
        MechanicalAssembly mechanical=MechanicalAssemblyAnalyzer.analyze(manifest,catalog);
        Set<GridVector> driven=new HashSet<GridVector>();
        for(MechanicalAssembly.DrivePath path:mechanical.getDrives())driven.add(path.wheelPosition);
        List<TrackAssembly.Unit> units=new ArrayList<TrackAssembly.Unit>();
        List<TrackAssembly.Problem> problems=new ArrayList<TrackAssembly.Problem>();
        for(ModularBlockSnapshot module:manifest.getModules()){
            if(!StandardComponentCatalog.TRACK_MODULE.equals(module.componentTypeId))continue;
            ComponentType type=catalog.require(module.componentTypeId);ContactProfile profile=type.getContact();
            double length=parameter(profile,"contact_length_metres"),width=parameter(profile,"width_metres");
            double radius=parameter(profile,"radius_metres");boolean mounted=mounted(manifest,module.localPosition);
            boolean hasDrive=driven.contains(module.localPosition);MechanicalPort hub=null;
            for(MechanicalPort port:type.getMechanicalPorts())if(port.kind==MechanicalPort.Kind.WHEEL_HUB){hub=port;break;}
            if(hub==null)throw new IllegalArgumentException("track without sprocket");
            Direction axle=module.localOrientation.toWorld(hub.axis);
            Direction rolling=horizontalPerpendicular(axle);
            units.add(new TrackAssembly.Unit(module.localPosition,rolling,length,width,radius,hasDrive,mounted));
            if(!hasDrive)problems.add(new TrackAssembly.Problem(TrackAssembly.Diagnostic.DRIVE_PATH_OPEN,module.localPosition));
            if(!mounted)problems.add(new TrackAssembly.Problem(TrackAssembly.Diagnostic.STRUCTURAL_MOUNT_OPEN,module.localPosition));
            if(length<=0.0||width<=0.0||length>2.0||width>1.0)
                problems.add(new TrackAssembly.Problem(TrackAssembly.Diagnostic.INVALID_CONTACT_AREA,module.localPosition));
        }
        if(units.size()>MAX_TRACK_MODULES)problems.add(new TrackAssembly.Problem(
                TrackAssembly.Diagnostic.TRACK_LIMIT_EXCEEDED,GridVector.ZERO));
        Collections.sort(problems);return new TrackAssembly(units,problems);
    }
    private static boolean mounted(ModularRobotManifest manifest,GridVector position){
        for(AssemblyEdge edge:manifest.getEdges())if(edge.kind==AssemblyEdge.Kind.STRUCTURAL
                &&(position.equals(edge.firstPosition)&&"mount_up".equals(edge.firstPort)
                ||position.equals(edge.secondPosition)&&"mount_up".equals(edge.secondPort)))return true;
        return false;
    }
    private static Direction horizontalPerpendicular(Direction axle){
        if(axle==Direction.EAST)return Direction.NORTH;
        if(axle==Direction.WEST)return Direction.SOUTH;
        if(axle==Direction.NORTH)return Direction.WEST;
        if(axle==Direction.SOUTH)return Direction.EAST;
        throw new IllegalArgumentException("vertical track axle");
    }
    private static double parameter(ContactProfile profile,String name){Double value=profile.getParameters().get(name);return value==null?0.0:value.doubleValue();}
}
