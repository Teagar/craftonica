package br.com.craftonica.sketch.server;

import br.com.craftonica.firmware.CompilationHandle;
import br.com.craftonica.firmware.CompilationRequest;
import br.com.craftonica.firmware.CompilationResult;
import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.firmware.SourceBundle;
import br.com.craftonica.firmware.server.CompilerServer;
import br.com.craftonica.sketch.network.EditorActionMessage;
import br.com.craftonica.sketch.network.EditorStateMessage;
import br.com.craftonica.sketch.network.SketchAction;
import br.com.craftonica.sketch.network.SketchNetwork;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.TileEntityRoboBoard;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/** Main-thread ingress, validation, mutation, and non-blocking compiler completion coordinator. */
public final class SketchServer {
    private static final int MAX_INGRESS = 256;
    private static final int MAX_INGRESS_PER_TICK = 32;
    private static final int MAX_QUEUED_PER_PLAYER = 8;
    private static final double MAX_DISTANCE_SQUARED = 64.0;
    private static final ArrayBlockingQueue<Ingress> INGRESS = new ArrayBlockingQueue<Ingress>(MAX_INGRESS);
    private static final Map<UUID, Pending> BY_PLAYER = new HashMap<UUID, Pending>();
    private static final Map<BoardKey, Pending> BY_BOARD = new HashMap<BoardKey, Pending>();
    private static final Map<UUID, Integer> QUEUED = new HashMap<UUID, Integer>();
    private static final PlayerCompileRateLimiter RATE_LIMITER = new PlayerCompileRateLimiter();

    public static final SketchServer EVENTS = new SketchServer();

    private SketchServer() {}

    public static void start() { CompilerServer.start(); }

    public static void stop() {
        cancelAll();
        CompilerServer.stop();
        INGRESS.clear();
        RATE_LIMITER.clear();
    }

    public static void enqueue(EntityPlayerMP player, EditorActionMessage message) {
        if (player == null || message == null || !message.isValid()) return;
        UUID playerId = player.getUniqueID();
        synchronized (QUEUED) {
            Integer count = QUEUED.get(playerId);
            if (count != null && count >= MAX_QUEUED_PER_PLAYER) return;
            if (!INGRESS.offer(new Ingress(player, playerId, message))) return;
            QUEUED.put(playerId, count == null ? 1 : count + 1);
        }
    }

