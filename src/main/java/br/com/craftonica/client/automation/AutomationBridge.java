package br.com.craftonica.client.automation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ScreenShotHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AutomationBridge {
    private static final Logger LOGGER = LogManager.getLogger("CraftonicaAutomation");
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final long CLIENT_TIMEOUT_SECONDS = 10L;

    private final AutomationBridgeConfig config;
    private final Gson gson = new Gson();
    private HttpServer server;

    public AutomationBridge(AutomationBridgeConfig config) {
        this.config = config;
    }

    public static void startConfigured() {
        try {
            new AutomationBridge(AutomationBridgeConfig.fromSystemProperties()).start();
        } catch (IllegalArgumentException error) {
            LOGGER.error("Craftonica automation is disabled because its configuration is invalid: {}",
                    error.getMessage());
        }
    }

    public synchronized void start() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.getPort());
            server = HttpServer.create(address, 8);
            server.createContext("/v1/state", new StateHandler());
            server.createContext("/v1/screenshot", new ScreenshotHandler());
            server.createContext("/v1/action", new ActionHandler());
            server.setExecutor(Executors.newFixedThreadPool(2, new ThreadFactory() {
                private int sequence;

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "Craftonica automation " + (++sequence));
                    thread.setDaemon(true);
                    return thread;
                }
            }));
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    stop();
                }
            }, "Craftonica automation shutdown"));
            LOGGER.info("Automation bridge listening on 127.0.0.1:{}", config.getPort());
        } catch (IOException error) {
            LOGGER.error("Could not start the Craftonica automation bridge", error);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private abstract class AuthenticatedHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            try {
                if (!isAuthorized(exchange.getRequestHeaders())) {
                    writeJson(exchange, 401, error("unauthorized"));
                    return;
                }
                handleAuthorized(exchange);
            } catch (IllegalArgumentException error) {
                writeJson(exchange, 400, error(error.getMessage()));
            } catch (Exception error) {
                LOGGER.warn("Automation request failed", error);
                writeJson(exchange, 500, error("request_failed"));
            } finally {
                exchange.close();
            }
        }

        protected abstract void handleAuthorized(HttpExchange exchange) throws Exception;
    }

    private final class StateHandler extends AuthenticatedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "GET");
            JsonObject state = onClientThread(new Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    return captureState();
                }
            });
            writeJson(exchange, 200, state);
        }
    }

    private final class ScreenshotHandler extends AuthenticatedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "GET");
            byte[] png = onClientThread(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    return captureScreenshot();
                }
            });
            write(exchange, 200, "image/png", png);
        }
    }

    private final class ActionHandler extends AuthenticatedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            final JsonObject request = parseObject(readBody(exchange));
            JsonObject result = onClientThread(new Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    return performAction(request);
                }
            });
            writeJson(exchange, 200, result);
        }
    }

    private JsonObject captureState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        JsonObject state = new JsonObject();
        state.addProperty("connected", minecraft.theWorld != null && minecraft.thePlayer != null);
        state.addProperty("screen", minecraft.currentScreen == null ? "game" : minecraft.currentScreen.getClass().getSimpleName());
        state.addProperty("paused", minecraft.isGamePaused());
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return state;
        }

        EntityPlayer player = minecraft.thePlayer;
        JsonObject playerState = new JsonObject();
        playerState.addProperty("name", player.getCommandSenderName());
        playerState.addProperty("x", player.posX);
        playerState.addProperty("y", player.posY);
        playerState.addProperty("z", player.posZ);
        playerState.addProperty("yaw", player.rotationYaw);
        playerState.addProperty("pitch", player.rotationPitch);
        playerState.addProperty("health", player.getHealth());
        playerState.addProperty("food", player.getFoodStats().getFoodLevel());
        playerState.addProperty("hotbar_slot", player.inventory.currentItem + 1);
        ItemStack held = player.getHeldItem();
        if (held != null) {
            playerState.addProperty("held_item", String.valueOf(Item.itemRegistry.getNameForObject(held.getItem())));
            playerState.addProperty("held_count", held.stackSize);
        }
        state.add("player", playerState);
        state.addProperty("dimension", minecraft.theWorld.provider.dimensionId);
        state.addProperty("world_time", minecraft.theWorld.getWorldTime());

        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target != null) {
            JsonObject targetState = new JsonObject();
            targetState.addProperty("type", target.typeOfHit.name().toLowerCase());
            if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                targetState.addProperty("x", target.blockX);
                targetState.addProperty("y", target.blockY);
                targetState.addProperty("z", target.blockZ);
                targetState.addProperty("side", target.sideHit);
            }
            state.add("target", targetState);
        }
        return state;
    }

    private byte[] captureScreenshot() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        File directory = Files.createTempDirectory("craftonica-automation-").toFile();
        File screenshots = new File(directory, "screenshots");
        File screenshot = new File(screenshots, "capture.png");
        try {
            ScreenShotHelper.saveScreenshot(directory, screenshot.getName(), minecraft.displayWidth,
                    minecraft.displayHeight, minecraft.getFramebuffer());
            if (!screenshot.isFile()) {
                throw new IOException("Minecraft did not produce a screenshot");
            }
            return Files.readAllBytes(screenshot.toPath());
        } finally {
            deleteTemporaryFile(screenshot);
            deleteTemporaryFile(screenshots);
            deleteTemporaryFile(directory);
        }
    }

    private void deleteTemporaryFile(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private JsonObject performAction(JsonObject request) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String action = requiredString(request, "action");
        if ("close_screen".equals(action)) {
            minecraft.displayGuiScreen(null);
            if (minecraft.theWorld != null) {
                minecraft.setIngameFocus();
            }
        } else if (minecraft.thePlayer == null) {
            throw new IllegalArgumentException("not_connected");
        } else if ("chat".equals(action)) {
            String text = requiredString(request, "text");
            if (text.length() > 256) {
                throw new IllegalArgumentException("chat text exceeds 256 characters");
            }
            minecraft.thePlayer.sendChatMessage(text);
        } else if ("look".equals(action)) {
            minecraft.thePlayer.rotationYaw = requiredFloat(request, "yaw");
            float pitch = requiredFloat(request, "pitch");
            minecraft.thePlayer.rotationPitch = Math.max(-90.0F, Math.min(90.0F, pitch));
        } else if ("select_hotbar".equals(action)) {
            int slot = requiredInt(request, "slot");
            if (slot < 1 || slot > 9) {
                throw new IllegalArgumentException("slot must be between 1 and 9");
            }
            minecraft.thePlayer.inventory.currentItem = slot - 1;
        } else if ("key".equals(action)) {
            KeyBinding key = findKeyBinding(minecraft, requiredString(request, "key"));
            KeyBinding.setKeyBindState(key.getKeyCode(), requiredBoolean(request, "pressed"));
        } else if ("use".equals(action)) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindUseItem.getKeyCode());
        } else if ("attack".equals(action)) {
            KeyBinding.onTick(minecraft.gameSettings.keyBindAttack.getKeyCode());
        } else {
            throw new IllegalArgumentException("unsupported action: " + action);
        }

        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("action", action);
        return result;
    }

    private KeyBinding findKeyBinding(Minecraft minecraft, String name) {
        if ("forward".equals(name)) {
            return minecraft.gameSettings.keyBindForward;
        }
        if ("back".equals(name)) {
            return minecraft.gameSettings.keyBindBack;
        }
        if ("left".equals(name)) {
            return minecraft.gameSettings.keyBindLeft;
        }
        if ("right".equals(name)) {
            return minecraft.gameSettings.keyBindRight;
        }
        if ("jump".equals(name)) {
            return minecraft.gameSettings.keyBindJump;
        }
        if ("sneak".equals(name)) {
            return minecraft.gameSettings.keyBindSneak;
        }
        throw new IllegalArgumentException("unsupported key: " + name);
    }

    private <T> T onClientThread(Callable<T> callable) throws Exception {
        ListenableFuture<T> future = Minecraft.getMinecraft().func_152343_a(callable);
        try {
            return future.get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception error) {
            future.cancel(false);
            throw error;
        }
    }

    private boolean isAuthorized(Headers headers) {
        String authorization = headers.getFirst("Authorization");
        byte[] expected = ("Bearer " + config.getToken()).getBytes(UTF_8);
        byte[] received = authorization == null ? new byte[0] : authorization.getBytes(UTF_8);
        return MessageDigest.isEqual(expected, received);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("request body exceeds 64 KiB");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), UTF_8);
    }

    private JsonObject parseObject(String json) {
        JsonElement element;
        try {
            element = new JsonParser().parse(json);
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("request body contains invalid JSON", error);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("request body must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing string: " + name);
        }
        return object.get(name).getAsString();
    }

    private int requiredInt(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing number: " + name);
        }
        return object.get(name).getAsInt();
    }

    private float requiredFloat(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing number: " + name);
        }
        float value = object.get(name).getAsFloat();
        if (Float.isInfinite(value) || Float.isNaN(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private boolean requiredBoolean(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing boolean: " + name);
        }
        return object.get(name).getAsBoolean();
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        if (!expected.equals(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("method_not_allowed");
        }
    }

    private JsonObject error(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("error", message == null ? "invalid_request" : message);
        return response;
    }

    private void writeJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        write(exchange, status, "application/json; charset=utf-8", gson.toJson(body).getBytes(UTF_8));
    }

    private void write(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        OutputStream output = exchange.getResponseBody();
        output.write(body);
        output.close();
    }
}
