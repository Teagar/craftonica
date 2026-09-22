package br.com.craftonica.robot.modular.joint;

import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Base64;

/** Bounded canonical server state for joint coordinates; clients receive a read-only projection. */
public final class JointStateSet {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JOINTS = 31;
    public static final JointStateSet EMPTY = new JointStateSet(Collections.<Entry>emptyList());
    private final List<Entry> entries;

    public JointStateSet(List<Entry> entries) {
        if (entries == null || entries.size() > MAX_JOINTS) throw new IllegalArgumentException("joint state count");
        List<Entry> copy = new ArrayList<Entry>(entries); Set<GridVector> positions = new HashSet<GridVector>();
        GridVector previous = null;
        for (Entry entry : copy) {
            if (entry == null || !positions.add(entry.position)
                    || previous != null && compare(previous, entry.position) >= 0)
                throw new IllegalArgumentException("joint state order");
            previous = entry.position;
        }
        this.entries = Collections.unmodifiableList(copy);
    }

    public static JointStateSet initial(KinematicAssembly assembly) {
        if (assembly == null || !assembly.isValid()) throw new IllegalArgumentException("kinematic assembly");
        List<Entry> values = new ArrayList<Entry>();
        for (KinematicAssembly.Joint joint : assembly.getJoints()) {
            double position = StrictMath.max(joint.minimumPosition, StrictMath.min(joint.maximumPosition, 0.0));
            values.add(new Entry(joint.modulePosition, new JointState(position, 0.0)));
        }
        return new JointStateSet(values);
    }

    public List<Entry> getEntries() { return entries; }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Schema", SCHEMA_VERSION);
        NBTTagList values = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound value = new NBTTagCompound();
            value.setInteger("X", entry.position.x); value.setInteger("Y", entry.position.y);
            value.setInteger("Z", entry.position.z); value.setDouble("Position", entry.state.position);
            value.setDouble("Velocity", entry.state.velocity); values.appendTag(value);
        }
        tag.setTag("Values", values); return tag;
    }

    public static JointStateSet read(NBTTagCompound tag, KinematicAssembly assembly) {
        if (tag == null || assembly == null || !assembly.isValid()
                || tag.getInteger("Schema") != SCHEMA_VERSION) throw new IllegalArgumentException("joint state schema");
        NBTTagList values = tag.getTagList("Values", 10);
        if (values.tagCount() != assembly.getJoints().size() || values.tagCount() > MAX_JOINTS)
            throw new IllegalArgumentException("joint state count");
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < values.tagCount(); i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            if (!value.hasKey("X") || !value.hasKey("Y") || !value.hasKey("Z")
                    || !value.hasKey("Position") || !value.hasKey("Velocity"))
                throw new IllegalArgumentException("joint state fields");
            KinematicAssembly.Joint joint = assembly.getJoints().get(i);
            GridVector position = new GridVector(value.getInteger("X"), value.getInteger("Y"), value.getInteger("Z"));
            JointState state = new JointState(value.getDouble("Position"), value.getDouble("Velocity"));
            if (!position.equals(joint.modulePosition) || state.position < joint.minimumPosition
                    || state.position > joint.maximumPosition || StrictMath.abs(state.velocity) > joint.maximumVelocity)
                throw new IllegalArgumentException("joint state bounds");
            entries.add(new Entry(position, state));
        }
        return new JointStateSet(entries);
    }

    public void writeClient(ByteBuf buffer) {
        if (buffer == null) throw new IllegalArgumentException("buffer");
        buffer.writeByte(SCHEMA_VERSION); buffer.writeByte(entries.size());
        for (Entry entry : entries) {
            buffer.writeByte(entry.position.x); buffer.writeByte(entry.position.y); buffer.writeByte(entry.position.z);
            buffer.writeDouble(entry.state.position); buffer.writeDouble(entry.state.velocity);
        }
    }

    public static JointStateSet readClient(ByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 2 || buffer.readUnsignedByte() != SCHEMA_VERSION)
            throw new IllegalArgumentException("joint client schema");
        int count = buffer.readUnsignedByte();
        if (count > MAX_JOINTS || buffer.readableBytes() != count * 19)
            throw new IllegalArgumentException("joint client payload");
        List<Entry> values = new ArrayList<Entry>();
        for (int i = 0; i < count; i++) {
            GridVector position=new GridVector(buffer.readByte(), buffer.readByte(), buffer.readByte());
            JointState state=new JointState(buffer.readDouble(), buffer.readDouble());
            if(StrictMath.abs(state.position)>10000.0||StrictMath.abs(state.velocity)>10000.0)
                throw new IllegalArgumentException("joint client state bounds");
            values.add(new Entry(position,state));
        }
        return new JointStateSet(values);
    }

    public String encodeClientString() {
        ByteBuf buffer = Unpooled.buffer(2 + entries.size() * 19); writeClient(buffer);
        byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static JointStateSet decodeClientString(String encoded) {
        if (encoded == null || encoded.length() > 1024) throw new IllegalArgumentException("joint client string");
        try { return readClient(Unpooled.wrappedBuffer(Base64.getDecoder().decode(encoded))); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("joint client string", invalid); }
    }

    public static final class Entry {
        public final GridVector position;
        public final JointState state;
        public Entry(GridVector position, JointState state) {
            if (position == null || state == null || position.x < -16 || position.x > 16
                    || position.y < -16 || position.y > 16 || position.z < -16 || position.z > 16)
                throw new IllegalArgumentException("joint state entry");
            this.position = position; this.state = state;
        }
    }

    private static int compare(GridVector a, GridVector b) {
        if (a.x != b.x) return a.x < b.x ? -1 : 1;
        if (a.y != b.y) return a.y < b.y ? -1 : 1;
        return a.z == b.z ? 0 : a.z < b.z ? -1 : 1;
    }
}
