package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RigidBodyPropertiesTest {
    @Test public void derivesMassCenterAndParallelAxisInertiaFromModulePositions() {
        RigidBodyProperties near = body(module(StandardComponentCatalog.CHASSIS, 0, 0, 0),
                module(StandardComponentCatalog.CHASSIS, 2, 0, 0));
        assertEquals(16.0, near.massKg, 0.0);
        assertEquals(1.5, near.centerOfMassMetres.x, 0.0);
        assertEquals(0.5, near.centerOfMassMetres.y, 0.0);
        assertEquals(18.666666666666664, near.principalInertiaKgMetresSquared.y, 1.0e-12);

        RigidBodyProperties far = body(module(StandardComponentCatalog.CHASSIS, 0, 0, 0),
                module(StandardComponentCatalog.CHASSIS, 4, 0, 0));
        assertEquals(2.5, far.centerOfMassMetres.x, 0.0);
        assertTrue(far.principalInertiaKgMetresSquared.y > near.principalInertiaKgMetresSquared.y);
    }

    @Test public void wheelAndCasterContactsPreserveInstalledPoseAndProfile() {
        RigidBodyProperties body = body(module(StandardComponentCatalog.WHEEL, -2, 0, 1),
                module(StandardComponentCatalog.WHEEL_150, 2, 0, 1),
                module(StandardComponentCatalog.CASTER, 0, 0, -2));
        assertEquals(3, body.getContacts().size());
        for (RigidBodyProperties.Contact contact : body.getContacts()) {
            if (contact.modulePosition.x == -2) {
                assertEquals(0.05, contact.radiusMetres, 0.0);
                assertEquals(-1.5, contact.pointMetres.x, 0.0);
            } else if (contact.modulePosition.x == 2) {
                assertEquals(0.075, contact.radiusMetres, 0.0);
                assertEquals(2.5, contact.pointMetres.x, 0.0);
            } else assertEquals(0.025, contact.radiusMetres, 0.0);
        }
    }

    @Test public void faceSharingCellsFuseBeforeCollisionBudgetIsApplied() {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        for (int x = 0; x < 129; x++) modules.add(module(StandardComponentCatalog.CHASSIS, x, 0, 0));
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(7L, 8L), modules,
                Collections.<br.com.craftonica.robot.modular.assembly.AssemblyEdge>emptyList());
        RigidBodyProperties body = RigidBodyProperties.derive(manifest, StandardComponentCatalog.create());
        assertEquals(1, body.getCollisionVolumes().size());
    }

    private static RigidBodyProperties body(ModularBlockSnapshot... modules) {
        List<ModularBlockSnapshot> values = new ArrayList<ModularBlockSnapshot>();
        Collections.addAll(values, modules);
        ModularRobotManifest manifest = new ModularRobotManifest(new UUID(1L, 2L), values,
                Collections.<br.com.craftonica.robot.modular.assembly.AssemblyEdge>emptyList());
        return RigidBodyProperties.derive(manifest, StandardComponentCatalog.create());
    }

    private static ModularBlockSnapshot module(String type, int x, int y, int z) {
        return new ModularBlockSnapshot(type, 1, new GridVector(x, y, z),
                ComponentOrientation.NORTH_UP, type, 0, null);
    }
}
