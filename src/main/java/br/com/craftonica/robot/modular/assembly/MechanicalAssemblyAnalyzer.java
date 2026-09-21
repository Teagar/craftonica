package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.ContactProfile;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.MechanicalPort;
import br.com.craftonica.robot.modular.StandardComponentCatalog;

import java.util.*;

/** Bounded graph traversal from motor shafts to conventional wheel hubs. */
public final class MechanicalAssemblyAnalyzer {
    private MechanicalAssemblyAnalyzer() { }

    public static MechanicalAssembly analyze(AssemblyGraph graph) {
        if (graph == null) throw new IllegalArgumentException("graph");
        Map<GridVector, DiscoveredComponent> components = new HashMap<GridVector, DiscoveredComponent>();
        for (DiscoveredComponent component : graph.getComponents()) components.put(component.localPosition, component);
        Map<GridVector, List<Link>> links = new HashMap<GridVector, List<Link>>();
        for (AssemblyEdge edge : graph.getEdges()) if (edge.kind == AssemblyEdge.Kind.MECHANICAL) {
            add(links, edge.firstPosition, new Link(edge.secondPosition, edge.firstPort, edge.secondPort));
            add(links, edge.secondPosition, new Link(edge.firstPosition, edge.secondPort, edge.firstPort));
        }
        List<MechanicalAssembly.DrivePath> drives = new ArrayList<MechanicalAssembly.DrivePath>();
        Set<GridVector> drivenWheels = new HashSet<GridVector>();
        for (DiscoveredComponent component : graph.getComponents())
            if (StandardComponentCatalog.DC_MOTOR.equals(component.type.getId()))
                discover(component, components, links, drives, drivenWheels);
        List<MechanicalAssembly.GroundContact> contacts = new ArrayList<MechanicalAssembly.GroundContact>();
        for (DiscoveredComponent component : graph.getComponents()) {
            ContactProfile profile = component.type.getContact(); if (profile == null) continue;
            boolean driven = drivenWheels.contains(component.localPosition);
            contacts.add(new MechanicalAssembly.GroundContact(component.localPosition, profile.kind, driven,
                    parameter(profile, "radius_metres"), parameter(profile, "longitudinal_friction"),
                    parameter(profile, "lateral_friction"), parameter(profile, "rolling_friction")));
        }
        Comparator<MechanicalAssembly.DrivePath> driveOrder = new Comparator<MechanicalAssembly.DrivePath>() {
            @Override public int compare(MechanicalAssembly.DrivePath a, MechanicalAssembly.DrivePath b) {
                int value = a.motorPosition.toString().compareTo(b.motorPosition.toString());
                return value != 0 ? value : a.wheelPosition.toString().compareTo(b.wheelPosition.toString());
            }
        };
        Collections.sort(drives, driveOrder);
        Collections.sort(contacts, new Comparator<MechanicalAssembly.GroundContact>() {
            @Override public int compare(MechanicalAssembly.GroundContact a, MechanicalAssembly.GroundContact b) {
                return a.position.toString().compareTo(b.position.toString());
            }
        });
        return new MechanicalAssembly(drives, contacts);
    }

    private static void discover(DiscoveredComponent motor, Map<GridVector, DiscoveredComponent> components,
            Map<GridVector, List<Link>> links, List<MechanicalAssembly.DrivePath> drives, Set<GridVector> wheels) {
        Queue<Path> queue = new ArrayDeque<Path>(); Set<GridVector> visited = new HashSet<GridVector>();
        queue.add(new Path(motor.localPosition, Double.POSITIVE_INFINITY)); visited.add(motor.localPosition);
        while (!queue.isEmpty()) {
            Path path = queue.remove(); List<Link> next = links.get(path.position); if (next == null) continue;
            for (Link link : next) {
                if (!visited.add(link.other)) continue;
                DiscoveredComponent component = components.get(link.other); if (component == null) continue;
                double limit = StrictMath.min(path.limit, StrictMath.min(port(components.get(path.position), link.ownPort),
                        port(component, link.otherPort)));
                if (component.type.getContact() != null
                        && component.type.getContact().kind == ContactProfile.Kind.DRIVEN_WHEEL) {
                    ContactProfile contact = component.type.getContact(); MechanicalPort hub = mechanical(component, link.otherPort);
                    drives.add(new MechanicalAssembly.DrivePath(motor.localPosition, component.localPosition,
                            component.localOrientation.toWorld(hub.axis), parameter(contact, "radius_metres"),
                            parameter(contact, "width_metres"), limit)); wheels.add(component.localPosition);
                } else if (StandardComponentCatalog.AXLE.equals(component.type.getId())) queue.add(new Path(link.other, limit));
            }
        }
    }

    private static double port(DiscoveredComponent component, String id) { return mechanical(component, id).maximumTorqueNewtonMetres; }
    private static MechanicalPort mechanical(DiscoveredComponent component, String id) {
        for (MechanicalPort port : component.type.getMechanicalPorts()) if (port.id.equals(id)) return port;
        throw new IllegalArgumentException("unknown mechanical port " + id);
    }
    private static double parameter(ContactProfile profile, String key) {
        Double value = profile.getParameters().get(key); return value == null ? 0.0 : value.doubleValue();
    }
    private static void add(Map<GridVector, List<Link>> links, GridVector at, Link link) {
        List<Link> values = links.get(at); if (values == null) { values = new ArrayList<Link>(); links.put(at, values); } values.add(link);
    }
    private static final class Link { final GridVector other; final String ownPort, otherPort; Link(GridVector other, String own, String target) { this.other=other; ownPort=own; otherPort=target; } }
    private static final class Path { final GridVector position; final double limit; Path(GridVector p, double l) { position=p; limit=l; } }
}
