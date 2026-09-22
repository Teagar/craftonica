package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.BoxVolume;
import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.ComponentType;
import br.com.craftonica.robot.modular.ContactProfile;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.MassProperties;
import br.com.craftonica.robot.modular.MechanicalPort;
import br.com.craftonica.robot.modular.Vector3;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical mass, contact and compound-volume properties derived from a manifest. */
public strictfp final class RigidBodyProperties {
    public static final int MAX_COLLISION_VOLUMES = 128;
    public static final int MAX_CONTACTS = 24;

    public final double massKg;
    public final Vector3 centerOfMassMetres;
    public final Vector3 principalInertiaKgMetresSquared;
    private final List<AxisAlignedVolume> collisionVolumes;
    private final List<Contact> contacts;

    private RigidBodyProperties(double massKg, Vector3 center, Vector3 inertia,
            List<AxisAlignedVolume> volumes, List<Contact> contacts) {
        this.massKg = massKg;
        this.centerOfMassMetres = center;
        this.principalInertiaKgMetresSquared = inertia;
        this.collisionVolumes = Collections.unmodifiableList(volumes);
        this.contacts = Collections.unmodifiableList(contacts);
    }

    public List<AxisAlignedVolume> getCollisionVolumes() { return collisionVolumes; }
    public List<Contact> getContacts() { return contacts; }

    public static RigidBodyProperties derive(ModularRobotManifest manifest, ComponentCatalog catalog) {
        if (manifest == null || catalog == null) throw new IllegalArgumentException("manifest or catalog");
        return derive(manifest.getModules(), catalog);
    }

    public static RigidBodyProperties derive(List<ModularBlockSnapshot> modules, ComponentCatalog catalog) {
        if (modules == null || modules.isEmpty() || catalog == null) throw new IllegalArgumentException("modules or catalog");
        List<Contribution> contributions = new ArrayList<Contribution>();
        List<AxisAlignedVolume> volumes = new ArrayList<AxisAlignedVolume>();
        List<Contact> contacts = new ArrayList<Contact>();
        double totalMass = 0.0, weightedX = 0.0, weightedY = 0.0, weightedZ = 0.0;
        for (ModularBlockSnapshot module : modules) {
            ComponentType type = catalog.require(module.componentTypeId);
            if (type.getSchemaVersion() != module.componentSchema)
                throw new IllegalArgumentException("component schema: " + module.componentTypeId);
            MassProperties mass = type.getMass();
            Vector3 center = point(module.localPosition, module.localOrientation, mass.centerMetres);
            Vector3 componentInertia = rotatePrincipal(mass.principalInertiaKgMetresSquared,
                    module.localOrientation);
            contributions.add(new Contribution(mass.massKg, center, componentInertia));
            totalMass += mass.massKg;
            weightedX += mass.massKg * center.x;
            weightedY += mass.massKg * center.y;
            weightedZ += mass.massKg * center.z;
            volumes.add(volume(module.localPosition, module.localOrientation, type.getVolume()));
            if (type.getContact() != null) contacts.add(contact(module, type));
        }
        mergeAdjacent(volumes);
        if (volumes.size() > MAX_COLLISION_VOLUMES)
            throw new IllegalArgumentException("maximum collision volumes");
        if (contacts.size() > MAX_CONTACTS) throw new IllegalArgumentException("maximum contacts");
        Vector3 center = new Vector3(weightedX / totalMass, weightedY / totalMass, weightedZ / totalMass);
        double ix = 0.0, iy = 0.0, iz = 0.0;
        for (Contribution value : contributions) {
            double dx = value.center.x - center.x, dy = value.center.y - center.y,
                    dz = value.center.z - center.z;
            ix += value.inertia.x + value.mass * (dy * dy + dz * dz);
            iy += value.inertia.y + value.mass * (dx * dx + dz * dz);
            iz += value.inertia.z + value.mass * (dx * dx + dy * dy);
        }
        return new RigidBodyProperties(totalMass, center, new Vector3(ix, iy, iz),
                new ArrayList<AxisAlignedVolume>(volumes), new ArrayList<Contact>(contacts));
    }

    private static Contact contact(ModularBlockSnapshot module, ComponentType type) {
        ContactProfile profile = type.getContact();
        double radius = parameter(profile, "radius_metres");
        Vector3 center = point(module.localPosition, module.localOrientation,
                new Vector3(0.5, 0.5, 0.5));
        AxisAlignedVolume physicalVolume = volume(module.localPosition, module.localOrientation,
                type.getVolume());
        Vector3 point = new Vector3(center.x, physicalVolume.minimum.y, center.z);
        Vector3 rolling = new Vector3(0.0, 0.0, 0.0);
        if (profile.kind == ContactProfile.Kind.DRIVEN_WHEEL || profile.kind == ContactProfile.Kind.DRIVEN_TRACK) {
            MechanicalPort hub = null;
            for (MechanicalPort port : type.getMechanicalPorts())
                if (port.kind == MechanicalPort.Kind.WHEEL_HUB) { hub = port; break; }
            if (hub == null) throw new IllegalArgumentException("wheel without hub");
            Direction axle = module.localOrientation.toWorld(hub.axis);
            rolling = horizontalPerpendicular(axle);
        }
        return new Contact(module.localPosition, profile.kind, point, rolling, radius,
                parameter(profile, "longitudinal_friction"), parameter(profile, "lateral_friction"),
                parameter(profile, "rolling_friction"), parameter(profile, "contact_length_metres"),
                parameter(profile, "width_metres"));
    }

    private static Vector3 horizontalPerpendicular(Direction axle) {
        double x = axle.vector.x, z = axle.vector.z;
        double length = StrictMath.sqrt(x * x + z * z);
        if (length == 0.0) throw new IllegalArgumentException("vertical wheel axle");
        return new Vector3(z / length, 0.0, -x / length);
    }

    private static double parameter(ContactProfile profile, String name) {
        Double value = profile.getParameters().get(name);
        return value == null ? 0.0 : value.doubleValue();
    }

    private static Vector3 point(GridVector position, ComponentOrientation orientation, Vector3 local) {
        Vector3 centered = new Vector3(local.x - 0.5, local.y - 0.5, local.z - 0.5);
        Vector3 rotated = rotate(centered, orientation);
        return new Vector3(position.x + 0.5 + rotated.x, position.y + 0.5 + rotated.y,
                position.z + 0.5 + rotated.z);
    }

    private static AxisAlignedVolume volume(GridVector position, ComponentOrientation orientation, BoxVolume box) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            Vector3 corner = point(position, orientation, new Vector3(
                    x == 0 ? box.minimum.x : box.maximum.x,
                    y == 0 ? box.minimum.y : box.maximum.y,
                    z == 0 ? box.minimum.z : box.maximum.z));
            minX = StrictMath.min(minX, corner.x); minY = StrictMath.min(minY, corner.y);
            minZ = StrictMath.min(minZ, corner.z); maxX = StrictMath.max(maxX, corner.x);
            maxY = StrictMath.max(maxY, corner.y); maxZ = StrictMath.max(maxZ, corner.z);
        }
        return new AxisAlignedVolume(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Greedy canonical fusion only joins exact face-sharing boxes; it never fills empty space. */
    private static void mergeAdjacent(List<AxisAlignedVolume> values) {
        boolean changed = true;
        while (changed) {
            changed = false;
            outer: for (int first = 0; first < values.size(); first++) {
                for (int second = first + 1; second < values.size(); second++) {
                    AxisAlignedVolume merged = merge(values.get(first), values.get(second));
                    if (merged == null) continue;
                    values.set(first, merged); values.remove(second); changed = true; break outer;
                }
            }
        }
    }

    private static AxisAlignedVolume merge(AxisAlignedVolume a, AxisAlignedVolume b) {
        if (same(a.minimum.y, b.minimum.y) && same(a.maximum.y, b.maximum.y)
                && same(a.minimum.z, b.minimum.z) && same(a.maximum.z, b.maximum.z)
                && touches(a.minimum.x, a.maximum.x, b.minimum.x, b.maximum.x))
            return new AxisAlignedVolume(StrictMath.min(a.minimum.x, b.minimum.x), a.minimum.y, a.minimum.z,
                    StrictMath.max(a.maximum.x, b.maximum.x), a.maximum.y, a.maximum.z);
        if (same(a.minimum.x, b.minimum.x) && same(a.maximum.x, b.maximum.x)
                && same(a.minimum.z, b.minimum.z) && same(a.maximum.z, b.maximum.z)
                && touches(a.minimum.y, a.maximum.y, b.minimum.y, b.maximum.y))
            return new AxisAlignedVolume(a.minimum.x, StrictMath.min(a.minimum.y, b.minimum.y), a.minimum.z,
                    a.maximum.x, StrictMath.max(a.maximum.y, b.maximum.y), a.maximum.z);
        if (same(a.minimum.x, b.minimum.x) && same(a.maximum.x, b.maximum.x)
                && same(a.minimum.y, b.minimum.y) && same(a.maximum.y, b.maximum.y)
                && touches(a.minimum.z, a.maximum.z, b.minimum.z, b.maximum.z))
            return new AxisAlignedVolume(a.minimum.x, a.minimum.y, StrictMath.min(a.minimum.z, b.minimum.z),
                    a.maximum.x, a.maximum.y, StrictMath.max(a.maximum.z, b.maximum.z));
        return null;
    }

    private static boolean same(double a, double b) { return Double.compare(a, b) == 0; }
    private static boolean touches(double minA, double maxA, double minB, double maxB) {
        return same(maxA, minB) || same(maxB, minA);
    }

    private static Vector3 rotate(Vector3 value, ComponentOrientation orientation) {
        GridVector right = orientation.getRight().vector, up = orientation.getUp().vector;
        GridVector positiveZ = orientation.getForward().opposite().vector;
        return new Vector3(right.x * value.x + up.x * value.y + positiveZ.x * value.z,
                right.y * value.x + up.y * value.y + positiveZ.y * value.z,
                right.z * value.x + up.z * value.y + positiveZ.z * value.z);
    }

    private static Vector3 rotatePrincipal(Vector3 inertia, ComponentOrientation orientation) {
        double[] result = new double[3];
        addAxis(result, orientation.getRight(), inertia.x);
        addAxis(result, orientation.getUp(), inertia.y);
        addAxis(result, orientation.getForward(), inertia.z);
        return new Vector3(result[0], result[1], result[2]);
    }

    private static void addAxis(double[] values, Direction direction, double value) {
        if (direction.vector.x != 0) values[0] += value;
        else if (direction.vector.y != 0) values[1] += value;
        else values[2] += value;
    }

    private static final class Contribution {
        final double mass; final Vector3 center; final Vector3 inertia;
        Contribution(double mass, Vector3 center, Vector3 inertia) {
            this.mass = mass; this.center = center; this.inertia = inertia;
        }
    }

    public static final class Contact {
        public final GridVector modulePosition;
        public final ContactProfile.Kind kind;
        public final Vector3 pointMetres;
        public final Vector3 rollingDirection;
        public final double radiusMetres;
        public final double longitudinalFriction;
        public final double lateralFriction;
        public final double rollingFriction;
        public final double contactLengthMetres, contactWidthMetres;

        Contact(GridVector modulePosition, ContactProfile.Kind kind, Vector3 point, Vector3 rolling,
                double radius, double longitudinal, double lateral, double rollingFriction,
                double contactLength, double contactWidth) {
            this.modulePosition = modulePosition; this.kind = kind; this.pointMetres = point;
            this.rollingDirection = rolling; this.radiusMetres = radius;
            this.longitudinalFriction = longitudinal; this.lateralFriction = lateral;
            this.rollingFriction = rollingFriction;
            this.contactLengthMetres = contactLength; this.contactWidthMetres = contactWidth;
        }
    }
}
