package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.*;

/** Derives rigid bodies and a bounded rooted joint tree without consulting a World. */
public final class KinematicAssemblyAnalyzer {
    public static final int MAX_BODIES = 32;
    public static final int MAX_JOINTS = 31;
    private KinematicAssemblyAnalyzer() { }

    public static KinematicAssembly analyze(ModularRobotManifest manifest, ComponentCatalog catalog) {
        if (manifest == null || catalog == null) throw new IllegalArgumentException("manifest or catalog");
        List<DiscoveredComponent> components = new ArrayList<DiscoveredComponent>();
        for (ModularBlockSnapshot module : manifest.getModules())
            components.add(new DiscoveredComponent(module.localPosition, module.localPosition,
                    module.localOrientation, catalog.require(module.componentTypeId)));
        return analyze(new AssemblyGraph(GridVector.ZERO, ComponentOrientation.NORTH_UP,
                components, manifest.getEdges()));
    }

    public static KinematicAssembly analyze(AssemblyGraph graph) {
        if (graph == null) throw new IllegalArgumentException("graph");
        Map<GridVector, DiscoveredComponent> components = new HashMap<GridVector, DiscoveredComponent>();
        Set<GridVector> jointModules = new HashSet<GridVector>();
        Union union = new Union();
        for (DiscoveredComponent component : graph.getComponents()) {
            components.put(component.localPosition, component);
            if (component.type.getJoint() != null) jointModules.add(component.localPosition);
            else union.add(component.localPosition);
        }
        // Legacy modular_robot:1 assemblies remain one rigid body until a physical joint opts in.
        if (jointModules.isEmpty()) {
            List<GridVector> positions = sorted(components.keySet());
            return new KinematicAssembly(Collections.singletonList(new KinematicAssembly.Body(0, positions)),
                    Collections.<KinematicAssembly.Joint>emptyList(),
                    Collections.<KinematicAssembly.Problem>emptyList());
        }
        for (AssemblyEdge edge : graph.getEdges()) if (edge.kind == AssemblyEdge.Kind.STRUCTURAL
                && !jointModules.contains(edge.firstPosition) && !jointModules.contains(edge.secondPosition)
                && components.containsKey(edge.firstPosition) && components.containsKey(edge.secondPosition))
            union.join(edge.firstPosition, edge.secondPosition);

        GridVector anchorRoot = union.contains(GridVector.ZERO) ? union.root(GridVector.ZERO) : null;
        List<GridVector> roots = new ArrayList<GridVector>(union.roots());
        Collections.sort(roots, POSITION_ORDER);
        if (anchorRoot != null) { roots.remove(anchorRoot); roots.add(0, anchorRoot); }
        Map<GridVector, Integer> bodyByRoot = new HashMap<GridVector, Integer>();
        for (int i = 0; i < roots.size(); i++) bodyByRoot.put(roots.get(i), Integer.valueOf(i));
        Map<GridVector, Integer> bodyByModule = new HashMap<GridVector, Integer>();
        List<List<GridVector>> bodyModules = new ArrayList<List<GridVector>>();
        for (int i = 0; i < roots.size(); i++) bodyModules.add(new ArrayList<GridVector>());
        for (GridVector position : union.positions()) {
            int id = bodyByRoot.get(union.root(position)).intValue();
            bodyByModule.put(position, Integer.valueOf(id)); bodyModules.get(id).add(position);
        }

        List<KinematicAssembly.Problem> problems = new ArrayList<KinematicAssembly.Problem>();
        List<KinematicAssembly.Joint> joints = new ArrayList<KinematicAssembly.Joint>();
        for (GridVector jointPosition : sorted(jointModules)) {
            DiscoveredComponent jointComponent = components.get(jointPosition);
            Attachment parent = null, child = null; int parentCount = 0, childCount = 0;
            GridVector driveConnection = null; int driveCount = 0;
            for (AssemblyEdge edge : graph.getEdges()) {
                if (edge.kind == AssemblyEdge.Kind.JOINT) {
                    boolean first = jointPosition.equals(edge.firstPosition), second = jointPosition.equals(edge.secondPosition);
                    if (!first && !second) continue;
                    String jointPortId = first ? edge.firstPort : edge.secondPort;
                    GridVector bodyPosition = first ? edge.secondPosition : edge.firstPosition;
                    JointPort port = jointPort(jointComponent, jointPortId);
                    Integer body = bodyByModule.get(bodyPosition);
                    if (port == null || body == null) continue;
                    Attachment value = new Attachment(body.intValue(), bodyPosition);
                    if (port.role == JointPort.Role.PARENT) { parent = value; parentCount++; }
                    else { child = value; childCount++; }
                } else if (edge.kind == AssemblyEdge.Kind.MECHANICAL) {
                    if (jointPosition.equals(edge.firstPosition)) { driveConnection = edge.secondPosition; driveCount++; }
                    if (jointPosition.equals(edge.secondPosition)) { driveConnection = edge.firstPosition; driveCount++; }
                }
            }
            if (parentCount == 0 || childCount == 0) {
                problem(problems, KinematicAssembly.Diagnostic.JOINT_CONNECTION_MISSING, jointPosition, "parent/child");
                continue;
            }
            if (parentCount != 1 || childCount != 1 || driveCount > 1) {
                problem(problems, KinematicAssembly.Diagnostic.JOINT_CONNECTION_AMBIGUOUS, jointPosition,
                        "parent=" + parentCount + ",child=" + childCount + ",drive=" + driveCount);
                continue;
            }
            if (parent.bodyId == child.bodyId) {
                problem(problems, KinematicAssembly.Diagnostic.JOINT_RIGIDLY_BYPASSED, jointPosition, "body=" + parent.bodyId);
                continue;
            }
            bodyModules.get(parent.bodyId).add(jointPosition);
            JointProfile profile = jointComponent.type.getJoint();
            joints.add(new KinematicAssembly.Joint(jointPosition, parent.bodyId, child.bodyId, profile,
                    jointComponent.localOrientation.toWorld(profile.axis), driveConnection));
        }

        validateTree(bodyModules.size(), joints, problems);
        if (bodyModules.size() > MAX_BODIES || joints.size() > MAX_JOINTS)
            problem(problems, KinematicAssembly.Diagnostic.LIMIT_EXCEEDED, GridVector.ZERO,
                    "bodies=" + bodyModules.size() + ",joints=" + joints.size());
        List<KinematicAssembly.Body> bodies = new ArrayList<KinematicAssembly.Body>();
        for (int i = 0; i < bodyModules.size(); i++) {
            Collections.sort(bodyModules.get(i), POSITION_ORDER);
            bodies.add(new KinematicAssembly.Body(i, bodyModules.get(i)));
        }
        Collections.sort(joints, new Comparator<KinematicAssembly.Joint>() {
            @Override public int compare(KinematicAssembly.Joint a, KinematicAssembly.Joint b) {
                return POSITION_ORDER.compare(a.modulePosition, b.modulePosition);
            }
        });
        Collections.sort(problems, new Comparator<KinematicAssembly.Problem>() {
            @Override public int compare(KinematicAssembly.Problem a, KinematicAssembly.Problem b) {
                int p = POSITION_ORDER.compare(a.position, b.position);
                return p != 0 ? p : a.code.compareTo(b.code);
            }
        });
        return new KinematicAssembly(bodies, joints, problems);
    }

