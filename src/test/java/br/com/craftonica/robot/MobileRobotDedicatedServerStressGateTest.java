package br.com.craftonica.robot;

import br.com.craftonica.command.CommandCraftonica;
import br.com.craftonica.registry.ModEntities;
import br.com.craftonica.runtime.server.RoboBoardRuntimeHost;
import br.com.craftonica.runtime.server.RuntimeSupervisor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MobileRobotDedicatedServerStressGateTest {
    @Test public void authoritativeRobotClassesHaveNoClientOnlyLinkage() throws Exception {
        assertServerSafe(EntityMobileRobot.class);
        assertServerSafe(MobileRobotIoBridge.class);
        assertServerSafe(MobileRobotState.class);
        assertServerSafe(RoboBoardRuntimeHost.class);
        assertServerSafe(RuntimeSupervisor.class);
        assertServerSafe(CommandCraftonica.class);
    }

    @Test public void networkTrackingIsFrequentButStrictlyBounded() {
        assertTrue(ModEntities.MOBILE_TRACKING_RANGE > EntityMobileRobot.COLLISION_WIDTH);
        assertTrue(ModEntities.MOBILE_TRACKING_RANGE <= 96);
        assertTrue(ModEntities.MOBILE_UPDATE_FREQUENCY >= 1);
        assertTrue(ModEntities.MOBILE_UPDATE_FREQUENCY <= 2);
    }

    private static void assertServerSafe(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        InputStream input = type.getResourceAsStream(resource);
        assertTrue("missing class resource " + resource, input != null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[2048]; int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
        } finally { input.close(); }
        String pool = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        assertFalse(type.getName() + " links net.minecraft.client", pool.contains("net/minecraft/client"));
        assertFalse(type.getName() + " links Craftonica client package", pool.contains("br/com/craftonica/client"));
    }
}
