package br.com.craftonica.sensor;

import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.SaveHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Atomically merges nearby board batches into one bounded world-save dataset. */
public final class UltrasonicMetrologyExporter {
    private static final double RANGE_SQ = 64.0 * 64.0;
    private UltrasonicMetrologyExporter() { }

    public static Result export(WorldServer world, EntityPlayerMP player) throws IOException {
        if (world == null || player == null || player.worldObj != world
                || !(world.getSaveHandler() instanceof SaveHandler)) throw new IOException("World directory unavailable");
        File root = ((SaveHandler) world.getSaveHandler()).getWorldDirectory();
        File directory = new File(root, "craftonica/exports");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create export directory");
        File raw = checked(root, directory, "hc-sr04-samples.csv");
        File metrics = checked(root, directory, "hc-sr04-metrics.csv");
        UltrasonicMetrologyDataset dataset = new UltrasonicMetrologyDataset();
        if (raw.isFile()) {
            try { dataset.merge(readBounded(raw)); }
            catch (IllegalArgumentException invalid) { throw new IOException("Stored metrology CSV is invalid", invalid); }
        }
        int boards = 0;
        for (Object value : world.loadedTileEntityList) if (value instanceof TileEntityRoboBoard) {
            TileEntityRoboBoard board = (TileEntityRoboBoard) value;
            if (!player.getUniqueID().equals(board.getOwnerId())
                    || player.getDistanceSq(board.xCoord + 0.5, board.yCoord + 0.5, board.zCoord + 0.5) > RANGE_SQ)
                continue;
            RoboBoardState.SerialHistorySnapshot history = board.getSerialHistorySnapshot();
            if (history.isTruncated()) throw new IOException("Serial history truncated");
            byte[] bytes = history.getBytes();
            if (!hasMetrologyHeader(bytes)) continue;
            try { dataset.merge(bytes); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid metrology CSV", invalid); }
            boards++;
        }
        writeAtomic(raw, dataset.renderRawCsv());
        writeAtomic(metrics, dataset.renderMetricsCsv());
        return new Result(raw, metrics, boards, dataset.size(), dataset.missingCount(), dataset.isComplete());
    }

    private static byte[] readBounded(File file) throws IOException {
        long length = file.length();
        if (length < 0 || length > UltrasonicMetrologyDataset.MAX_INPUT_BYTES) throw new IOException("Dataset too large");
        byte[] bytes = new byte[(int) length];
        FileInputStream input = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw new IOException("Unexpected dataset EOF");
                offset += read;
            }
            return bytes;
        } finally { input.close(); }
    }

    private static boolean hasMetrologyHeader(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII)
                .contains("material,nominal_cm,amostra,medida_cm,eco");
    }

    private static File checked(File root, File directory, String name) throws IOException {
        File target = new File(directory, name);
        String rootPath = root.getCanonicalPath() + File.separator;
        String directoryPath = directory.getCanonicalPath() + File.separator;
        if (!directoryPath.startsWith(rootPath) || !target.getCanonicalPath().startsWith(directoryPath))
            throw new IOException("Invalid export path");
        return target;
    }

    private static void writeAtomic(File target, String text) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary);
        try { output.write(text.getBytes(StandardCharsets.UTF_8)); output.getFD().sync(); }
        finally { output.close(); }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unavailable) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class Result {
        public final File rawFile, metricsFile;
        public final int boardsMerged, samples, missing;
        public final boolean complete;
        Result(File rawFile, File metricsFile, int boardsMerged, int samples, int missing, boolean complete) {
            this.rawFile = rawFile; this.metricsFile = metricsFile; this.boardsMerged = boardsMerged;
            this.samples = samples; this.missing = missing; this.complete = complete;
        }
    }
}
