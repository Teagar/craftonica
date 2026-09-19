package br.com.craftonica.lesson;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.SaveHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class TeacherProgressExporter {
    private TeacherProgressExporter() { }

    public static File export(WorldServer requestingWorld) throws IOException {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer world = server == null ? requestingWorld : server.worldServerForDimension(0);
        if (world == null || !(world.getSaveHandler() instanceof SaveHandler))
            throw new IOException("Diretorio do mundo indisponivel");
        File worldDirectory = ((SaveHandler) world.getSaveHandler()).getWorldDirectory();
        File directory = new File(worldDirectory, "craftonica/exports");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Nao foi possivel criar diretorio");
        File target = new File(directory, "lesson-completions.csv");
        File temporary = new File(directory, "lesson-completions.csv.tmp");
        String worldPath = worldDirectory.getCanonicalPath() + File.separator;
        String directoryPath = directory.getCanonicalPath() + File.separator;
        if (!directoryPath.startsWith(worldPath) || !target.getCanonicalPath().startsWith(directoryPath)
                || !temporary.getCanonicalPath().startsWith(directoryPath))
            throw new IOException("Caminho de exportacao invalido");
        Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), "UTF-8");
        try { writer.write(renderCsv(LessonProgressData.get(world).getCompletionCounts())); }
        finally { writer.close(); }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unavailable) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    static String renderCsv(Map<String, Integer> completionCounts) {
        StringBuilder result = new StringBuilder("schema_version,1\nlesson_id,completions\n");
        for (Map.Entry<String, Integer> entry : completionCounts.entrySet()) {
            if (!TeacherActivityParser.isValidLessonId(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0)
                continue;
            result.append(entry.getKey()).append(',').append(entry.getValue()).append('\n');
        }
        return result.toString();
    }
}
