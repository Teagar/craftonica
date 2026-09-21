package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.GridVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable isolated nets derived only from physical terminal contacts and wire joins. */
public final class MobileElectricalNetlist {
    public static final int MAX_TERMINALS = 1024;
    public static final int MAX_NETWORKS = 256;
    public static final MobileElectricalNetlist EMPTY = new MobileElectricalNetlist(Collections.<MobileTerminal>emptyList());
    private final List<MobileTerminal> terminals;
    private final Map<String, MobileTerminal> byKey;
    private final int networkCount;

    public MobileElectricalNetlist(List<MobileTerminal> source) {
        if (source == null || source.size() > MAX_TERMINALS) throw new IllegalArgumentException("terminal count");
        List<MobileTerminal> copy = new ArrayList<MobileTerminal>(source);
        Collections.sort(copy, new Comparator<MobileTerminal>() {
            @Override public int compare(MobileTerminal a, MobileTerminal b) { return a.key().compareTo(b.key()); }
        });
        Map<String, MobileTerminal> map = new HashMap<String, MobileTerminal>(); Set<Integer> networks = new HashSet<Integer>();
        for (MobileTerminal terminal : copy) {
            if (terminal == null || map.put(terminal.key(), terminal) != null) throw new IllegalArgumentException("duplicate terminal");
            networks.add(Integer.valueOf(terminal.networkId));
        }
        if (networks.size() > MAX_NETWORKS) throw new IllegalArgumentException("network count");
        for (int i = 0; i < networks.size(); i++) if (!networks.contains(Integer.valueOf(i)))
            throw new IllegalArgumentException("non-canonical network ids");
        terminals = Collections.unmodifiableList(copy); byKey = Collections.unmodifiableMap(map); networkCount = networks.size();
    }

    public List<MobileTerminal> getTerminals() { return terminals; }
    public int getNetworkCount() { return networkCount; }
    public MobileTerminal terminal(GridVector module, String port) { return byKey.get(module + "/" + port); }
    public Integer network(GridVector module, String port) {
        MobileTerminal value = terminal(module, port); return value == null ? null : Integer.valueOf(value.networkId);
    }
    public List<MobileTerminal> terminalsOn(int networkId) {
        List<MobileTerminal> values = new ArrayList<MobileTerminal>();
        for (MobileTerminal terminal : terminals) if (terminal.networkId == networkId) values.add(terminal);
        return Collections.unmodifiableList(values);
    }
}
