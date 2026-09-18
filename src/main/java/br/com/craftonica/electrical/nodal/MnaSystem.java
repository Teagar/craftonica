package br.com.craftonica.electrical.nodal;

import java.util.*;

/** Pure, deterministic list of linear DC stamps. NodeId.REFERENCE is ground. */
public final class MnaSystem {
    public static final class Element {
        public enum Kind { RESISTOR, VOLTAGE_SOURCE, SWITCH, BREAKER, LED, DIODE }
        private final Kind kind; private final BranchId id; private final NodeId a,b; private final double value;
        private final boolean closed, burned;
        private final double dynamicResistance;
        private Element(Kind k,BranchId id,NodeId a,NodeId b,double value,boolean closed){this(k,id,a,b,value,closed,false,0.0);}
        private Element(Kind k,BranchId id,NodeId a,NodeId b,double value,boolean closed,boolean burned){this(k,id,a,b,value,closed,burned,0.0);}
        private Element(Kind k,BranchId id,NodeId a,NodeId b,double value,boolean closed,boolean burned,double rd){this.kind=k;this.id=id;this.a=a;this.b=b;this.value=value;this.closed=closed;this.burned=burned;this.dynamicResistance=rd;}
        public Kind getKind(){return kind;} public BranchId getId(){return id;} public NodeId getA(){return a;} public NodeId getB(){return b;} public double getValue(){return value;}
        public boolean isClosed(){return closed;}
        public boolean isBurned(){return burned;}
        public double getDynamicResistance(){return dynamicResistance;}
    }
    private final List<NodeId> nodes; private final List<Element> elements;
    private MnaSystem(List<NodeId> nodes,List<Element> elements){this.nodes=Collections.unmodifiableList(new ArrayList<NodeId>(nodes));this.elements=Collections.unmodifiableList(new ArrayList<Element>(elements));}
    public List<NodeId> getNodes(){return nodes;} public List<Element> getElements(){return elements;}
    public int getUnknownCount(){int count=nodes.size();for(Element element:elements)if(element.kind==Element.Kind.VOLTAGE_SOURCE)count++;return count;}
    public boolean hasNonlinearElements(){for(Element element:elements)if(element.kind==Element.Kind.LED||element.kind==Element.Kind.DIODE)return true;return false;}
    public MnaSystem deenergizedWithTestSource(BranchId testId,NodeId positive,NodeId negative){
        if(testId==null||positive==null||negative==null||positive.equals(negative)||hasNonlinearElements())return null;
        Builder builder=builder();
        for(Element element:elements){
            switch(element.kind){
                case RESISTOR: builder.resistor(element.id,element.a,element.b,element.value); break;
                case VOLTAGE_SOURCE: builder.voltageSource(element.id,element.a,element.b,0.0); break;
                case SWITCH: builder.switchBranch(element.id,element.a,element.b,element.closed); break;
                case BREAKER: builder.breaker(element.id,element.a,element.b,element.closed); break;
                default: return null;
            }
        }
        builder.voltageSource(testId,positive,negative,1.0);
        return builder.build();
    }
    public static Builder builder(){return new Builder();}
    public static final class Builder {
        private final Set<NodeId> nodes=new TreeSet<NodeId>(); private final List<Element> elements=new ArrayList<Element>();
        private void node(NodeId n){if(n!=null&&!n.isReference())nodes.add(n);}
        public Builder resistor(BranchId id,NodeId a,NodeId b,double ohms){if(id==null||a==null||b==null||!(ohms>0)||Double.isInfinite(ohms)||Double.isNaN(ohms))throw new IllegalArgumentException("Resistor invalido");node(a);node(b);elements.add(new Element(Element.Kind.RESISTOR,id,a,b,ohms,true));return this;}
        public Builder voltageSource(BranchId id,NodeId positive,NodeId negative,double volts){if(id==null||positive==null||negative==null||Double.isInfinite(volts)||Double.isNaN(volts))throw new IllegalArgumentException("Fonte invalida");node(positive);node(negative);elements.add(new Element(Element.Kind.VOLTAGE_SOURCE,id,positive,negative,volts,true));return this;}
        public Builder switchBranch(BranchId id,NodeId a,NodeId b,boolean closed){if(id==null||a==null||b==null)throw new IllegalArgumentException("Chave invalida");node(a);node(b);elements.add(new Element(Element.Kind.SWITCH,id,a,b,0.01,closed));return this;}
        public Builder breaker(BranchId id,NodeId a,NodeId b,boolean closed){if(id==null||a==null||b==null)throw new IllegalArgumentException("Disjuntor invalido");node(a);node(b);elements.add(new Element(Element.Kind.BREAKER,id,a,b,0.01,closed));return this;}
        public Builder led(BranchId id,NodeId anode,NodeId cathode){return led(id,anode,cathode,false);}
        public Builder led(BranchId id,NodeId anode,NodeId cathode,boolean burned){return diodeElement(Element.Kind.LED,id,anode,cathode,DcComponentParameters.DIODE_FORWARD_VOLTAGE,DcComponentParameters.DIODE_DYNAMIC_RESISTANCE_OHMS,burned);}
        public Builder diode(BranchId id,NodeId anode,NodeId cathode){return diode(id,anode,cathode,DcComponentParameters.DIODE_FORWARD_VOLTAGE,DcComponentParameters.DIODE_DYNAMIC_RESISTANCE_OHMS);}
        public Builder diode(BranchId id,NodeId anode,NodeId cathode,double vf,double rd){return diodeElement(Element.Kind.DIODE,id,anode,cathode,vf,rd,false);}
        private Builder diodeElement(Element.Kind kind,BranchId id,NodeId anode,NodeId cathode,double vf,double rd,boolean burned){if(id==null||anode==null||cathode==null||!(vf>=0)||!(rd>0)||Double.isNaN(vf)||Double.isInfinite(vf)||Double.isNaN(rd)||Double.isInfinite(rd))throw new IllegalArgumentException("Diodo invalido");node(anode);node(cathode);elements.add(new Element(kind,id,anode,cathode,vf,false,burned,rd));return this;}
        public Builder powerSource(BranchId id,NodeId positive,NodeId internal){return powerSource(id,positive,internal,DcComponentParameters.SOURCE_VOLTAGE,DcComponentParameters.SOURCE_INTERNAL_RESISTANCE_OHMS);}
        public Builder powerSource(BranchId id,NodeId positive,NodeId internal,double volts,double resistance){return thevenin(id,positive,internal,volts,resistance);}
        public Builder thevenin(BranchId id,NodeId positive,NodeId internal,double volts,double resistance){voltageSource(id,internal,NodeId.REFERENCE,volts);return resistor(new BranchId(id.getPosition(),id.getComponentKind()+"_internal",id.getOrdinal()),internal,positive,resistance);}
        public Builder thevenin(BranchId id,NodeId positive,NodeId negative,NodeId internal,double volts,double resistance){voltageSource(id,internal,negative,volts);return resistor(new BranchId(id.getPosition(),id.getComponentKind()+"_internal",id.getOrdinal()),internal,positive,resistance);}
        public MnaSystem build(){Collections.sort(elements,new Comparator<Element>(){public int compare(Element x,Element y){return x.id.compareTo(y.id);}});return new MnaSystem(new ArrayList<NodeId>(nodes),elements);}
    }
}
