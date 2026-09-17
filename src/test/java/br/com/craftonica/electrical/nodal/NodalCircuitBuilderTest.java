package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NodalCircuitBuilderTest {
    @Test public void collapsesWireAndContactsIntoCanonicalNodes() {
        ComponentSnapshot wire = component(0, 0, 0, "wire", Face.EAST, Face.WEST, new int[]{0, 1});
        ComponentSnapshot resistor = component(1, 0, 0, "resistor", Face.WEST, Face.EAST, null);
        ComponentSnapshot ground = component(2, 0, 0, "ground", Face.WEST, null, null);
        NodalCircuit c = new NodalCircuitBuilder().add(resistor).add(ground).add(wire).build();
        assertTrue(c.isValid());
        assertEquals(2, c.getNodes().size());
        assertEquals(1, c.getBranches().size());
        assertEquals(c.getNode(wire.getTerminals().get(1).getId()), c.getNode(resistor.getTerminals().get(0).getId()));
    }

    @Test public void shuffledInputHasSameFingerprintAndOrder() {
        ComponentSnapshot a=component(-1,2,0,"resistor",Face.EAST,Face.WEST,null);
        ComponentSnapshot b=component(1,0,0,"resistor",Face.EAST,Face.WEST,null);
        NodalCircuit first=new NodalCircuitBuilder().add(a).add(b).build();
        NodalCircuit second=new NodalCircuitBuilder().add(b).add(a).build();
        assertEquals(first.getTopologyFingerprint(),second.getTopologyFingerprint());
        assertEquals(first.getBranches().get(0).getId(),second.getBranches().get(0).getId());
    }

    @Test public void reportsMissingReferenceAndBlockLimit() {
        NodalCircuit missing=new NodalCircuitBuilder().add(component(0,0,0,"resistor",Face.EAST,Face.WEST,null)).build();
        assertFalse(missing.isValid()); assertEquals(DiagnosticCode.MISSING_REFERENCE,missing.getDiagnostics().get(0).getCode());
        NodalCircuitBuilder many=new NodalCircuitBuilder();
        for(int i=0;i<NodalLimits.MAX_BLOCKS+1;i++) many.add(component(i,0,0,"wire",Face.UP,null,null));
        assertEquals(DiagnosticCode.NETWORK_TOO_LARGE,many.build().getDiagnostics().get(0).getCode());
    }

    @Test public void nodeOrderIsNumericAndFingerprintIncludesElectricalState() {
        TerminalId negativeTen = new TerminalId(new BlockPosition(-10, 0, 0), Face.UP, 0);
        TerminalId negativeTwo = new TerminalId(new BlockPosition(-2, 0, 0), Face.UP, 0);
        assertTrue(new NodeId(negativeTen).compareTo(new NodeId(negativeTwo)) < 0);

        ComponentSnapshot open = statefulSwitch(false, Collections.<int[]>emptyList());
        ComponentSnapshot closed = statefulSwitch(true, Collections.<int[]>emptyList());
        ComponentSnapshot incorrectlyShorted = statefulSwitch(true, Collections.singletonList(new int[]{0, 1}));
        assertNotEquals(new NodalCircuitBuilder().add(open).build().getTopologyFingerprint(),
                new NodalCircuitBuilder().add(closed).build().getTopologyFingerprint());
        assertNotEquals(new NodalCircuitBuilder().add(closed).build().getTopologyFingerprint(),
                new NodalCircuitBuilder().add(incorrectlyShorted).build().getTopologyFingerprint());
    }

    private ComponentSnapshot statefulSwitch(boolean closed, List<int[]> groups) {
        BlockPosition position = new BlockPosition(0, 0, 0);
        List<TerminalSnapshot> terminals = Arrays.asList(new TerminalSnapshot(position, Face.WEST, 0),
                new TerminalSnapshot(position, Face.EAST, 1));
        Map<String, String> state = new HashMap<String, String>();
        state.put("closed", Boolean.toString(closed));
        return new ComponentSnapshot(position, "switch", terminals, Collections.<String, Double>emptyMap(), state, groups);
    }

    private ComponentSnapshot component(int x,int y,int z,String kind,Face first,Face second,int[] group) {
        List<TerminalSnapshot> t=new ArrayList<TerminalSnapshot>(); t.add(new TerminalSnapshot(new BlockPosition(x,y,z),first));
        if(second!=null)t.add(new TerminalSnapshot(new BlockPosition(x,y,z),second));
        List<int[]> groups=group==null?Collections.<int[]>emptyList():Collections.singletonList(group);
        return new ComponentSnapshot(new BlockPosition(x,y,z),kind,t,Collections.<String,Double>emptyMap(),Collections.<String,String>emptyMap(),groups);
    }
}
