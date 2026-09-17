package br.com.craftonica.lesson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LessonProgressData extends WorldSavedData {
    public static final String DATA_NAME = "craftonica_lessons";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_PLAYERS = 4096;
    private static final int MAX_COMPLETED = 256;

    private final Map<UUID, Progress> players = new HashMap<UUID, Progress>();
    private boolean writable = true;

    public LessonProgressData() { super(DATA_NAME); }
    public LessonProgressData(String name) { super(name); }

    public static LessonProgressData get(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer canonical = server == null ? null : server.worldServerForDimension(0);
        if (canonical == null && world instanceof WorldServer) canonical = (WorldServer) world;
        if (canonical == null) throw new IllegalStateException("Mundo servidor indisponivel");
        MapStorage storage = canonical.mapStorage;
        LessonProgressData data = (LessonProgressData) storage.loadData(LessonProgressData.class, DATA_NAME);
        if (data == null) {
            data = new LessonProgressData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public String getActive(UUID player) {
        Progress progress = players.get(player);
        return progress == null ? null : progress.active;
    }

    public Set<String> getCompleted(UUID player) {
        Progress progress = players.get(player);
        return progress == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(progress.completed));
    }

    public boolean start(UUID player, String lessonId) {
        ensureWritable();
        Progress progress = progress(player);
        if (lessonId.equals(progress.active)) return false;
        progress.active = lessonId;
        markDirty();
        return true;
    }

    public boolean complete(UUID player, String lessonId) {
        ensureWritable();
        Progress progress = progress(player);
        boolean changed = progress.completed.add(lessonId);
        if (lessonId.equals(progress.active)) { progress.active = null; changed = true; }
        if (changed) markDirty();
        return changed;
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        players.clear();
        writable = true;
        int version = root.getInteger("SchemaVersion");
        if (version <= 0 || version > SCHEMA_VERSION) { writable = false; return; }
        NBTTagList list = root.getTagList("Players", 10);
        if (list.tagCount() > MAX_PLAYERS) { writable = false; return; }
        int count = list.tagCount();
        for (int i = 0; i < count; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            try {
                UUID id = UUID.fromString(tag.getString("UUID"));
                Progress progress = new Progress();
                String active = tag.getString("ActiveLesson");
                if (LessonCatalog.get(active) != null) progress.active = active;
                NBTTagList completed = tag.getTagList("Completed", 10);
                for (int j = 0; j < Math.min(completed.tagCount(), MAX_COMPLETED); j++) {
                    String lessonId = completed.getCompoundTagAt(j).getString("Id");
                    if (LessonCatalog.get(lessonId) != null) progress.completed.add(lessonId);
                }
                players.put(id, progress);
            } catch (IllegalArgumentException malformedUuid) {
                // Ignore malformed external save data without granting progress.
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound root) {
        root.setInteger("SchemaVersion", SCHEMA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Progress> entry : players.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("UUID", entry.getKey().toString());
            if (entry.getValue().active != null) tag.setString("ActiveLesson", entry.getValue().active);
            NBTTagList completed = new NBTTagList();
            for (String lessonId : entry.getValue().completed) {
                NBTTagCompound lesson = new NBTTagCompound();
                lesson.setString("Id", lessonId);
                completed.appendTag(lesson);
            }
            tag.setTag("Completed", completed);
            list.appendTag(tag);
        }
        root.setTag("Players", list);
    }

    private Progress progress(UUID player) {
        Progress progress = players.get(player);
        if (progress == null) { progress = new Progress(); players.put(player, progress); }
        return progress;
    }

    private void ensureWritable() {
        if (!writable) throw new IllegalStateException("Dados de licao usam schema nao suportado");
    }

    private static final class Progress {
        private String active;
        private final Set<String> completed = new LinkedHashSet<String>();
    }
}
