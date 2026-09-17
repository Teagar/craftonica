package br.com.craftonica.electrical.nodal;

import java.util.*;

/** Pure, deterministic list of linear DC stamps. NodeId.REFERENCE is ground. */
public final class MnaSystem {
    public static final class Element {
        public enum Kind { RESISTOR, VOLTAGE_SOURCE, SWITCH, LED }
        private final Kind kind; private final BranchId id; private final NodeId a,b; private final double value;
        private final boolean closed, burned;
        private Element(Kind k,BranchId id,NodeId a,NodeId b,double value,boolean closed){this(k,id,a,b,value,closed,false);}
        private Element(Kind k,BranchId id,NodeId a,NodeId b,double value,boolean closed,boolean burned){this.kind=k;this.id=id;this.a=a;this.b=b;this.value=value;this.closed=closed;this.burned=burned;}
        public Kind getKind(){return kind;} public BranchId getId(){return id;} public NodeId getA(){return a;} public NodeId getB(){return b;} public double getValue(){return value;}
        public boolean isClosed(){return closed;}
        public boolean isBurned(){return burned;}
    }
    private final List<NodeId> nodes; private final List<Element> elements;
    private MnaSystem(List<NodeId> nodes,List<Element> elements){this.nodes=Collections.unmodifiableList(new ArrayList<NodeId>(nodes));this.elements=Collections.unmodifiableList(new ArrayList<Element>(elements));}
    public List<NodeId> getNodes(){return nodes;} public List<Element> getElements(){return elements;}
    public static Builder builder(){return new Builder();}
    public static final class Builder {
        private final Set<NodeId> nodes=new TreeSet<NodeId>(); private final List<Element> elements=new ArrayList<Element>();
        private void node(NodeId n){if(n!=null&&!n.isReference())nodes.add(n);}
        public Builder resistor(BranchId id,NodeId a,NodeId b,double ohms){if(id==null||a==null||b==null||!(ohms>0)||Double.isInfinite(ohms)||Double.isNaN(ohms))throw new IllegalArgumentException("Resistor invalido");node(a);node(b);elements.add(new Element(Element.Kind.RESISTOR,id,a,b,ohms,true));return this;}
        public Builder voltageSource(BranchId id,NodeId positive,NodeId negative,double volts){if(id==null||positive==null||negative==null||Double.isInfinite(volts)||Double.isNaN(volts))throw new IllegalArgumentException("Fonte invalida");node(positive);node(negative);elements.add(new Element(Element.Kind.VOLTAGE_SOURCE,id,positive,negative,volts,true));return this;}
        public Builder switchBranch(BranchId id,NodeId a,NodeId b,boolean closed){if(id==null||a==null||b==null)throw new IllegalArgumentException("Chave invalida");node(a);node(b);elements.add(new Element(Element.Kind.SWITCH,id,a,b,0.01,closed));return this;}
        public Builder led(BranchId id,NodeId anode,NodeId cathode){return led(id,anode,cathode,false);}
        public Builder led(BranchId id,NodeId anode,NodeId cathode,boolean burned){if(id==null||anode==null||cathode==null)throw new IllegalArgumentException("LED invalido");node(anode);node(cathode);elements.add(new Element(Element.Kind.LED,id,anode,cathode,1.0,false,burned));return this;}
        public Builder powerSource(BranchId id,NodeId positive,NodeId internal){return thevenin(id,positive,internal,5.0,10.0);}
        public Builder thevenin(BranchId id,NodeId positive,NodeId internal,double volts,double resistance){voltageSource(id,internal,NodeId.REFERENCE,volts);return resistor(new BranchId(id.getPosition(),id.getComponentKind()+"_internal",id.getOrdinal()),internal,positive,resistance);}
        public MnaSystem build(){Collections.sort(elements,new Comparator<Element>(){public int compare(Element x,Element y){return x.id.compareTo(y.id);}});return new MnaSystem(new ArrayList<NodeId>(nodes),elements);}
    }
}
