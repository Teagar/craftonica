package br.com.craftonica.client.automation;

public final class AutomationBridgeConfig {
    static final int DEFAULT_PORT = 8765;

    private final boolean enabled;
    private final int port;
    private final String token;

    private AutomationBridgeConfig(boolean enabled, int port, String token) {
        this.enabled = enabled;
        this.port = port;
        this.token = token;
    }

    public static AutomationBridgeConfig fromProperties(String enabledValue, String portValue, String tokenValue) {
        boolean enabled = Boolean.parseBoolean(enabledValue);
        if (!enabled) {
            return new AutomationBridgeConfig(false, DEFAULT_PORT, "");
        }
        if (tokenValue == null || tokenValue.trim().length() < 16) {
            throw new IllegalArgumentException("craftonica.automation.token must contain at least 16 characters");
        }

        int port = DEFAULT_PORT;
        if (portValue != null && !portValue.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("craftonica.automation.port must be a number", error);
            }
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("craftonica.automation.port must be between 1024 and 65535");
        }
        return new AutomationBridgeConfig(true, port, tokenValue);
    }

    public static AutomationBridgeConfig fromSystemProperties() {
        String token = System.getenv("CRAFTONICA_AUTOMATION_TOKEN");
        if (token == null) {
            token = System.getProperty("craftonica.automation.token");
        }
        return fromProperties(System.getProperty("craftonica.automation.enabled"),
                System.getProperty("craftonica.automation.port"),
                token);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }
}
