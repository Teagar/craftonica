package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.*;

/** Canonical bounded traversal of rotary shafts, bearings and spur-gear meshes. */
public strictfp final class MechanicalAssemblyAnalyzer {
    public static final int MAX_TRANSMISSION_EDGES = 64;
    public static final int MAX_GEAR_STAGES = 8;
    private MechanicalAssemblyAnalyzer() { }

    public static MechanicalAssembly analyze(ModularRobotManifest manifest, ComponentCatalog catalog) {
        if (manifest == null || catalog == null) throw new IllegalArgumentException("manifest or catalog");
        List<DiscoveredComponent> components = new ArrayList<DiscoveredComponent>();
        for (ModularBlockSnapshot module : manifest.getModules())
            components.add(new DiscoveredComponent(module.localPosition, module.localPosition,
                    module.localOrientation, catalog.require(module.componentTypeId)));
        return analyze(new AssemblyGraph(GridVector.ZERO, ComponentOrientation.NORTH_UP,
                components, manifest.getEdges()));
    }

    public static MechanicalAssembly analyze(AssemblyGraph graph) {
        if (graph == null) throw new IllegalArgumentException("graph");
        Map<GridVector, DiscoveredComponent> components = new HashMap<GridVector, DiscoveredComponent>();
        int trackModules = 0;
        for (DiscoveredComponent component : graph.getComponents()) {
            components.put(component.localPosition, component);
            if (StandardComponentCatalog.TRACK_MODULE.equals(component.type.getId())) trackModules++;
        }
        Map<Endpoint, List<Transition>> links = new HashMap<Endpoint, List<Transition>>();
        List<MechanicalAssembly.TransmissionDiagnostic> diagnostics =
                new ArrayList<MechanicalAssembly.TransmissionDiagnostic>();
        Set<Endpoint> externallyConnected = new HashSet<Endpoint>();
        Set<GridVector> mountedTracks = new HashSet<GridVector>();

        int mechanicalEdges = 0;
        for (AssemblyEdge edge : graph.getEdges()) {
            if (edge.kind == AssemblyEdge.Kind.STRUCTURAL) {
                markMountedTrack(edge.firstPosition, edge.firstPort, components, mountedTracks);
                markMountedTrack(edge.secondPosition, edge.secondPort, components, mountedTracks);
            }
            if (edge.kind != AssemblyEdge.Kind.MECHANICAL) continue;
            mechanicalEdges++;
            DiscoveredComponent first = components.get(edge.firstPosition);
            DiscoveredComponent second = components.get(edge.secondPosition);
            MechanicalPort a = port(first, edge.firstPort), b = port(second, edge.secondPort);
            if (a == null || b == null) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.UNKNOWN_PORT,
                        first == null ? edge.firstPosition : edge.secondPosition,
                        edge.firstPort + "=" + edge.secondPort);
                continue;
            }
            Endpoint one = new Endpoint(edge.firstPosition, edge.firstPort);
            Endpoint two = new Endpoint(edge.secondPosition, edge.secondPort);
            if (a.kind == MechanicalPort.Kind.GEAR_MESH || b.kind == MechanicalPort.Kind.GEAR_MESH) {
                TransmissionProfile ap = first.type.getTransmission(), bp = second.type.getTransmission();
                Direction aa = first.localOrientation.toWorld(a.axis), ba = second.localOrientation.toWorld(b.axis);
                if (a.kind != MechanicalPort.Kind.GEAR_MESH || b.kind != MechanicalPort.Kind.GEAR_MESH
                        || ap == null || !ap.meshesWith(bp) || (aa != ba && aa != ba.opposite())) {
                    diagnostic(diagnostics, MechanicalAssembly.Diagnostic.INCOMPATIBLE_GEAR_MESH,
                            edge.firstPosition, edge.firstPort + "=" + edge.secondPort);
                    continue;
                }
            } else if (!compatible(a, first, b, second)) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.INCOMPATIBLE_CONNECTION,
                        edge.firstPosition, edge.firstPort + "=" + edge.secondPort);
                continue;
            }
            connect(links, one, new Transition(two, true));
            connect(links, two, new Transition(one, true));
            externallyConnected.add(one); externallyConnected.add(two);
        }
        boolean overBudget = mechanicalEdges > MAX_TRANSMISSION_EDGES;
        if (overBudget)
            diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED,
                    GridVector.ZERO, "mechanical edges");
        boolean overTrackBudget = trackModules > TrackAssemblyAnalyzer.MAX_TRACK_MODULES;
        if (overTrackBudget)
            diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED,
                    GridVector.ZERO, "track modules");

        for (DiscoveredComponent component : graph.getComponents()) {
            TransmissionProfile profile = component.type.getTransmission();
            if (profile == null) continue;
            List<Endpoint> active = new ArrayList<Endpoint>();
            for (MechanicalPort port : component.type.getMechanicalPorts()) {
                Endpoint endpoint = new Endpoint(component.localPosition, port.id);
                if (externallyConnected.contains(endpoint)) active.add(endpoint);
            }
            Collections.sort(active);
            if (active.size() > 2) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_BRANCH_UNSUPPORTED,
                        component.localPosition, component.type.getId());
            } else if (active.size() == 2) {
                connect(links, active.get(0), new Transition(active.get(1), false));
                connect(links, active.get(1), new Transition(active.get(0), false));
            }
        }

        List<Candidate> candidates = new ArrayList<Candidate>();
        if (!overBudget) for (DiscoveredComponent component : graph.getComponents())
            if (StandardComponentCatalog.DC_MOTOR.equals(component.type.getId()))
                discover(component, components, links, mountedTracks, diagnostics, candidates);

        Map<GridVector, List<Candidate>> byWheel = new HashMap<GridVector, List<Candidate>>();
        for (Candidate candidate : candidates) {
            DiscoveredComponent output = components.get(candidate.path.wheelPosition);
            if (overTrackBudget && output != null
                    && StandardComponentCatalog.TRACK_MODULE.equals(output.type.getId())) continue;
            List<Candidate> values = byWheel.get(candidate.path.wheelPosition);
            if (values == null) { values = new ArrayList<Candidate>(); byWheel.put(candidate.path.wheelPosition, values); }
            values.add(candidate);
        }
        List<MechanicalAssembly.DrivePath> drives = new ArrayList<MechanicalAssembly.DrivePath>();
        Set<GridVector> drivenWheels = new HashSet<GridVector>();
        for (Map.Entry<GridVector, List<Candidate>> entry : byWheel.entrySet()) {
            if (entry.getValue().size() != 1) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.MULTIPLE_INPUTS,
                        entry.getKey(), "motors=" + entry.getValue().size());
            } else {
                drives.add(entry.getValue().get(0).path); drivenWheels.add(entry.getKey());
            }
        }

        List<MechanicalAssembly.GroundContact> contacts = contacts(graph, drivenWheels);
        Collections.sort(drives, DRIVE_ORDER);
        Collections.sort(contacts, CONTACT_ORDER);
        Collections.sort(diagnostics, DIAGNOSTIC_ORDER);
        return new MechanicalAssembly(drives, contacts, diagnostics);
    }

    private static void discover(DiscoveredComponent motor, Map<GridVector, DiscoveredComponent> components,
            Map<Endpoint, List<Transition>> links, Set<GridVector> mountedTracks,
            List<MechanicalAssembly.TransmissionDiagnostic> diagnostics,
            List<Candidate> candidates) {
        MechanicalPort shaft = null;
        for (MechanicalPort port : motor.type.getMechanicalPorts())
            if (port.kind == MechanicalPort.Kind.ROTARY_SHAFT) { shaft = port; break; }
        if (shaft == null) { diagnostic(diagnostics, MechanicalAssembly.Diagnostic.OPEN_PATH,
                motor.localPosition, "motor without shaft"); return; }
        Endpoint current = new Endpoint(motor.localPosition, shaft.id), previous = null;
        Set<Endpoint> visited = new HashSet<Endpoint>();
        double ratio = 1.0, efficiency = 1.0, reflectedInertia = 0.0;
        double maximumInputTorque = shaft.maximumTorqueNewtonMetres;
        int direction = 1, stages = 0, transitions = 0;

        while (true) {
            if (!visited.add(current)) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LOOP_UNSUPPORTED,
                        current.position, current.port); return;
            }
            DiscoveredComponent component = components.get(current.position);
            MechanicalPort currentPort = port(component, current.port);
            if (component == null || currentPort == null) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.UNKNOWN_PORT, current.position, current.port); return;
            }
            maximumInputTorque = StrictMath.min(maximumInputTorque,
                    currentPort.maximumTorqueNewtonMetres / (ratio * efficiency));
            ContactProfile contact = component.type.getContact();
            if (contact != null && (contact.kind == ContactProfile.Kind.DRIVEN_WHEEL
                    || contact.kind == ContactProfile.Kind.DRIVEN_TRACK
                    || contact.kind == ContactProfile.Kind.OMNI_WHEEL
                    || contact.kind == ContactProfile.Kind.MECANUM_WHEEL)
                    && currentPort.kind == MechanicalPort.Kind.WHEEL_HUB) {
                if (contact.kind == ContactProfile.Kind.DRIVEN_TRACK
                        && !mountedTracks.contains(component.localPosition)) {
                    diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRACK_MOUNT_OPEN,
                            component.localPosition, "mount_up"); return;
                }
                double radius = parameter(contact, "radius_metres"), width = parameter(contact, "width_metres");
                double angle = StrictMath.toRadians(parameter(contact, "traction_angle_degrees"));
                double tractionLever = radius * StrictMath.abs(StrictMath.cos(angle));
                double wheelInertia = 0.5 * component.type.getMass().massKg * radius * radius;
                reflectedInertia += wheelInertia / (ratio * ratio);
                double maximumOutputTorque = maximumInputTorque * ratio * efficiency;
                candidates.add(new Candidate(new MechanicalAssembly.DrivePath(motor.localPosition,
                        component.localPosition, component.localOrientation.toWorld(currentPort.axis), radius,
                        tractionLever, width, maximumOutputTorque, ratio, direction, efficiency,
                        reflectedInertia, stages)));
                return;
            }

            List<Transition> all = links.get(current);
            List<Transition> next = new ArrayList<Transition>();
            if (all != null) for (Transition transition : all)
                if (previous == null || !transition.target.equals(previous)) next.add(transition);
            if (next.isEmpty()) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.OPEN_PATH, motor.localPosition, current.toString()); return;
            }
            if (next.size() != 1) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_BRANCH_UNSUPPORTED,
                        current.position, current.port); return;
            }
            Transition transition = next.get(0);
            if (++transitions > MAX_TRANSMISSION_EDGES * 2) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED,
                        current.position, "path length"); return;
            }
            DiscoveredComponent targetComponent = components.get(transition.target.position);
            MechanicalPort targetPort = port(targetComponent, transition.target.port);
            if (transition.external && currentPort.kind == MechanicalPort.Kind.GEAR_MESH) {
                TransmissionProfile from = component.type.getTransmission();
                TransmissionProfile to = targetComponent == null ? null : targetComponent.type.getTransmission();
                if (from == null || !from.meshesWith(to)) {
                    diagnostic(diagnostics, MechanicalAssembly.Diagnostic.INCOMPATIBLE_GEAR_MESH,
                            current.position, current.port); return;
                }
                ratio *= (double) to.toothCount / (double) from.toothCount;
                efficiency *= StrictMath.min(from.efficiency, to.efficiency);
                direction = -direction; stages++;
                if (stages > MAX_GEAR_STAGES || ratio < 1.0 / 256.0 || ratio > 256.0) {
                    diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED,
                            current.position, "gear ratio or stages"); return;
                }
            } else if (!transition.external) {
                TransmissionProfile profile = component.type.getTransmission();
                if (profile != null) {
                    reflectedInertia += profile.rotationalInertiaKgM2 / (ratio * ratio);
                    if (profile.kind != TransmissionProfile.Kind.SPUR_GEAR) efficiency *= profile.efficiency;
                }
            }
            if (!finite(ratio) || !finite(efficiency) || efficiency <= 0.0) {
                diagnostic(diagnostics, MechanicalAssembly.Diagnostic.TRANSMISSION_LIMIT_EXCEEDED,
                        current.position, "non-finite transmission"); return;
            }
            previous = current; current = transition.target;
        }
    }

    private static List<MechanicalAssembly.GroundContact> contacts(AssemblyGraph graph, Set<GridVector> drivenWheels) {
        List<MechanicalAssembly.GroundContact> values = new ArrayList<MechanicalAssembly.GroundContact>();
        for (DiscoveredComponent component : graph.getComponents()) {
            ContactProfile profile = component.type.getContact(); if (profile == null) continue;
            values.add(new MechanicalAssembly.GroundContact(component.localPosition, profile.kind,
                    drivenWheels.contains(component.localPosition), parameter(profile, "radius_metres"),
                    parameter(profile, "longitudinal_friction"), parameter(profile, "lateral_friction"),
                    parameter(profile, "rolling_friction")));
        }
        return values;
    }

    private static MechanicalPort port(DiscoveredComponent component, String id) {
        if (component == null || id == null) return null;
        for (MechanicalPort port : component.type.getMechanicalPorts()) if (port.id.equals(id)) return port;
        return null;
    }
    private static void markMountedTrack(GridVector position, String port,
            Map<GridVector, DiscoveredComponent> components, Set<GridVector> mountedTracks) {
        DiscoveredComponent component = components.get(position);
        if (component != null && StandardComponentCatalog.TRACK_MODULE.equals(component.type.getId())
                && "mount_up".equals(port)) mountedTracks.add(position);
    }
    private static boolean compatible(MechanicalPort a, DiscoveredComponent ac,
            MechanicalPort b, DiscoveredComponent bc) {
        boolean coupling = a.coupling == MechanicalPort.Coupling.NEUTRAL
                || b.coupling == MechanicalPort.Coupling.NEUTRAL
                || (a.coupling == MechanicalPort.Coupling.PLUG && b.coupling == MechanicalPort.Coupling.SOCKET)
                || (a.coupling == MechanicalPort.Coupling.SOCKET && b.coupling == MechanicalPort.Coupling.PLUG);
        boolean kind = a.kind == b.kind
                || (a.kind == MechanicalPort.Kind.ROTARY_SHAFT && b.kind == MechanicalPort.Kind.WHEEL_HUB)
                || (a.kind == MechanicalPort.Kind.WHEEL_HUB && b.kind == MechanicalPort.Kind.ROTARY_SHAFT);
        Direction aa = ac.localOrientation.toWorld(a.axis), ba = bc.localOrientation.toWorld(b.axis);
        return coupling && kind && (aa == ba || aa == ba.opposite());
    }
    private static double parameter(ContactProfile profile, String key) {
        Double value = profile.getParameters().get(key); return value == null ? 0.0 : value.doubleValue();
    }
    private static void connect(Map<Endpoint, List<Transition>> links, Endpoint at, Transition link) {
        List<Transition> values = links.get(at);
        if (values == null) { values = new ArrayList<Transition>(); links.put(at, values); }
        values.add(link);
    }
    private static void diagnostic(List<MechanicalAssembly.TransmissionDiagnostic> values,
            MechanicalAssembly.Diagnostic code, GridVector position, String detail) {
        values.add(new MechanicalAssembly.TransmissionDiagnostic(code, position, detail));
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    private static final class Endpoint implements Comparable<Endpoint> {
        final GridVector position; final String port;
        Endpoint(GridVector position, String port) { this.position = position; this.port = port; }
        @Override public int compareTo(Endpoint other) { return toString().compareTo(other.toString()); }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Endpoint)) return false; Endpoint other = (Endpoint) value;
            return position.equals(other.position) && port.equals(other.port);
        }
        @Override public int hashCode() { return 31 * position.hashCode() + port.hashCode(); }
        @Override public String toString() { return position + "/" + port; }
    }
    private static final class Transition {
        final Endpoint target; final boolean external;
        Transition(Endpoint target, boolean external) { this.target = target; this.external = external; }
    }
    private static final class Candidate {
        final MechanicalAssembly.DrivePath path;
        Candidate(MechanicalAssembly.DrivePath path) { this.path = path; }
    }

    private static final Comparator<MechanicalAssembly.DrivePath> DRIVE_ORDER =
            new Comparator<MechanicalAssembly.DrivePath>() {
        @Override public int compare(MechanicalAssembly.DrivePath a, MechanicalAssembly.DrivePath b) {
            int value = a.motorPosition.toString().compareTo(b.motorPosition.toString());
            return value != 0 ? value : a.wheelPosition.toString().compareTo(b.wheelPosition.toString());
        }
    };
    private static final Comparator<MechanicalAssembly.GroundContact> CONTACT_ORDER =
            new Comparator<MechanicalAssembly.GroundContact>() {
        @Override public int compare(MechanicalAssembly.GroundContact a, MechanicalAssembly.GroundContact b) {
            return a.position.toString().compareTo(b.position.toString());
        }
    };
    private static final Comparator<MechanicalAssembly.TransmissionDiagnostic> DIAGNOSTIC_ORDER =
            new Comparator<MechanicalAssembly.TransmissionDiagnostic>() {
        @Override public int compare(MechanicalAssembly.TransmissionDiagnostic a,
                                     MechanicalAssembly.TransmissionDiagnostic b) {
            int value = a.position.toString().compareTo(b.position.toString());
            if (value != 0) return value; value = a.code.name().compareTo(b.code.name());
            return value != 0 ? value : a.detail.compareTo(b.detail);
        }
    };
}
