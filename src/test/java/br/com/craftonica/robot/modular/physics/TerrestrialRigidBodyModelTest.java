package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class TerrestrialRigidBodyModelTest {
    @Test public void equalForceChangesAccelerationWithDerivedMassAndNeverProducesNonFiniteState() {
        RigidBodyProperties light = body(module(StandardComponentCatalog.WHEEL, 0, 0, 0));
        RigidBodyProperties heavy = body(module(StandardComponentCatalog.WHEEL, 0, 0, 0),
                module(StandardComponentCatalog.CHASSIS, 0, 1, 0));
        TerrestrialRigidBodyModel.AppliedForce force = new TerrestrialRigidBodyModel.AppliedForce(0, 10.0, 10.0);
        TerrestrialRigidBodyModel.State initial = state();
        TerrestrialRigidBodyModel.State lightStep = TerrestrialRigidBodyModel.step(light, initial,
                Collections.singletonList(force), Collections.singletonList(Integer.valueOf(0)), 0.05);
        TerrestrialRigidBodyModel.State heavyStep = TerrestrialRigidBodyModel.step(heavy, initial,
                Collections.singletonList(force), Collections.singletonList(Integer.valueOf(0)), 0.05);
        assertTrue(lightStep.velocityZ > heavyStep.velocityZ);
        assertTrue(Double.isFinite(lightStep.x)); assertTrue(Double.isFinite(heavyStep.yawRadians));
    }

    @Test public void asymmetricForceKeepsTorqueSignAndMassLayoutChangesYawResponse() {
        RigidBodyProperties compact = body(module(StandardComponentCatalog.CHASSIS, 0, 0, 0),
                module(StandardComponentCatalog.CHASSIS, 2, 0, 0),
                module(StandardComponentCatalog.WHEEL, 0, 0, 2));
        RigidBodyProperties wide = body(module(StandardComponentCatalog.CHASSIS, 0, 0, 0),
                module(StandardComponentCatalog.CHASSIS, 4, 0, 0),
                module(StandardComponentCatalog.WHEEL, 0, 0, 2));
        TerrestrialRigidBodyModel.AppliedForce force = new TerrestrialRigidBodyModel.AppliedForce(0, 8.0, 8.0);
        TerrestrialRigidBodyModel.State compactStep = TerrestrialRigidBodyModel.step(compact, state(),
                Collections.singletonList(force), Collections.singletonList(Integer.valueOf(0)), 0.05);
        TerrestrialRigidBodyModel.State wideStep = TerrestrialRigidBodyModel.step(wide, state(),
                Collections.singletonList(force), Collections.singletonList(Integer.valueOf(0)), 0.05);
        assertTrue(compactStep.angularVelocityRadiansPerSecond > 0.0);
        assertTrue(wideStep.angularVelocityRadiansPerSecond > 0.0);
        assertTrue(StrictMath.abs(compactStep.angularVelocityRadiansPerSecond
                - wideStep.angularVelocityRadiansPerSecond) > 1.0e-9);
    }

    @Test public void unsupportedBodyFallsAndSupportedBodyCancelsVerticalVelocity() {
        RigidBodyProperties body = body(module(StandardComponentCatalog.WHEEL, 0, 0, 0));
        TerrestrialRigidBodyModel.State falling = TerrestrialRigidBodyModel.step(body, state(),
                Collections.singletonList(new TerrestrialRigidBodyModel.AppliedForce(0, 20.0, 20.0)),
                Collections.<Integer>emptyList(), 0.05);
        assertTrue(falling.velocityY < 0.0); assertTrue(falling.y < 10.0);
        assertEquals(0.0, falling.velocityX, 0.0); assertEquals(0.0, falling.velocityZ, 0.0);
        TerrestrialRigidBodyModel.State supported = TerrestrialRigidBodyModel.step(body, falling,
                Collections.<TerrestrialRigidBodyModel.AppliedForce>emptyList(),
                Collections.singletonList(Integer.valueOf(0)), 0.05);
        assertEquals(0.0, supported.velocityY, 0.0);
    }

    @Test public void supportedWheelTractionIsCappedByDeclaredFriction() {
        RigidBodyProperties body = body(module(StandardComponentCatalog.WHEEL, 0, 0, 0));
        TerrestrialRigidBodyModel.State result = TerrestrialRigidBodyModel.step(body, state(),
                Collections.singletonList(new TerrestrialRigidBodyModel.AppliedForce(0, 10000.0, 10000.0)),
                Collections.singletonList(Integer.valueOf(0)), 0.05);
        double frictionLimitedDelta = 0.9 * TerrestrialRigidBodyModel.GRAVITY_METRES_PER_SECOND_SQUARED * 0.05;
        assertEquals(frictionLimitedDelta, result.velocityZ, 1.0e-12);
    }

    private static TerrestrialRigidBodyModel.State state() {
        return new TerrestrialRigidBodyModel.State(0.0, 10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
    private static RigidBodyProperties body(ModularBlockSnapshot... modules) {
        return RigidBodyProperties.derive(new ModularRobotManifest(new UUID(3L, 4L),
                Arrays.asList(modules), Collections.<AssemblyEdge>emptyList()),
                StandardComponentCatalog.create());
    }
    private static ModularBlockSnapshot module(String type, int x, int y, int z) {
        return new ModularBlockSnapshot(type, 1, new GridVector(x, y, z),
                ComponentOrientation.NORTH_UP, type, 0, null);
    }
}
