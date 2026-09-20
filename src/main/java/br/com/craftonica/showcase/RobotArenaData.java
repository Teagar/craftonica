package br.com.craftonica.showcase;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.Map;
import java.util.TreeMap;

/** Persistent arena origins, one bounded entry per dimension. */
public final class RobotArenaData extends WorldSavedData {
    public static final String DATA_NAME = "craftonica_robot_arenas";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DIMENSIONS = 32;
    private static final int WORLD_LIMIT = 29999900;
    private final Map<Integer, Origin> origins = new TreeMap<Integer, Origin>();

    public RobotArenaData() { super(DATA_NAME); }
    public RobotArenaData(String name) { super(name); }

    public static RobotArenaData get(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer canonical = server == null ? null : server.worldServerForDimension(0);
        if (canonical == null && world instanceof WorldServer) canonical = (WorldServer) world;
        if (canonical == null) throw new IllegalStateException("Server world unavailable");
        MapStorage storage = canonical.mapStorage;
        RobotArenaData data = (RobotArenaData) storage.loadData(RobotArenaData.class, DATA_NAME);
        if (data == null) { data = new RobotArenaData(DATA_NAME); storage.setData(DATA_NAME, data); }
        return data;
    }

    public Origin getOrigin(int dimension) { return origins.get(Integer.valueOf(dimension)); }

    public void setOrigin(int dimension, int x, int y, int z) {
        validate(x, y, z);
        if (!origins.containsKey(Integer.valueOf(dimension)) && origins.size() >= MAX_DIMENSIONS)
            throw new IllegalStateException("Arena dimension limit");
        origins.put(Integer.valueOf(dimension), new Origin(x, y, z)); markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        origins.clear();
        if (tag == null || tag.getInteger("Schema") != SCHEMA_VERSION) return;
        NBTTagList list = tag.getTagList("Origins", 10);
        if (list.tagCount() > MAX_DIMENSIONS) return;
        try {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound value = list.getCompoundTagAt(i);
                int dimension = value.getInteger("Dimension");
                int x = value.getInteger("X"), y = value.getInteger("Y"), z = value.getInteger("Z");
                validate(x, y, z);
                if (origins.put(Integer.valueOf(dimension), new Origin(x, y, z)) != null)
                    throw new IllegalArgumentException("duplicate dimension");
            }
        } catch (RuntimeException invalid) { origins.clear(); }
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Schema", SCHEMA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, Origin> entry : origins.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setInteger("Dimension", entry.getKey().intValue());
            value.setInteger("X", entry.getValue().x); value.setInteger("Y", entry.getValue().y);
            value.setInteger("Z", entry.getValue().z); list.appendTag(value);
        }
        tag.setTag("Origins", list);
    }

    private static void validate(int x, int y, int z) {
        if (StrictMath.abs((long) x) > WORLD_LIMIT || StrictMath.abs((long) z) > WORLD_LIMIT
                || y < 2 || y > 248) throw new IllegalArgumentException("invalid arena origin");
    }

    public static final class Origin {
        public final int x, y, z;
        Origin(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
}
