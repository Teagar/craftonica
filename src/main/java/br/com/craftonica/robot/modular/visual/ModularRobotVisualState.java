package br.com.craftonica.robot.modular.visual;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.JointProfile;
import br.com.craftonica.robot.modular.Vector3;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.physics.articulated.RigidTransform3;
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
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_MODULES = 256;
    public static final int MAX_PALETTE = 64;
    public static final int MAX_NAME_BYTES = 64;
    public static final int MAX_PAYLOAD_BYTES = 8192;
    public static final int FLAG_WHEEL = 1;
    public static final int FLAG_AXLE = 2;
    public static final int FLAG_DIRECTIONAL = 4;

    private final List<Module> modules;
    private final List<Joint> joints;
    private final int bodyCount;

    private ModularRobotVisualState(List<Module> modules, List<Joint> joints, int bodyCount) {
        if (modules == null || modules.isEmpty() || modules.size() > MAX_MODULES)
            throw new IllegalArgumentException("visual module count");
        if (joints == null || joints.size() > 31 || bodyCount < 1 || bodyCount > 32)
            throw new IllegalArgumentException("visual body count");
        this.modules = Collections.unmodifiableList(new ArrayList<Module>(modules));
        this.joints = Collections.unmodifiableList(new ArrayList<Joint>(joints)); this.bodyCount = bodyCount;
    }

    public static ModularRobotVisualState fromManifest(ModularRobotManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("manifest");
        ComponentCatalog catalog = StandardComponentCatalog.create();
        KinematicAssembly kinematic = KinematicAssemblyAnalyzer.analyze(manifest, catalog);
        if (!kinematic.isValid()) throw new IllegalArgumentException("visual kinematic assembly");
        Map<GridVector,Integer> bodyByModule = new LinkedHashMap<GridVector,Integer>();
        for (KinematicAssembly.Body body : kinematic.getBodies())
            for (GridVector position : body.modulePositions) bodyByModule.put(position, Integer.valueOf(body.bodyId));
        List<Module> values = new ArrayList<Module>();
        for (ModularBlockSnapshot module : manifest.getModules()) {
            int flags = StandardComponentCatalog.WHEEL.equals(module.componentTypeId)
                    || StandardComponentCatalog.WHEEL_150.equals(module.componentTypeId)
                    || StandardComponentCatalog.OMNI_WHEEL.equals(module.componentTypeId)
                    || StandardComponentCatalog.MECANUM_LEFT.equals(module.componentTypeId)
                    || StandardComponentCatalog.MECANUM_RIGHT.equals(module.componentTypeId) ? FLAG_WHEEL
                    : StandardComponentCatalog.AXLE.equals(module.componentTypeId) ? FLAG_AXLE : 0;
            if (!StandardComponentCatalog.WIRE.equals(module.componentTypeId)
                    && !StandardComponentCatalog.CASTER.equals(module.componentTypeId)) flags |= FLAG_DIRECTIONAL;
            values.add(new Module(module.blockRegistryName, module.localPosition,
                    module.localOrientation, module.metadata, flags,
                    bodyByModule.containsKey(module.localPosition) ? bodyByModule.get(module.localPosition).intValue() : 0));
        }
        List<Joint> joints = new ArrayList<Joint>();
        for (KinematicAssembly.Joint joint : kinematic.getJoints()) joints.add(new Joint(joint.modulePosition,
                joint.parentBodyId, joint.childBodyId, joint.kind, joint.axis));
        return new ModularRobotVisualState(values, joints, kinematic.getBodies().size());
    }

    public void write(ByteBuf buffer) {
        if (buffer == null) throw new IllegalArgumentException("buffer");
        int start = buffer.writerIndex(); Map<String, Integer> palette = palette();
        buffer.writeByte(SCHEMA_VERSION); buffer.writeShort(modules.size()); buffer.writeByte(palette.size());
        buffer.writeByte(bodyCount); buffer.writeByte(joints.size());
        for (String name : palette.keySet()) writeName(buffer, name);
        for (Module module : modules) {
            buffer.writeByte(palette.get(module.blockRegistryName).intValue());
            buffer.writeByte(module.localPosition.x); buffer.writeByte(module.localPosition.y);
            buffer.writeByte(module.localPosition.z);
            buffer.writeByte(module.orientation.getForward().ordinal()
                    | module.orientation.getUp().ordinal() << 3);
            buffer.writeByte(module.metadata); buffer.writeByte(module.flags); buffer.writeByte(module.bodyId);
        }
        for (Joint joint : joints) {
            buffer.writeByte(joint.position.x); buffer.writeByte(joint.position.y); buffer.writeByte(joint.position.z);
            buffer.writeByte(joint.parentBodyId); buffer.writeByte(joint.childBodyId);
            buffer.writeByte(joint.kind.ordinal()); buffer.writeByte(joint.axis.ordinal());
        }
        if (buffer.writerIndex() - start > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("visual payload too large");
    }

    public static ModularRobotVisualState read(ByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 6 || buffer.readableBytes() > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("visual payload bounds");
        if (buffer.readUnsignedByte() != SCHEMA_VERSION) throw new IllegalArgumentException("visual schema");
        int moduleCount = buffer.readUnsignedShort(), paletteCount = buffer.readUnsignedByte();
        int bodyCount = buffer.readUnsignedByte(), jointCount = buffer.readUnsignedByte();
        if (moduleCount < 1 || moduleCount > MAX_MODULES || paletteCount < 1 || paletteCount > MAX_PALETTE)
            throw new IllegalArgumentException("visual counts");
        if (bodyCount < 1 || bodyCount > 32 || jointCount > 31) throw new IllegalArgumentException("visual kinematic counts");
        List<String> palette = new ArrayList<String>();
        for (int i = 0; i < paletteCount; i++) palette.add(readName(buffer));
        List<Module> modules = new ArrayList<Module>();
        for (int i = 0; i < moduleCount; i++) {
            if (buffer.readableBytes() < 8) throw new IllegalArgumentException("truncated visual module");
            int paletteIndex = buffer.readUnsignedByte(); int x = buffer.readByte(), y = buffer.readByte();
            int z = buffer.readByte(), orientation = buffer.readUnsignedByte();
            int forward = orientation & 7, up = orientation >> 3 & 7;
            if (paletteIndex >= palette.size() || forward >= Direction.values().length
                    || up >= Direction.values().length) throw new IllegalArgumentException("visual module enum");
            int metadata = buffer.readUnsignedByte(), flags = buffer.readUnsignedByte(), bodyId = buffer.readUnsignedByte();
            modules.add(new Module(palette.get(paletteIndex), new GridVector(x, y, z),
                    new ComponentOrientation(Direction.values()[forward], Direction.values()[up]),
                    metadata, flags, bodyId));
        }
        List<Joint> joints = new ArrayList<Joint>();
        for (int i=0;i<jointCount;i++) {
            if (buffer.readableBytes()<7) throw new IllegalArgumentException("truncated visual joint");
            GridVector position=new GridVector(buffer.readByte(),buffer.readByte(),buffer.readByte());
            int parent=buffer.readUnsignedByte(),child=buffer.readUnsignedByte(),kind=buffer.readUnsignedByte(),axis=buffer.readUnsignedByte();
            if(parent>=bodyCount||child>=bodyCount||kind>=JointProfile.Kind.values().length||axis>=Direction.values().length)
                throw new IllegalArgumentException("visual joint enum");
            joints.add(new Joint(position,parent,child,JointProfile.Kind.values()[kind],Direction.values()[axis]));
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("trailing visual payload");
        return new ModularRobotVisualState(modules,joints,bodyCount);
    }

    public int encodedSize() {
        int size = 6 + modules.size() * 8 + joints.size() * 7;
        for (String value : palette().keySet()) size += 1 + value.getBytes(StandardCharsets.UTF_8).length;
        return size;
    }

    public List<Module> getModules() { return modules; }
    public List<Joint> getJoints() { return joints; }
    public int getBodyCount() { return bodyCount; }

    public List<RigidTransform3> bodyTransforms(JointStateSet states) {
        if (states == null || states.getEntries().size() != joints.size()) throw new IllegalArgumentException("visual joint states");
        Map<GridVector,Double> coordinates=new LinkedHashMap<GridVector,Double>();
        for(JointStateSet.Entry entry:states.getEntries())coordinates.put(entry.position,Double.valueOf(entry.state.position));
        List<RigidTransform3> transforms=new ArrayList<RigidTransform3>(Collections.nCopies(bodyCount,(RigidTransform3)null));
        transforms.set(0,RigidTransform3.identity());int unresolved=joints.size();
        for(int pass=0;pass<joints.size()+1&&unresolved>0;pass++)for(Joint joint:joints){
            if(transforms.get(joint.childBodyId)!=null||transforms.get(joint.parentBodyId)==null)continue;
            Double coordinate=coordinates.get(joint.position);if(coordinate==null)throw new IllegalArgumentException("visual joint coordinate");
            RigidTransform3 parent=transforms.get(joint.parentBodyId);
            Vector3 pivot=parent.point(new Vector3(joint.position.x,joint.position.y+0.5,joint.position.z));
            Vector3 axis=parent.direction(new Vector3(joint.axis.vector.x,joint.axis.vector.y,joint.axis.vector.z));
            RigidTransform3 delta=joint.kind==JointProfile.Kind.REVOLUTE
                    ?RigidTransform3.rotation(axis,coordinate.doubleValue(),pivot)
                    :RigidTransform3.translation(RigidTransform3.scale(axis,coordinate.doubleValue()));
            transforms.set(joint.childBodyId,delta.after(parent));unresolved--;}
        if(unresolved!=0)throw new IllegalArgumentException("visual joint tree");
        return Collections.unmodifiableList(transforms);
    }

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
        public final int bodyId;

        Module(String blockRegistryName, GridVector localPosition, ComponentOrientation orientation,
                int metadata, int flags, int bodyId) {
            if (blockRegistryName == null || blockRegistryName.length() == 0 || localPosition == null
                    || orientation == null || localPosition.x < -16 || localPosition.x > 16
                    || localPosition.y < -16 || localPosition.y > 16 || localPosition.z < -16
                    || localPosition.z > 16 || metadata < 0 || metadata > 15 || (flags & ~7) != 0
                    || bodyId < 0 || bodyId > 31)
                throw new IllegalArgumentException("visual module");
            this.blockRegistryName = blockRegistryName; this.localPosition = localPosition;
            this.orientation = orientation; this.metadata = metadata; this.flags = flags;
            this.bodyId = bodyId;
        }
    }

    public static final class Joint {
        public final GridVector position; public final int parentBodyId,childBodyId;
        public final JointProfile.Kind kind; public final Direction axis;
        Joint(GridVector position,int parent,int child,JointProfile.Kind kind,Direction axis){
            if(position==null||parent<0||parent>31||child<0||child>31||parent==child||kind==null||axis==null)
                throw new IllegalArgumentException("visual joint");
            this.position=position;this.parentBodyId=parent;this.childBodyId=child;this.kind=kind;this.axis=axis;
        }
    }
}