    private static void validateTree(int bodyCount, List<KinematicAssembly.Joint> joints,
            List<KinematicAssembly.Problem> problems) {
        int[] parent = new int[bodyCount]; Arrays.fill(parent, -1);
        for (KinematicAssembly.Joint joint : joints) {
            if (parent[joint.childBodyId] != -1) problem(problems,
                    KinematicAssembly.Diagnostic.MULTIPLE_JOINT_PARENTS, joint.modulePosition,
                    "body=" + joint.childBodyId);
            else parent[joint.childBodyId] = joint.parentBodyId;
        }
        if (bodyCount > 0 && parent[0] != -1)
            problem(problems, KinematicAssembly.Diagnostic.KINEMATIC_LOOP_UNSUPPORTED,
                    GridVector.ZERO, "root has parent");
        for (int body = 1; body < bodyCount; body++) {
            Set<Integer> seen = new HashSet<Integer>(); int cursor = body;
            while (cursor > 0 && cursor < bodyCount && parent[cursor] != -1 && seen.add(Integer.valueOf(cursor)))
                cursor = parent[cursor];
            if (cursor == 0) continue;
            if (!seen.add(Integer.valueOf(cursor))) problem(problems,
                    KinematicAssembly.Diagnostic.KINEMATIC_LOOP_UNSUPPORTED, GridVector.ZERO, "body=" + body);
            else problem(problems, KinematicAssembly.Diagnostic.ORPHAN_BODY, GridVector.ZERO, "body=" + body);
        }
    }

    private static JointPort jointPort(DiscoveredComponent component, String id) {
        if (component == null) return null;
        for (JointPort port : component.type.getJointPorts()) if (port.id.equals(id)) return port;
        return null;
    }
    private static void problem(List<KinematicAssembly.Problem> values, KinematicAssembly.Diagnostic code,
            GridVector position, String detail) { values.add(new KinematicAssembly.Problem(code, position, detail)); }
    private static List<GridVector> sorted(Collection<GridVector> values) {
        List<GridVector> result = new ArrayList<GridVector>(values); Collections.sort(result, POSITION_ORDER); return result;
    }
    private static final class Attachment {
        final int bodyId; final GridVector position;
        Attachment(int bodyId, GridVector position) { this.bodyId = bodyId; this.position = position; }
    }
    private static final Comparator<GridVector> POSITION_ORDER = new Comparator<GridVector>() {
        @Override public int compare(GridVector a, GridVector b) {
            if (a.x != b.x) return a.x < b.x ? -1 : 1;
            if (a.y != b.y) return a.y < b.y ? -1 : 1;
            return a.z == b.z ? 0 : a.z < b.z ? -1 : 1;
        }
    };
    private static final class Union {
        private final Map<GridVector, GridVector> parent = new HashMap<GridVector, GridVector>();
        void add(GridVector value) { parent.put(value, value); }
        boolean contains(GridVector value) { return parent.containsKey(value); }
        GridVector root(GridVector value) {
            GridVector p = parent.get(value); if (p == null) throw new IllegalArgumentException("unknown union value");
            while (!p.equals(parent.get(p))) p = parent.get(p);
            GridVector cursor = value;
            while (!cursor.equals(p)) { GridVector next = parent.get(cursor); parent.put(cursor, p); cursor = next; }
            return p;
        }
        void join(GridVector a, GridVector b) {
            if (!contains(a) || !contains(b)) return; GridVector ra = root(a), rb = root(b);
            if (!ra.equals(rb)) parent.put(POSITION_ORDER.compare(ra, rb) <= 0 ? rb : ra,
                    POSITION_ORDER.compare(ra, rb) <= 0 ? ra : rb);
        }
        Set<GridVector> roots() { Set<GridVector> values = new HashSet<GridVector>(); for (GridVector p : parent.keySet()) values.add(root(p)); return values; }
        Set<GridVector> positions() { return parent.keySet(); }
    }
}
