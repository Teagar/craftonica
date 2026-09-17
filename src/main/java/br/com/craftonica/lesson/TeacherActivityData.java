package br.com.craftonica.lesson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TeacherActivityData extends WorldSavedData {
    public static final String DATA_NAME = "craftonica_teacher_activities";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ACTIVITIES = 128;
    private final Map<String, LessonDefinition> activities = new LinkedHashMap<String, LessonDefinition>();
    private String assignedId;
    private boolean writable = true;

    public TeacherActivityData() { super(DATA_NAME); }
    public TeacherActivityData(String name) { super(name); }

    public static TeacherActivityData get(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer canonical = server == null ? null : server.worldServerForDimension(0);
        if (canonical == null && world instanceof WorldServer) canonical = (WorldServer) world;
        if (canonical == null) throw new IllegalStateException("Mundo servidor indisponivel");
        MapStorage storage = canonical.mapStorage;
        TeacherActivityData data = (TeacherActivityData) storage.loadData(TeacherActivityData.class, DATA_NAME);
        if (data == null) { data = new TeacherActivityData(DATA_NAME); storage.setData(DATA_NAME, data); }
        return data;
    }

    public LessonDefinition getActivity(String id) { return activities.get(id); }
    public Set<String> getActivityIds() { return Collections.unmodifiableSet(new LinkedHashSet<String>(activities.keySet())); }
    public String getAssignedId() { return assignedId; }

    public void put(LessonDefinition activity) {
        ensureWritable();
        if (!activity.getId().startsWith("teacher-") || !activities.containsKey(activity.getId())
                && activities.size() >= MAX_ACTIVITIES) throw new IllegalArgumentException("Limite de atividades");
        activities.put(activity.getId(), activity);
        markDirty();
    }

    public void assign(String lessonId) {
        ensureWritable();
        assignedId = lessonId;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        activities.clear(); assignedId = null; writable = true;
        int version = root.getInteger("SchemaVersion");
        if (version <= 0 || version > SCHEMA_VERSION) { writable = false; return; }
        NBTTagList list = root.getTagList("Activities", 10);
        if (list.tagCount() > MAX_ACTIVITIES) { writable = false; return; }
        for (int i = 0; i < list.tagCount(); i++) {
            try {
                LessonDefinition activity = readActivity(list.getCompoundTagAt(i));
                activities.put(activity.getId(), activity);
            } catch (IllegalArgumentException malformed) {
                // Isolate malformed records instead of disabling every valid activity.
            }
        }
        String assigned = root.getString("AssignedId");
        if (LessonCatalog.get(assigned) != null || activities.containsKey(assigned)) assignedId = assigned;
    }

    @Override
    public void writeToNBT(NBTTagCompound root) {
        root.setInteger("SchemaVersion", SCHEMA_VERSION);
        if (assignedId != null) root.setString("AssignedId", assignedId);
        NBTTagList list = new NBTTagList();
        for (LessonDefinition activity : activities.values()) list.appendTag(writeActivity(activity));
        root.setTag("Activities", list);
    }

    private NBTTagCompound writeActivity(LessonDefinition activity) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("Id", activity.getId());
        NBTTagList rules = new NBTTagList();
        for (LessonDefinition.ComponentRule rule : activity.getComponentRules()) {
            NBTTagCompound encoded = new NBTTagCompound(); encoded.setString("Kind", rule.getKind());
            encoded.setInteger("Min", rule.getMinimum()); encoded.setInteger("Max", rule.getMaximum()); rules.appendTag(encoded);
        }
        NBTTagList goals = new NBTTagList();
        for (LessonDefinition.ElectricalGoal goal : activity.getGoals()) {
            NBTTagCompound encoded = new NBTTagCompound(); encoded.setString("Kind", goal.getComponentKind());
            encoded.setString("Quantity", goal.getQuantity().name()); encoded.setDouble("Expected", goal.getExpected());
            encoded.setDouble("Absolute", goal.getAbsoluteTolerance()); encoded.setDouble("Relative", goal.getRelativeTolerance());
            encoded.setBoolean("Magnitude", goal.isMagnitude()); encoded.setString("Quantifier", goal.getQuantifier().name()); goals.appendTag(encoded);
        }
        tag.setTag("Rules", rules); tag.setTag("Goals", goals); return tag;
    }

    private LessonDefinition readActivity(NBTTagCompound tag) {
        String id = tag.getString("Id");
        if (!id.startsWith("teacher-") || !TeacherActivityParser.isValidLessonId(id)) throw new IllegalArgumentException("ID invalido");
        NBTTagList encodedRules = tag.getTagList("Rules", 10);
        NBTTagList encodedGoals = tag.getTagList("Goals", 10);
        if (encodedRules.tagCount() == 0 || encodedRules.tagCount() > 16
                || encodedGoals.tagCount() == 0 || encodedGoals.tagCount() > 8) throw new IllegalArgumentException("Atividade invalida");
        List<LessonDefinition.ComponentRule> rules = new ArrayList<LessonDefinition.ComponentRule>();
        Set<String> allowed = new LinkedHashSet<String>();
        for (int i = 0; i < encodedRules.tagCount(); i++) {
            NBTTagCompound encoded = encodedRules.getCompoundTagAt(i); String kind = encoded.getString("Kind");
            int minimum = encoded.getInteger("Min"), maximum = encoded.getInteger("Max");
            if (!TeacherActivityParser.isKnownKind(kind) || !allowed.add(kind) || minimum < 0 || maximum < minimum || maximum > 1024)
                throw new IllegalArgumentException("Regra invalida");
            rules.add(new LessonDefinition.ComponentRule(kind, minimum, maximum));
        }
        List<LessonDefinition.ElectricalGoal> goals = new ArrayList<LessonDefinition.ElectricalGoal>();
        for (int i = 0; i < encodedGoals.tagCount(); i++) {
            NBTTagCompound encoded = encodedGoals.getCompoundTagAt(i); String kind = encoded.getString("Kind");
            if (!allowed.contains(kind)) throw new IllegalArgumentException("Objetivo invalido");
            double expected = encoded.getDouble("Expected"), absolute = encoded.getDouble("Absolute"), relative = encoded.getDouble("Relative");
            if (!finite(expected) || !finite(absolute) || !finite(relative) || Math.abs(expected) > 1000000.0
                    || absolute < 0.0 || absolute > 1000000.0 || relative < 0.0 || relative > 1.0
                    || !encoded.getBoolean("Magnitude")) throw new IllegalArgumentException("Objetivo fora dos limites");
            goals.add(new LessonDefinition.ElectricalGoal(kind,
                    LessonDefinition.Quantity.valueOf(encoded.getString("Quantity")), expected,
                    absolute, relative, true,
                    LessonDefinition.Quantifier.valueOf(encoded.getString("Quantifier"))));
        }
        return new LessonDefinition(id, allowed, rules, goals);
    }

    private void ensureWritable() { if (!writable) throw new IllegalStateException("Dados de professor usam schema nao suportado"); }
    private boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
