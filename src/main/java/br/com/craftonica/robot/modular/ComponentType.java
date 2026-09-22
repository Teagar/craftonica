package br.com.craftonica.robot.modular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable Forge-free description of one physical component block. */
public final class ComponentType {
    private final String id;
    private final int schemaVersion;
    private final BoxVolume volume;
    private final MassProperties mass;
    private final ComponentMaterial material;
    private final List<StructuralPort> structuralPorts;
    private final List<ElectricalPort> electricalPorts;
    private final List<MechanicalPort> mechanicalPorts;
    private final TransmissionProfile transmission;
    private final ActuatorProfile actuator;
    private final ContactProfile contact;
    private final SensorProfile sensor;

    private ComponentType(Builder builder) {
        id = ContractValues.id(builder.id, "component id");
        if (builder.schemaVersion <= 0 || builder.volume == null || builder.mass == null || builder.material == null)
            throw new IllegalArgumentException("component properties");
        schemaVersion = builder.schemaVersion;
        volume = builder.volume;
        mass = builder.mass;
        material = builder.material;
        structuralPorts = immutable(builder.structuralPorts);
        electricalPorts = immutable(builder.electricalPorts);
        mechanicalPorts = immutable(builder.mechanicalPorts);
        transmission = builder.transmission;
        actuator = builder.actuator;
        contact = builder.contact;
        sensor = builder.sensor;
        ensureUniquePortIds();
        ensureTransmissionContract();
    }

    public static Builder builder(String id, int schemaVersion) { return new Builder(id, schemaVersion); }
    public String getId() { return id; }
    public int getSchemaVersion() { return schemaVersion; }
    public BoxVolume getVolume() { return volume; }
    public MassProperties getMass() { return mass; }
    public ComponentMaterial getMaterial() { return material; }
    public List<StructuralPort> getStructuralPorts() { return structuralPorts; }
    public List<ElectricalPort> getElectricalPorts() { return electricalPorts; }
    public List<MechanicalPort> getMechanicalPorts() { return mechanicalPorts; }
    public TransmissionProfile getTransmission() { return transmission; }
    public ActuatorProfile getActuator() { return actuator; }
    public ContactProfile getContact() { return contact; }
    public SensorProfile getSensor() { return sensor; }

    private void ensureUniquePortIds() {
        Set<String> ids = new HashSet<String>();
        for (StructuralPort port : structuralPorts) if (!ids.add(port.id)) duplicate(port.id);
        for (ElectricalPort port : electricalPorts) if (!ids.add(port.id)) duplicate(port.id);
        for (MechanicalPort port : mechanicalPorts) if (!ids.add(port.id)) duplicate(port.id);
    }

    private void ensureTransmissionContract() {
        if (transmission == null) return;
        int shafts = 0, meshes = 0;
        for (MechanicalPort port : mechanicalPorts) {
            if (port.kind == MechanicalPort.Kind.ROTARY_SHAFT) shafts++;
            if (port.kind == MechanicalPort.Kind.GEAR_MESH) meshes++;
        }
        if (transmission.kind == TransmissionProfile.Kind.SPUR_GEAR) {
            if (shafts < 1 || meshes < 1) throw new IllegalArgumentException("gear transmission ports");
        } else if (shafts < 2 || meshes != 0) {
            throw new IllegalArgumentException("shaft transmission ports");
        }
    }

    private static void duplicate(String id) { throw new IllegalArgumentException("duplicate port: " + id); }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static final class Builder {
        private final String id;
        private final int schemaVersion;
        private BoxVolume volume;
        private MassProperties mass;
        private ComponentMaterial material;
        private final List<StructuralPort> structuralPorts = new ArrayList<StructuralPort>();
        private final List<ElectricalPort> electricalPorts = new ArrayList<ElectricalPort>();
        private final List<MechanicalPort> mechanicalPorts = new ArrayList<MechanicalPort>();
        private TransmissionProfile transmission;
        private ActuatorProfile actuator;
        private ContactProfile contact;
        private SensorProfile sensor;

        private Builder(String id, int schemaVersion) { this.id = id; this.schemaVersion = schemaVersion; }
        public Builder physical(BoxVolume volume, MassProperties mass, ComponentMaterial material) {
            this.volume = volume; this.mass = mass; this.material = material; return this;
        }
        public Builder structural(StructuralPort port) { if (port == null) throw new IllegalArgumentException("port"); structuralPorts.add(port); return this; }
        public Builder electrical(ElectricalPort port) { if (port == null) throw new IllegalArgumentException("port"); electricalPorts.add(port); return this; }
        public Builder mechanical(MechanicalPort port) { if (port == null) throw new IllegalArgumentException("port"); mechanicalPorts.add(port); return this; }
        public Builder transmission(TransmissionProfile value) { transmission = value; return this; }
        public Builder actuator(ActuatorProfile value) { actuator = value; return this; }
        public Builder contact(ContactProfile value) { contact = value; return this; }
        public Builder sensor(SensorProfile value) { sensor = value; return this; }
        public ComponentType build() { return new ComponentType(this); }
    }
}
