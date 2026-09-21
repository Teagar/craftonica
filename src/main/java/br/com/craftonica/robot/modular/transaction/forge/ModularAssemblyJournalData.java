package br.com.craftonica.robot.modular.transaction.forge;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularManifestNbtCodec;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent intent log. Entries are removed only after commit or complete rollback. */
public final class ModularAssemblyJournalData extends WorldSavedData {
    public static final String NAME = "craftonica_modular_assembly_journal_v1";
    private static final int SCHEMA_VERSION = 1;
    public enum Kind { ASSEMBLE, DISASSEMBLE }
    private final Map<UUID, Record> records = new LinkedHashMap<UUID, Record>();

    public ModularAssemblyJournalData() { super(NAME); }
    public ModularAssemblyJournalData(String name) { super(name); }

    public static ModularAssemblyJournalData get(World world) {
        ModularAssemblyJournalData data = (ModularAssemblyJournalData) world.perWorldStorage.loadData(
                ModularAssemblyJournalData.class, NAME);
        if (data == null) { data = new ModularAssemblyJournalData(); world.perWorldStorage.setData(NAME, data); }
        return data;
    }

    public void put(Record value) { if (value == null) throw new IllegalArgumentException("record"); records.put(value.transactionId, value); markDirty(); }
    public void remove(UUID transactionId) { if (records.remove(transactionId) != null) markDirty(); }
    public List<Record> all() { return new ArrayList<Record>(records.values()); }

    @Override public void readFromNBT(NBTTagCompound tag) {
        records.clear();
        if (tag == null || tag.getInteger("Schema") != SCHEMA_VERSION) return;
        try {
            NBTTagList list = tag.getTagList("Records", 10);
            if (list.tagCount() > 64) throw new IllegalArgumentException("journal count");
            for (int i = 0; i < list.tagCount(); i++) {
                Record value = Record.read(list.getCompoundTagAt(i));
                if (records.put(value.transactionId, value) != null) throw new IllegalArgumentException("duplicate transaction");
            }
        } catch (RuntimeException invalid) { records.clear(); }
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Schema", SCHEMA_VERSION);
        NBTTagList list = new NBTTagList(); for (Record value : records.values()) list.appendTag(value.write()); tag.setTag("Records", list);
    }

    public static final class Record {
        public final UUID transactionId, robotId, ownerId;
        public final Kind kind;
        public final GridVector anchor;
        public final ComponentOrientation orientation;
        public final ModularRobotManifest manifest;

        public Record(UUID transactionId, UUID robotId, UUID ownerId, Kind kind, GridVector anchor,
                      ComponentOrientation orientation, ModularRobotManifest manifest) {
            if (transactionId == null || robotId == null || ownerId == null || kind == null || anchor == null
                    || orientation == null || manifest == null) throw new IllegalArgumentException("journal record");
            this.transactionId = transactionId; this.robotId = robotId; this.ownerId = ownerId; this.kind = kind;
            this.anchor = anchor; this.orientation = orientation; this.manifest = manifest;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound(); uuid(tag, "Transaction", transactionId); uuid(tag, "Robot", robotId); uuid(tag, "Owner", ownerId);
            tag.setByte("Kind", (byte) kind.ordinal()); tag.setInteger("X", anchor.x); tag.setInteger("Y", anchor.y); tag.setInteger("Z", anchor.z);
            tag.setByte("Forward", (byte) orientation.getForward().ordinal()); tag.setByte("Up", (byte) orientation.getUp().ordinal());
            tag.setTag("Manifest", ModularManifestNbtCodec.write(manifest)); return tag;
        }

        static Record read(NBTTagCompound tag) {
            int kind = tag.getByte("Kind"), forward = tag.getByte("Forward"), up = tag.getByte("Up");
            if (kind < 0 || kind >= Kind.values().length || forward < 0 || forward >= Direction.values().length
                    || up < 0 || up >= Direction.values().length) throw new IllegalArgumentException("journal enum");
            return new Record(uuid(tag, "Transaction"), uuid(tag, "Robot"), uuid(tag, "Owner"), Kind.values()[kind],
                    new GridVector(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")),
                    new ComponentOrientation(Direction.values()[forward], Direction.values()[up]),
                    ModularManifestNbtCodec.read(tag.getCompoundTag("Manifest")));
        }

        private static void uuid(NBTTagCompound tag, String prefix, UUID value) { tag.setLong(prefix + "Most", value.getMostSignificantBits()); tag.setLong(prefix + "Least", value.getLeastSignificantBits()); }
        private static UUID uuid(NBTTagCompound tag, String prefix) {
            if (!tag.hasKey(prefix + "Most") || !tag.hasKey(prefix + "Least")) throw new IllegalArgumentException("journal uuid");
            return new UUID(tag.getLong(prefix + "Most"), tag.getLong(prefix + "Least"));
        }
    }
}
