package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class HolonomicWheelPhysicsTest {
    private static final double DT=0.025;

    @Test public void xConfigurationProducesForwardStrafeAndRotationFromPhysicalForceVectors(){
        RigidBodyProperties body=mecanum(false,false);List<Integer> supported=Arrays.asList(0,1,2,3);
        TerrestrialRigidBodyModel.State forward=step(body,supported,1,1,1,1);
        TerrestrialRigidBodyModel.State strafe=step(body,supported,1,-1,-1,1);
        TerrestrialRigidBodyModel.State rotate=step(body,supported,-1,-1,1,1);
        assertNearZero(forward.velocityX);assertTrue(forward.velocityZ<0.0);assertNearZero(forward.angularVelocityRadiansPerSecond);
        assertTrue(strafe.velocityX>0.0);assertNearZero(strafe.velocityZ);assertNearZero(strafe.angularVelocityRadiansPerSecond);
        assertEquals("rotation vx",0.0,rotate.velocityX,1.0e-12);
        assertEquals("rotation vz",0.0,rotate.velocityZ,1.0e-12);
        assertTrue("rotation yaw="+rotate.angularVelocityRadiansPerSecond,
                rotate.angularVelocityRadiansPerSecond>0.0);
    }

    @Test public void oConfigurationChangesTheStrafeVectorWithoutBlueprintCorrection(){
        RigidBodyProperties x=mecanum(false,false),o=mecanum(true,false);
        List<Integer> supported=Arrays.asList(0,1,2,3);
        TerrestrialRigidBodyModel.State xResult=step(x,supported,1,-1,-1,1);
        TerrestrialRigidBodyModel.State oResult=step(o,supported,1,-1,-1,1);
        assertTrue(xResult.velocityX>0.0);assertTrue(oResult.velocityX<0.0);
        assertNearZero(xResult.velocityZ);assertNearZero(oResult.velocityZ);
    }

    @Test public void incorrectlyRotatedWheelPhysicallySpoilsStraightMotion(){
        List<Integer> supported=Arrays.asList(0,1,2,3);
        TerrestrialRigidBodyModel.State correct=step(mecanum(false,false),supported,1,1,1,1);
        TerrestrialRigidBodyModel.State wrong=step(mecanum(false,true),supported,1,1,1,1);
        assertNearZero(correct.velocityX);assertNearZero(correct.angularVelocityRadiansPerSecond);
        assertTrue(StrictMath.abs(wrong.velocityX)>1.0e-6
                || StrictMath.abs(wrong.angularVelocityRadiansPerSecond)>1.0e-6);
    }

    @Test public void omniWheelKeepsDriveGripAndReleasesRollerDirection(){
        RigidBodyProperties body=RigidBodyProperties.derive(new ModularRobotManifest(new UUID(20,21),
                Collections.singletonList(module(StandardComponentCatalog.OMNI_WHEEL,0,0,0,
                        ComponentOrientation.NORTH_UP)),Collections.<AssemblyEdge>emptyList()),StandardComponentCatalog.create());
        RigidBodyProperties.Contact contact=body.getContacts().get(0);
        assertEquals(ContactProfile.Kind.OMNI_WHEEL,contact.kind);
        assertEquals(0.95,contact.longitudinalFriction,0.0);
        assertEquals(0.04,contact.lateralFriction,0.0);
        assertEquals(contact.radiusMetres,contact.tractionLeverArmMetres,0.0);
    }

    @Test public void mecanumKeepsWheelPlaneSeparateFromRollerTraction(){
        RigidBodyProperties body=RigidBodyProperties.derive(new ModularRobotManifest(new UUID(26,27),
                Collections.singletonList(module(StandardComponentCatalog.MECANUM_LEFT,0,0,0,
                        ComponentOrientation.NORTH_UP)),Collections.<AssemblyEdge>emptyList()),StandardComponentCatalog.create());
        RigidBodyProperties.Contact contact=body.getContacts().get(0);
        assertEquals(0.0,contact.rollingDirection.x,1.0e-12);
        assertEquals(-1.0,contact.rollingDirection.z,1.0e-12);
        assertEquals(1.0/StrictMath.sqrt(2.0),contact.tractionDirection.x,1.0e-12);
        assertEquals(-1.0/StrictMath.sqrt(2.0),contact.tractionDirection.z,1.0e-12);
    }

    private static TerrestrialRigidBodyModel.State step(RigidBodyProperties body,List<Integer> supported,int a,int b,int c,int d){
        int[] signs={a,b,c,d};List<TerrestrialRigidBodyModel.AppliedForce> forces=new ArrayList<TerrestrialRigidBodyModel.AppliedForce>();
        for(int i=0;i<4;i++)forces.add(new TerrestrialRigidBodyModel.AppliedForce(i,signs[i]*10.0,10.0));
        return TerrestrialRigidBodyModel.step(body,new TerrestrialRigidBodyModel.State(0,0,0,0,0,0,0,0),forces,supported,DT);
    }
    private static RigidBodyProperties mecanum(boolean oConfiguration,boolean rotateFrontLeft){
        String left=StandardComponentCatalog.MECANUM_LEFT,right=StandardComponentCatalog.MECANUM_RIGHT;
        String[] types=oConfiguration?new String[]{right,left,left,right}:new String[]{left,right,right,left};
        int[][] positions={{-1,0,-1},{1,0,-1},{-1,0,1},{1,0,1}};
        List<ModularBlockSnapshot> modules=new ArrayList<ModularBlockSnapshot>();
        for(int i=0;i<4;i++)modules.add(module(types[i],positions[i][0],0,positions[i][2],
                rotateFrontLeft&&i==0?new ComponentOrientation(Direction.SOUTH,Direction.UP):ComponentOrientation.NORTH_UP));
        return RigidBodyProperties.derive(new ModularRobotManifest(new UUID(oConfiguration?22:23,rotateFrontLeft?24:25),
                modules,Collections.<AssemblyEdge>emptyList()),StandardComponentCatalog.create());
    }
    private static ModularBlockSnapshot module(String type,int x,int y,int z,ComponentOrientation orientation){
        return new ModularBlockSnapshot(type,1,new GridVector(x,y,z),orientation,type,0,null);
    }
    private static void assertNearZero(double value){assertEquals(0.0,value,1.0e-12);}
}
