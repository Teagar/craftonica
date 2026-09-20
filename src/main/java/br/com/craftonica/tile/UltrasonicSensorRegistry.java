package br.com.craftonica.tile;

import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Weak loaded-tile index; entries are removed on invalidation and chunk unload. */
public final class UltrasonicSensorRegistry {
    private static final Map<World, Set<TileEntityUltrasonicSensor>> BY_WORLD =
            new WeakHashMap<World, Set<TileEntityUltrasonicSensor>>();

    private UltrasonicSensorRegistry() { }

    public static synchronized void register(TileEntityUltrasonicSensor sensor) {
        if (sensor == null || sensor.getWorldObj() == null || sensor.getWorldObj().isRemote) return;
        Set<TileEntityUltrasonicSensor> values = BY_WORLD.get(sensor.getWorldObj());
        if (values == null) {
            values = Collections.newSetFromMap(new WeakHashMap<TileEntityUltrasonicSensor, Boolean>());
            BY_WORLD.put(sensor.getWorldObj(), values);
        }
        values.add(sensor);
    }

    public static synchronized void unregister(TileEntityUltrasonicSensor sensor) {
        if (sensor == null || sensor.getWorldObj() == null) return;
        Set<TileEntityUltrasonicSensor> values = BY_WORLD.get(sensor.getWorldObj());
        if (values != null) values.remove(sensor);
    }

    public static synchronized List<TileEntityUltrasonicSensor> loaded(World world) {
        Set<TileEntityUltrasonicSensor> values = BY_WORLD.get(world);
        if (values == null) return Collections.emptyList();
        List<TileEntityUltrasonicSensor> result = new ArrayList<TileEntityUltrasonicSensor>();
        for (TileEntityUltrasonicSensor sensor : values)
            if (sensor != null && !sensor.isInvalid() && sensor.getWorldObj() == world) result.add(sensor);
        return result;
    }

    public static synchronized void unload(World world) { BY_WORLD.remove(world); }
}
