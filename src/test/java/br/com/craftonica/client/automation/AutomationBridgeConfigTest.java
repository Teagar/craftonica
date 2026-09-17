package br.com.craftonica.client.automation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutomationBridgeConfigTest {
    @Test
    public void remainsDisabledByDefault() {
        AutomationBridgeConfig config = AutomationBridgeConfig.fromProperties(null, null, null);

        assertFalse(config.isEnabled());
        assertEquals(8765, config.getPort());
    }

    @Test
    public void acceptsExplicitSecureConfiguration() {
        AutomationBridgeConfig config = AutomationBridgeConfig.fromProperties("true", "9123",
                "local-test-token");

        assertTrue(config.isEnabled());
        assertEquals(9123, config.getPort());
        assertEquals("local-test-token", config.getToken());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortToken() {
        AutomationBridgeConfig.fromProperties("true", null, "short");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPrivilegedPort() {
        AutomationBridgeConfig.fromProperties("true", "80", "local-test-token");
    }
}
