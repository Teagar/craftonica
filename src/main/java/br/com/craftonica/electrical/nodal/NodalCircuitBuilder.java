package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Pure topology extraction. World adapters are responsible for producing snapshots. */
public final class NodalCircuitBuilder {
    private final List<ComponentSnapshot> input = new ArrayList<ComponentSnapshot>();
    public NodalCircuitBuilder add(ComponentSnapshot snapshot) { if(snapshot==null) throw new IllegalArgumentException("Snapshot nulo"); input.add(snapshot); return this; }
    public NodalCircuitBuilder addComponent(ComponentSnapshot snapshot) { return add(snapshot); }
    public NodalCircuit build() {
        List<ComponentSnapshot> components=new ArrayList<ComponentSnapshot>(input);
        Collections.sort(components,new Comparator<ComponentSnapshot>(){public int compare(ComponentSnapshot a,ComponentSnapshot b){return position(a.getPosition(),b.getPosition());}});
        List<CircuitDiagnostic> errors=new ArrayList<CircuitDiagnostic>();
        if(components.size()>NodalLimits.MAX_BLOCKS) errors.add(diag(DiagnosticCode.NETWORK_TOO_LARGE,components));
        for(ComponentSnapshot c:components) for(Map.Entry<String,Double> parameter:c.getParameters().entrySet()) {
            Double value=parameter.getValue();
            if(value==null || value.isNaN() || value.isInfinite() || ("resistance".equalsIgnoreCase(parameter.getKey()) && value.doubleValue()<=0.0))
                errors.add(diag(DiagnosticCode.INVALID_COMPONENT_DATA,Collections.singletonList(c)));
        }
        List<TerminalId> ids=new ArrayList<TerminalId>(); Map<TerminalId,ComponentSnapshot> owners=new HashMap<TerminalId,ComponentSnapshot>();
        for(ComponentSnapshot c:components) for(TerminalSnapshot t:c.getTerminals()){ if(!owners.containsKey(t.getId())){ids.add(t.getId());owners.put(t.getId(),c);} else errors.add(diag(DiagnosticCode.INVALID_COMPONENT_DATA,Collections.singletonList(c))); }
        Collections.sort(ids); if(ids.size()>NodalLimits.MAX_TERMINALS) errors.add(diag(DiagnosticCode.TERMINAL_LIMIT,components));
        UnionFind uf=new UnionFind(ids);
        for(ComponentSnapshot c:components){List<TerminalSnapshot> ts=c.getTerminals();for(int[] group:c.getConductorGroups())for(int i=1;i<group.length;i++)if(valid(group[0],ts)&&valid(group[i],ts))uf.union(ts.get(group[0]).getId(),ts.get(group[i]).getId());}
        Map<TerminalId,TerminalId> byId=new HashMap<TerminalId,TerminalId>();for(TerminalId id:ids)byId.put(id,id);
        for(TerminalId id:ids){TerminalId other=adjacent(id,owners);if(other!=null&&id.compareTo(other)<0)uf.union(id,other);}
        Map<TerminalId,NodeId> terminalNodes=new LinkedHashMap<TerminalId,NodeId>();TreeSet<NodeId> nodeSet=new TreeSet<NodeId>();
        boolean ground=false; Set<TerminalId> groundRoots=new HashSet<TerminalId>();
        for(ComponentSnapshot c:components)if("ground".equalsIgnoreCase(c.getKind())){ground=true;for(TerminalSnapshot t:c.getTerminals())groundRoots.add(uf.root(t.getId()));}
        for(TerminalId id:ids){TerminalId root=uf.root(id);NodeId n=nodeSetNode(root,groundRoots.contains(root));terminalNodes.put(id,n);nodeSet.add(n);}
        if(!ground)errors.add(diag(DiagnosticCode.MISSING_REFERENCE,components));
        List<NodalBranch> branches=new ArrayList<NodalBranch>();
        for(ComponentSnapshot c:components)if(c.getTerminals().size()>=2&&!isConductor(c.getKind())){
            List<TerminalSnapshot> ts=c.getTerminals();
            branches.add(new NodalBranch(new BranchId(c.getPosition(),c.getKind(),0),ts.get(0).getId(),ts.get(1).getId()));
            if ("potentiometer".equalsIgnoreCase(c.getKind()) && ts.size() >= 3)
                branches.add(new NodalBranch(new BranchId(c.getPosition(),c.getKind(),1),ts.get(1).getId(),ts.get(2).getId()));
        }
        Collections.sort(branches,new Comparator<NodalBranch>(){public int compare(NodalBranch a,NodalBranch b){return a.getId().compareTo(b.getId());}});
        if(branches.size()>NodalLimits.MAX_BRANCHES)errors.add(diag(DiagnosticCode.BRANCH_LIMIT,components)); Collections.sort(errors);
        return new NodalCircuit(new ArrayList<NodeId>(nodeSet),branches,terminalNodes,components,errors,fingerprint(components,branches));
    }
    private NodeId nodeSetNode(TerminalId root,boolean reference){return reference?NodeId.REFERENCE:new NodeId(root);}
    private boolean isConductor(String kind){return "wire".equalsIgnoreCase(kind)||"ground".equalsIgnoreCase(kind);}
    private boolean valid(int i,List<TerminalSnapshot> t){return i>=0&&i<t.size();}
    private CircuitDiagnostic diag(DiagnosticCode code,List<ComponentSnapshot> cs){List<BlockPosition> p=new ArrayList<BlockPosition>();for(ComponentSnapshot c:cs)p.add(c.getPosition());return new CircuitDiagnostic(code,CircuitDiagnostic.Severity.ERROR,p);}
    private int position(BlockPosition a,BlockPosition b){int c=Integer.compare(a.x,b.x);if(c==0)c=Integer.compare(a.y,b.y);return c==0?Integer.compare(a.z,b.z):c;}
    private TerminalId adjacent(TerminalId id,Map<TerminalId,ComponentSnapshot> owners){for(TerminalId candidate:owners.keySet())if(candidate.getPosition().equals(next(id))&&candidate.getFace()==opposite(id.getFace()))return candidate;return null;}
    private Face opposite(Face face){switch(face){case DOWN:return Face.UP;case UP:return Face.DOWN;case NORTH:return Face.SOUTH;case SOUTH:return Face.NORTH;case WEST:return Face.EAST;default:return Face.WEST;}}
    private BlockPosition next(TerminalId id){int x=id.getPosition().x,y=id.getPosition().y,z=id.getPosition().z;switch(id.getFace()){case DOWN:y--;break;case UP:y++;break;case NORTH:z--;break;case SOUTH:z++;break;case WEST:x--;break;case EAST:x++;break;}return new BlockPosition(x,y,z);}
    private String fingerprint(List<ComponentSnapshot> cs,List<NodalBranch> bs){StringBuilder s=new StringBuilder("nodal-1|");for(ComponentSnapshot c:cs){s.append(c.getPosition()).append('|').append(c.getKind());for(TerminalSnapshot t:c.getTerminals())s.append('|').append(t.getId());for(Map.Entry<String,Double> e:c.getParameters().entrySet())s.append('|').append(e.getKey()).append('=').append(e.getValue());}for(NodalBranch b:bs)s.append('|').append(b.getId()).append(':').append(b.getA()).append(':').append(b.getB());try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(Charset.forName("UTF-8")));StringBuilder h=new StringBuilder();for(byte v:d)h.append(String.format("%02x",v&255));return h.toString();}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static final class UnionFind {private final Map<TerminalId,TerminalId> p=new HashMap<TerminalId,TerminalId>();UnionFind(List<TerminalId> ids){for(TerminalId i:ids)p.put(i,i);}TerminalId root(TerminalId i){TerminalId r=p.get(i);while(!r.equals(p.get(r)))r=p.get(r);return r;}void union(TerminalId a,TerminalId b){TerminalId x=root(a),y=root(b);if(!x.equals(y)){if(x.compareTo(y)<0)p.put(y,x);else p.put(x,y);}}}
}
