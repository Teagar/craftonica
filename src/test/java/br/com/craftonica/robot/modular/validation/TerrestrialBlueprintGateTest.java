package br.com.craftonica.robot.modular.validation;

import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.electrical.MobileElectricalDiagnostic;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.assembly.MechanicalAssemblyAnalyzer;
import br.com.craftonica.robot.modular.visual.ModularRobotVisualState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public final class TerrestrialBlueprintGateTest {
    @Test public void fourLayoutsUseTheSameAnalyzersWithoutRuntimeBlueprintIds() {
        List<ModularRobotManifest> fixtures = Arrays.asList(TerrestrialBlueprintFixtures.twoWheelCaster(),
                TerrestrialBlueprintFixtures.threeWheel(), TerrestrialBlueprintFixtures.fourWheel(),
                TerrestrialBlueprintFixtures.skidSteer());
        int[] contacts = {3,3,4,4}, drives = {2,3,4,4};
        for (int i = 0; i < fixtures.size(); i++) {
            RigidBodyProperties body = RigidBodyProperties.derive(fixtures.get(i), StandardComponentCatalog.create());
            CoupledDriveLoop loop = new CoupledDriveLoop(fixtures.get(i), StandardComponentCatalog.create());
            assertEquals(contacts[i], body.getContacts().size());
            assertEquals(drives[i], loop.initialState().getChannels().size());
            assertTrue(MobileElectricalEvaluator.evaluate(fixtures.get(i).getElectricalNetlist())
                    .getDiagnostics().isEmpty());
        }
        assertNotEquals(RigidBodyProperties.derive(fixtures.get(2), StandardComponentCatalog.create())
                        .principalInertiaKgMetresSquared.y,
                RigidBodyProperties.derive(fixtures.get(3), StandardComponentCatalog.create())
                        .principalInertiaKgMetresSquared.y, 0.0);
    }

    @Test public void wheelMassAndDisconnectedSignalChangeDerivedBehavior() {
        RigidBodyProperties small = RigidBodyProperties.derive(TerrestrialBlueprintFixtures.twoWheelCaster(),
                StandardComponentCatalog.create());
        RigidBodyProperties large = RigidBodyProperties.derive(TerrestrialBlueprintFixtures.largerWheels(),
                StandardComponentCatalog.create());
        assertNotEquals(small.getContacts().get(0).radiusMetres, large.getContacts().get(0).radiusMetres, 0.0);
        assertNotEquals(small.principalInertiaKgMetresSquared.y,
                large.principalInertiaKgMetresSquared.y, 0.0);
        assertTrue(RigidBodyProperties.derive(TerrestrialBlueprintFixtures.heavierChassis(),
                StandardComponentCatalog.create()).massKg > small.massKg);
        assertEquals(1, MechanicalAssemblyAnalyzer.analyze(TerrestrialBlueprintFixtures.openMechanicalPath(),
                StandardComponentCatalog.create()).getDrives().size());
        MobileElectricalEvaluation open = MobileElectricalEvaluator.evaluate(
                TerrestrialBlueprintFixtures.openSignal().getElectricalNetlist());
        assertTrue(has(open, MobileElectricalDiagnostic.Code.HBRIDGE_PWM_OPEN));
    }

    @Test public void soakIsFiniteDeterministicAndVisualPayloadFeedsTwoPassiveClients() {
        ModularRobotManifest manifest = TerrestrialBlueprintFixtures.skidSteer();
        RigidBodyProperties body = RigidBodyProperties.derive(manifest, StandardComponentCatalog.create());
        TerrestrialRigidBodyModel.State a = new TerrestrialRigidBodyModel.State(0,64,0,0,0,0,0,0), b = a;
        for (int tick = 0; tick < 20000; tick++) {
            a = TerrestrialRigidBodyModel.step(body, a,
                    Collections.<TerrestrialRigidBodyModel.AppliedForce>emptyList(),
                    Arrays.asList(0,1,2,3), 0.025);
            b = TerrestrialRigidBodyModel.step(body, b,
                    Collections.<TerrestrialRigidBodyModel.AppliedForce>emptyList(),
                    Arrays.asList(0,1,2,3), 0.025);
        }
        assertEquals(Double.doubleToLongBits(a.y), Double.doubleToLongBits(b.y));
        assertTrue(finite(a.x) && finite(a.y) && finite(a.z));
        ModularRobotVisualState visual = ModularRobotVisualState.fromManifest(manifest);
        ByteBuf encoded = Unpooled.buffer(); visual.write(encoded); byte[] bytes = new byte[encoded.readableBytes()];
        encoded.readBytes(bytes);
        ModularRobotVisualState clientA = ModularRobotVisualState.read(Unpooled.wrappedBuffer(bytes));
        ModularRobotVisualState clientB = ModularRobotVisualState.read(Unpooled.wrappedBuffer(bytes));
        assertEquals(clientA.getModules().size(), clientB.getModules().size());
        assertEquals(clientA.getModules().get(0).localPosition, clientB.getModules().get(0).localPosition);
        assertTrue(bytes.length <= ModularRobotVisualState.MAX_PAYLOAD_BYTES);
    }

    private static boolean has(MobileElectricalEvaluation value, MobileElectricalDiagnostic.Code code) {
        for (MobileElectricalDiagnostic diagnostic : value.getDiagnostics()) if (diagnostic.code == code) return true;
        return false;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
