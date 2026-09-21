package br.com.craftonica.robot.modular.visual;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded render-only projection. It intentionally excludes netlists, tile NBT and physics. */
public final class ModularRobotVisualState {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MODULES = 256;
    public static final int MAX_PALETTE = 64;
    public static final int MAX_NAME_BYTES = 64;
    public static final int MAX_PAYLOAD_BYTES = 8192;
    public static final int FLAG_WHEEL = 1;
    public static final int FLAG_AXLE = 2;
    public static final int FLAG_DIRECTIONAL = 4;

    private final List<Module> modules;

    private ModularRobotVisualState(List<Module> modules) {
        if (modules == null || modules.isEmpty() || modules.size() > MAX_MODULES)
            throw new IllegalArgumentException("visual module count");
        this.modules = Collections.unmodifiableList(new ArrayList<Module>(modules));
    }

    public static ModularRobotVisualState fromManifest(ModularRobotManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("manifest");
        List<Module> values = new ArrayList<Module>();
        for (ModularBlockSnapshot module : manifest.getModules()) {
            int flags = StandardComponentCatalog.WHEEL.equals(module.componentTypeId)
                    || StandardComponentCatalog.WHEEL_150.equals(module.componentTypeId) ? FLAG_WHEEL
                    : StandardComponentCatalog.AXLE.equals(module.componentTypeId) ? FLAG_AXLE : 0;
            if (!StandardComponentCatalog.WIRE.equals(module.componentTypeId)
                    && !StandardComponentCatalog.CASTER.equals(module.componentTypeId)) flags |= FLAG_DIRECTIONAL;
            values.add(new Module(module.blockRegistryName, module.localPosition,
                    module.localOrientation, module.metadata, flags));
        }
        return new ModularRobotVisualState(values);
    }

    public void write(ByteBuf buffer) {
        if (buffer == null) throw new IllegalArgumentException("buffer");
        int start = buffer.writerIndex(); Map<String, Integer> palette = palette();
        buffer.writeByte(SCHEMA_VERSION); buffer.writeShort(modules.size()); buffer.writeByte(palette.size());
        for (String name : palette.keySet()) writeName(buffer, name);
        for (Module module : modules) {
            buffer.writeByte(palette.get(module.blockRegistryName).intValue());
            buffer.writeByte(module.localPosition.x); buffer.writeByte(module.localPosition.y);
            buffer.writeByte(module.localPosition.z);
            buffer.writeByte(module.orientation.getForward().ordinal()
                    | module.orientation.getUp().ordinal() << 3);
            buffer.writeByte(module.metadata); buffer.writeByte(module.flags);
        }
        if (buffer.writerIndex() - start > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("visual payload too large");
    }

    public static ModularRobotVisualState read(ByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 4 || buffer.readableBytes() > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("visual payload bounds");
        if (buffer.readUnsignedByte() != SCHEMA_VERSION) throw new IllegalArgumentException("visual schema");
        int moduleCount = buffer.readUnsignedShort(), paletteCount = buffer.readUnsignedByte();
        if (moduleCount < 1 || moduleCount > MAX_MODULES || paletteCount < 1 || paletteCount > MAX_PALETTE)
            throw new IllegalArgumentException("visual counts");
        List<String> palette = new ArrayList<String>();
        for (int i = 0; i < paletteCount; i++) palette.add(readName(buffer));
        List<Module> modules = new ArrayList<Module>();
        for (int i = 0; i < moduleCount; i++) {
            if (buffer.readableBytes() < 7) throw new IllegalArgumentException("truncated visual module");
            int paletteIndex = buffer.readUnsignedByte(); int x = buffer.readByte(), y = buffer.readByte();
            int z = buffer.readByte(), orientation = buffer.readUnsignedByte();
            int forward = orientation & 7, up = orientation >> 3 & 7;
            if (paletteIndex >= palette.size() || forward >= Direction.values().length
                    || up >= Direction.values().length) throw new IllegalArgumentException("visual module enum");
            modules.add(new Module(palette.get(paletteIndex), new GridVector(x, y, z),
                    new ComponentOrientation(Direction.values()[forward], Direction.values()[up]),
                    buffer.readUnsignedByte(), buffer.readUnsignedByte()));
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("trailing visual payload");
        return new ModularRobotVisualState(modules);
    }

    public int encodedSize() {
        int size = 4 + modules.size() * 7;
        for (String value : palette().keySet()) size += 1 + value.getBytes(StandardCharsets.UTF_8).length;
        return size;
    }

    public List<Module> getModules() { return modules; }

    private Map<String, Integer> palette() {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        for (Module module : modules) if (!values.containsKey(module.blockRegistryName)) {
            if (values.size() >= MAX_PALETTE) throw new IllegalArgumentException("visual palette");
            values.put(module.blockRegistryName, Integer.valueOf(values.size()));
        }
        return values;
    }
    private static void writeName(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_NAME_BYTES) throw new IllegalArgumentException("block name");
        buffer.writeByte(bytes.length); buffer.writeBytes(bytes);
    }
    private static String readName(ByteBuf buffer) {
        if (!buffer.isReadable()) throw new IllegalArgumentException("missing block name");
        int length = buffer.readUnsignedByte();
        if (length < 1 || length > MAX_NAME_BYTES || buffer.readableBytes() < length)
            throw new IllegalArgumentException("block name bounds");
        byte[] bytes = new byte[length]; buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class Module {
        public final String blockRegistryName;
        public final GridVector localPosition;
        public final ComponentOrientation orientation;
        public final int metadata;
        public final int flags;

        Module(String blockRegistryName, GridVector localPosition, ComponentOrientation orientation,
                int metadata, int flags) {
            if (blockRegistryName == null || blockRegistryName.length() == 0 || localPosition == null
                    || orientation == null || localPosition.x < -16 || localPosition.x > 16
                    || localPosition.y < -16 || localPosition.y > 16 || localPosition.z < -16
                    || localPosition.z > 16 || metadata < 0 || metadata > 15 || (flags & ~7) != 0)
                throw new IllegalArgumentException("visual module");
            this.blockRegistryName = blockRegistryName; this.localPosition = localPosition;
            this.orientation = orientation; this.metadata = metadata; this.flags = flags;
        }
    }
}