    public static void openEditor(EntityPlayerMP player, TileEntityRoboBoard board) {
        if (player == null || board == null || board.getWorldObj() == null || board.getWorldObj().isRemote
                || player.worldObj != board.getWorldObj()
                || player.getDistanceSq(board.xCoord + 0.5, board.yCoord + 0.5, board.zCoord + 0.5)
                > MAX_DISTANCE_SQUARED) return;
        if (board.getOwnerId() == null) board.claimOwner(player.getUniqueID());
        if (board.canAccess(player)) sendState(player, board, "craftonica.editor.ready", "");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Ingress ingress;
        for (int processed = 0; processed < MAX_INGRESS_PER_TICK && (ingress = INGRESS.poll()) != null; processed++) {
            decrementQueued(ingress.playerId);
            process(ingress);
        }
        pollCompilations();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) cancelDimension(event.world.provider.dimensionId);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) cancelPlayer(event.player.getUniqueID());
    }

    private static void process(Ingress ingress) {
        EntityPlayerMP player = ingress.player;
        EditorActionMessage request = ingress.request;
        if (!authenticated(ingress)) return;
        if (player.dimension != request.getDimension() || !(player.worldObj instanceof WorldServer)) return;
        WorldServer world = (WorldServer) player.worldObj;
        if (!world.blockExists(request.getX(), request.getY(), request.getZ())) return;
        if (player.getDistanceSq(request.getX() + 0.5, request.getY() + 0.5, request.getZ() + 0.5)
                > MAX_DISTANCE_SQUARED) return;
        TileEntity tile = world.getTileEntity(request.getX(), request.getY(), request.getZ());
        if (!(tile instanceof TileEntityRoboBoard)) return;
        TileEntityRoboBoard board = (TileEntityRoboBoard) tile;
        if (!request.getBoardId().equals(board.getBoardId()) || request.getGeneration() != board.getGeneration()) return;
        if (!board.canAccess(player)) return;
        if (request.getAction() == SketchAction.REFRESH) {
            sendState(player, board, compilationCode(board, player.getUniqueID()), "");
            return;
        }
        if (request.getExpectedRevision() != board.getRevision()) {
            sendState(player, board, "craftonica.editor.stale", "");
            return;
        }

        if (request.getAction() == SketchAction.START) {
            mutate(player, board, request.getExpectedRevision(), true);
        } else if (request.getAction() == SketchAction.STOP) {
            mutate(player, board, request.getExpectedRevision(), false);
        } else if (request.getAction() == SketchAction.COMPILE) {
            compile(player, board, request);
        }
    }

    private static void mutate(EntityPlayerMP player, TileEntityRoboBoard board, long revision, boolean start) {
        try {
            if (start) board.startFirmware(revision); else board.stopFirmware(revision);
            sendState(player, board, start ? "craftonica.editor.started" : "craftonica.editor.stopped", "");
        } catch (RuntimeException rejected) {
            sendState(player, board, "craftonica.editor.action_rejected", "");
        }
    }

    private static void compile(EntityPlayerMP player, TileEntityRoboBoard board, EditorActionMessage request) {
        UUID playerId = player.getUniqueID();
        BoardKey boardKey = BoardKey.of(board);
        if (BY_PLAYER.containsKey(playerId) || BY_BOARD.containsKey(boardKey)) {
            sendState(player, board, "craftonica.editor.compile_busy", "");
            return;
        }
        if (!RATE_LIMITER.tryAcquire(playerId)) {
            sendState(player, board, "craftonica.editor.rate_limited", "");
            return;
        }
        SourceBundle sources;
        try {
            sources = new SourceBundle(RoboBoardState.SKETCH_NAME,
                    Collections.singletonMap(RoboBoardState.SKETCH_FILE, request.getSource()));
        } catch (IllegalArgumentException invalid) {
            sendState(player, board, "craftonica.editor.invalid_source", "");
            return;
        }
        CompilationRequest compilation = new CompilationRequest(playerId.toString(), UUID.randomUUID(),
                request.getExpectedRevision(), sources);
        CompilationHandle handle = CompilerServer.submit(compilation);
        if (handle == null) {
            sendState(player, board, "craftonica.editor.compiler_unavailable", "");
            return;
        }
        Pending pending = new Pending(playerId, boardKey, request.getGeneration(), request.getExpectedRevision(),
                sources, handle);
        BY_PLAYER.put(playerId, pending);
        BY_BOARD.put(boardKey, pending);
        sendState(player, board, "craftonica.editor.compiling", "");
    }

    private static void pollCompilations() {
        List<Pending> completed = new ArrayList<Pending>();
        for (Pending pending : BY_PLAYER.values()) if (pending.handle.isDone()) completed.add(pending);
        for (Pending pending : completed) {
            BY_PLAYER.remove(pending.playerId);
            BY_BOARD.remove(pending.boardKey);
            CompilationResult result;
            try {
                result = pending.handle.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            complete(pending, result);
        }
    }

    private static void complete(Pending pending, CompilationResult result) {
        EntityPlayerMP player = onlinePlayer(pending.playerId);
        WorldServer world = DimensionManager.getWorld(pending.boardKey.dimension);
        TileEntityRoboBoard board = loadedBoard(world, pending.boardKey.x, pending.boardKey.y, pending.boardKey.z);
        String diagnostics = diagnostics(result);
        if (!canView(player, board)) return;
        if (result == null || !result.isSuccess()) {
            sendState(player, board, resultCode(result), diagnostics);
            return;
        }
        CRLFirmware firmware = result.getFirmware();
        byte[] sourceHash = pending.sources.getSourceHash();
        if (board == null || !CompilationTarget.matches(pending.boardKey.boardId, pending.generation,
                pending.revision, sourceHash, board.getBoardId(), board.getGeneration(), board.getRevision(),
                firmware.getSourceHash())) {
            if (canView(player, board)) sendState(player, board, "craftonica.editor.compile_stale", diagnostics);
            return;
        }
        try {
            board.installCompiledSketch(pending.sources, firmware, pending.revision);
            sendState(player, board, "craftonica.editor.compile_success", diagnostics);
        } catch (RuntimeException stale) {
            if (canView(player, board)) sendState(player, board, "craftonica.editor.compile_stale", diagnostics);
        }
    }

    private static boolean canView(EntityPlayerMP player, TileEntityRoboBoard board) {
        return player != null && board != null && player.isEntityAlive() && player.playerNetServerHandler != null
                && board.canAccess(player)
                && player.worldObj == board.getWorldObj()
                && player.getDistanceSq(board.xCoord + 0.5, board.yCoord + 0.5, board.zCoord + 0.5)
                <= MAX_DISTANCE_SQUARED;
    }

    private static TileEntityRoboBoard loadedBoard(WorldServer world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityRoboBoard ? (TileEntityRoboBoard) tile : null;
    }

    private static boolean authenticated(Ingress ingress) {
        EntityPlayerMP player = ingress.player;
        return player != null && player.isEntityAlive() && player.playerNetServerHandler != null
                && ingress.playerId.equals(player.getUniqueID()) && player.worldObj != null
                && player.worldObj.playerEntities.contains(player);
    }

    private static EntityPlayerMP onlinePlayer(UUID id) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object value : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (id.equals(player.getUniqueID()) && player.isEntityAlive()) return player;
        }
        return null;
    }

    private static String compilationCode(TileEntityRoboBoard board, UUID playerId) {
        Pending pending = BY_BOARD.get(BoardKey.of(board));
        if (pending == null) return "craftonica.editor.ready";
        return pending.playerId.equals(playerId) ? "craftonica.editor.compiling" : "craftonica.editor.compile_busy";
    }

    private static String diagnostics(CompilationResult result) {
        if (result == null) return "";
        StringBuilder value = new StringBuilder();
        for (String line : result.getDiagnostics().getEntries()) {
            if (value.length() > 0) value.append('\n');
            value.append(line);
        }
        return value.toString();
    }

    private static String resultCode(CompilationResult result) {
        if (result == null) return "craftonica.editor.compiler_unavailable";
        switch (result.getStatus()) {
            case COMPILE_ERROR: return "craftonica.editor.compile_error";
            case COMPILE_LIMIT: return "craftonica.editor.compile_limit";
            case VERIFY_REJECTED: return "craftonica.editor.verify_rejected";
            case REJECTED: return "craftonica.editor.compile_busy";
            case CANCELLED: return "craftonica.editor.compile_cancelled";
            default: return "craftonica.editor.compiler_unavailable";
        }
    }

    private static void sendState(EntityPlayerMP player, TileEntityRoboBoard board, String code, String diagnostics) {
        RoboBoardState.SerialHistorySnapshot history = board.getSerialHistorySnapshot();
        byte[] source = board.hasInstalledSketchSource() ? board.getInstalledSketchSource() : new byte[0];
        EditorStateMessage state = new EditorStateMessage(board.getWorldObj().provider.dimensionId,
                board.xCoord, board.yCoord, board.zCoord, board.getBoardId(), board.getGeneration(),
                board.getRevision(), board.getStatus().ordinal(), board.getFault(), source, code, diagnostics,
                history.getBytes(), history.getStartOffset(), history.getEndOffset(), history.isTruncated());
        SketchNetwork.sendTo(player, state);
    }

    private static void cancelDimension(int dimension) {
        Iterator<Pending> iterator = BY_PLAYER.values().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (pending.boardKey.dimension != dimension) continue;
            pending.handle.cancel();
            BY_BOARD.remove(pending.boardKey);
            iterator.remove();
        }
        Iterator<Ingress> ingress = INGRESS.iterator();
        while (ingress.hasNext()) {
            Ingress queued = ingress.next();
            if (queued.request.getDimension() == dimension) { ingress.remove(); decrementQueued(queued.playerId); }
        }
    }

    private static void cancelAll() {
        for (Pending pending : BY_PLAYER.values()) pending.handle.cancel();
        BY_PLAYER.clear();
        BY_BOARD.clear();
        synchronized (QUEUED) { QUEUED.clear(); }
    }

    private static void cancelPlayer(UUID playerId) {
        Pending pending = BY_PLAYER.remove(playerId);
        if (pending != null) { pending.handle.cancel(); BY_BOARD.remove(pending.boardKey); }
        Iterator<Ingress> ingress = INGRESS.iterator();
        while (ingress.hasNext()) if (playerId.equals(ingress.next().playerId)) ingress.remove();
        synchronized (QUEUED) { QUEUED.remove(playerId); }
        RATE_LIMITER.remove(playerId);
    }

    private static void decrementQueued(UUID playerId) {
        synchronized (QUEUED) {
            Integer count = QUEUED.get(playerId);
            if (count == null || count <= 1) QUEUED.remove(playerId);
            else QUEUED.put(playerId, count - 1);
        }
    }

    private static final class Ingress {
        final EntityPlayerMP player;
        final UUID playerId;
        final EditorActionMessage request;
        Ingress(EntityPlayerMP player, UUID playerId, EditorActionMessage request) {
            this.player = player; this.playerId = playerId; this.request = request;
        }
    }

    private static final class Pending {
        final UUID playerId;
        final BoardKey boardKey;
        final long generation, revision;
        final SourceBundle sources;
        final CompilationHandle handle;
        Pending(UUID playerId, BoardKey boardKey, long generation, long revision,
                SourceBundle sources, CompilationHandle handle) {
            this.playerId = playerId; this.boardKey = boardKey; this.generation = generation;
            this.revision = revision; this.sources = sources; this.handle = handle;
        }
    }

    private static final class BoardKey {
        final int dimension, x, y, z;
        final UUID boardId;
        private BoardKey(int dimension, int x, int y, int z, UUID boardId) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.boardId = boardId;
        }
        static BoardKey of(TileEntityRoboBoard board) {
            return new BoardKey(board.getWorldObj().provider.dimensionId, board.xCoord, board.yCoord,
                    board.zCoord, board.getBoardId());
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof BoardKey)) return false;
            BoardKey that = (BoardKey) other;
            return dimension == that.dimension && x == that.x && y == that.y && z == that.z
                    && boardId.equals(that.boardId);
        }
        @Override public int hashCode() {
            int result = boardId.hashCode();
            result = 31 * result + dimension; result = 31 * result + x;
            result = 31 * result + y; result = 31 * result + z;
            return result;
        }
    }
}
