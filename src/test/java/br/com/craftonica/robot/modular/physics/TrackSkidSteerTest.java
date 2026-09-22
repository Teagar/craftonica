package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.forge.ForgeRigidBodyWorld;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class TrackSkidSteerTest {
    @Test public void equalCommandsRunStraightAndDifferentialCommandsYaw(){
        RigidBodyProperties body=body();List<Integer> supported=Arrays.asList(0,1);
        TerrestrialRigidBodyModel.State stopped=new TerrestrialRigidBodyModel.State(0,0,0,0,0,0,0,0);
        TerrestrialRigidBodyModel.State straight=TerrestrialRigidBodyModel.step(body,stopped,Arrays.asList(
                new TerrestrialRigidBodyModel.AppliedForce(0,100,100),new TerrestrialRigidBodyModel.AppliedForce(1,100,100)),supported,0.05);
        TerrestrialRigidBodyModel.State turning=TerrestrialRigidBodyModel.step(body,stopped,Arrays.asList(
                new TerrestrialRigidBodyModel.AppliedForce(0,100,100),new TerrestrialRigidBodyModel.AppliedForce(1,-100,100)),supported,0.05);
        assertTrue(StrictMath.abs(straight.velocityZ)>0.0);assertEquals(0.0,straight.angularVelocityRadiansPerSecond,1.0e-12);
        assertTrue(StrictMath.abs(turning.angularVelocityRadiansPerSecond)>0.0);
    }

    @Test public void slipperierSurfaceReducesAvailableGrip(){
        assertTrue(ForgeRigidBodyWorld.frictionMultiplier(0.98)<ForgeRigidBodyWorld.frictionMultiplier(0.6));
        assertTrue(ForgeRigidBodyWorld.frictionMultiplier(0.4)>ForgeRigidBodyWorld.frictionMultiplier(0.6));
    }

    private static RigidBodyProperties body(){
        List<ModularBlockSnapshot> modules=Arrays.asList(track(-1),track(1));
        return RigidBodyProperties.derive(new ModularRobotManifest(new UUID(5,6),modules,
                Collections.<AssemblyEdge>emptyList()),StandardComponentCatalog.create());
    }
    private static ModularBlockSnapshot track(int x){return new ModularBlockSnapshot(StandardComponentCatalog.TRACK_MODULE,1,
            new GridVector(x,0,0),ComponentOrientation.NORTH_UP,StandardComponentCatalog.TRACK_MODULE,0,null);}
}
