package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class TrackAssemblyAnalyzerTest {
    private final ComponentCatalog catalog=StandardComponentCatalog.create();

    @Test public void declaredBeltNeedsBothSprocketDriveAndStructuralMount(){
        TrackAssembly complete=TrackAssemblyAnalyzer.analyze(complete(),catalog);
        assertTrue(complete.getDiagnostics().toString(),complete.isValid());
        assertEquals(1,complete.getUnits().size());
        TrackAssembly.Unit unit=complete.getUnits().get(0);
        assertTrue(unit.transmitsEffort());assertEquals(0.342,unit.contactAreaSquareMetres,1.0e-12);
        assertEquals(Direction.EAST,unit.rollingDirection);

        ModularBlockSnapshot track=module(StandardComponentCatalog.TRACK_MODULE,p(0,0,0),ComponentOrientation.NORTH_UP);
        TrackAssembly incomplete=TrackAssemblyAnalyzer.analyze(new ModularRobotManifest(new UUID(3,4),
                Collections.singletonList(track),Collections.<AssemblyEdge>emptyList()),catalog);
        assertFalse(incomplete.isValid());assertFalse(incomplete.getUnits().get(0).transmitsEffort());
        assertEquals(2,incomplete.getDiagnostics().size());
        assertEquals(TrackAssembly.Diagnostic.DRIVE_PATH_OPEN,incomplete.getDiagnostics().get(0).code);
        assertEquals(TrackAssembly.Diagnostic.STRUCTURAL_MOUNT_OPEN,incomplete.getDiagnostics().get(1).code);

        ModularRobotManifest drivenButLoose=withoutMount(complete());
        TrackAssembly loose=TrackAssemblyAnalyzer.analyze(drivenButLoose,catalog);
        assertFalse(loose.getUnits().get(0).transmitsEffort());
        assertTrue(MechanicalAssemblyAnalyzer.analyze(drivenButLoose,catalog).getDrives().isEmpty());
        assertEquals(MechanicalAssembly.Diagnostic.TRACK_MOUNT_OPEN,
                MechanicalAssemblyAnalyzer.analyze(drivenButLoose,catalog).getDiagnostics().get(0).code);
    }

    @Test public void trackBudgetRejectsEveryDriveAtomically(){
        List<ModularBlockSnapshot> modules=new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges=new ArrayList<AssemblyEdge>();
        for(int index=0;index<9;index++)addCompleteTrack(index*2-8,modules,edges);
        ModularRobotManifest manifest=new ModularRobotManifest(new UUID(9,10),modules,edges);
        TrackAssembly tracks=TrackAssemblyAnalyzer.analyze(manifest,catalog);
        MechanicalAssembly mechanical=MechanicalAssemblyAnalyzer.analyze(manifest,catalog);
        assertFalse(tracks.isValid());assertEquals(9,tracks.getUnits().size());
        assertTrue(mechanical.getDrives().isEmpty());
        assertTrue(has(tracks,TrackAssembly.Diagnostic.TRACK_LIMIT_EXCEEDED));
    }

    private static ModularRobotManifest complete(){
        List<ModularBlockSnapshot> modules=new ArrayList<ModularBlockSnapshot>();
        List<AssemblyEdge> edges=new ArrayList<AssemblyEdge>();addCompleteTrack(0,modules,edges);
        return new ModularRobotManifest(new UUID(1,2),modules,edges);
    }
    private static void addCompleteTrack(int x,List<ModularBlockSnapshot> modules,List<AssemblyEdge> edges){
        GridVector motor=p(x,0,0),axle=p(x,0,1),track=p(x,0,2),mount=p(x,1,2);
        modules.add(module(StandardComponentCatalog.DC_MOTOR,motor,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.AXLE,axle,ComponentOrientation.NORTH_UP));
        modules.add(module(StandardComponentCatalog.TRACK_MODULE,track,new ComponentOrientation(Direction.EAST,Direction.UP)));
        modules.add(module(StandardComponentCatalog.CHASSIS,mount,ComponentOrientation.NORTH_UP));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,motor,"shaft",axle,"shaft_in"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,axle,"shaft_out",track,"sprocket"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL,track,"mount_up",mount,"mount_down"));
    }
    private static ModularRobotManifest withoutMount(ModularRobotManifest complete){
        List<AssemblyEdge> edges=new ArrayList<AssemblyEdge>();
        for(AssemblyEdge edge:complete.getEdges())if(edge.kind!=AssemblyEdge.Kind.STRUCTURAL)edges.add(edge);
        return new ModularRobotManifest(new UUID(7,8),complete.getModules(),edges);
    }
    private static ModularBlockSnapshot module(String type,GridVector p,ComponentOrientation o){return new ModularBlockSnapshot(type,1,p,o,type,0,null);}
    private static GridVector p(int x,int y,int z){return new GridVector(x,y,z);}
    private static boolean has(TrackAssembly assembly,TrackAssembly.Diagnostic code){
        for(TrackAssembly.Problem problem:assembly.getDiagnostics())if(problem.code==code)return true;return false;
    }
}
