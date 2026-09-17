package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import java.util.*;

public final class ComponentSnapshot {
    private final BlockPosition position; private final String kind; private final List<TerminalSnapshot> terminals;
    private final Map<String, Double> parameters; private final Map<String, String> state; private final List<int[]> conductorGroups;
    public ComponentSnapshot(BlockPosition position, String kind, List<TerminalSnapshot> terminals) {
        this(position, kind, terminals, Collections.<String,Double>emptyMap(), Collections.<String,String>emptyMap(), Collections.<int[]>emptyList());
    }
    public ComponentSnapshot(BlockPosition position, String kind, List<TerminalSnapshot> terminals, Map<String,Double> parameters,
                             Map<String,String> state, List<int[]> conductorGroups) {
        if (position == null || kind == null || terminals == null) throw new IllegalArgumentException("Snapshot invalido");
        this.position=position; this.kind=kind; this.terminals=Collections.unmodifiableList(new ArrayList<TerminalSnapshot>(terminals));
        this.parameters=Collections.unmodifiableMap(new TreeMap<String,Double>(parameters)); this.state=Collections.unmodifiableMap(new TreeMap<String,String>(state));
        List<int[]> groups=new ArrayList<int[]>(); for(int[] g: conductorGroups) groups.add(g.clone()); this.conductorGroups=Collections.unmodifiableList(groups);
    }
    public BlockPosition getPosition(){return position;} public String getKind(){return kind;} public List<TerminalSnapshot> getTerminals(){return terminals;}
    public Map<String,Double> getParameters(){return parameters;} public Map<String,String> getState(){return state;}
    public List<int[]> getConductorGroups(){List<int[]> copy=new ArrayList<int[]>();for(int[] group:conductorGroups)copy.add(group.clone());return Collections.unmodifiableList(copy);}
}
