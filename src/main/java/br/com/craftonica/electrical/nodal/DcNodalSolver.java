package br.com.craftonica.electrical.nodal;

import java.util.*;

/** Assembles linear MNA stamps and projects the solution back to oriented branches. */
public final class DcNodalSolver {
    public NodalCircuitResult solve(MnaSystem system){
        if(system==null)return failure(SolveStatus.NON_FINITE_VALUE,DiagnosticCode.NON_FINITE_VALUE);
        List<MnaSystem.Element> elements=system.getElements();List<NodeId> nodes=system.getNodes();
        if(nodes.size()+countSources(elements)>NodalLimits.MAX_UNKNOWNS)return failure(SolveStatus.MATRIX_LIMIT,DiagnosticCode.MATRIX_LIMIT);
        if(!hasReference(elements))return failure(SolveStatus.MISSING_REFERENCE,DiagnosticCode.MISSING_REFERENCE);
        Set<NodeId> connected=connectedToReference(elements);for(NodeId n:nodes)if(!connected.contains(n))return failure(SolveStatus.FLOATING_NODE,DiagnosticCode.FLOATING_NODE);
        int n=nodes.size(), sourceCount=countSources(elements), size=n+sourceCount;double[][] a=new double[size][size];double[] z=new double[size];
        Map<NodeId,Integer> indices=new HashMap<NodeId,Integer>();for(int i=0;i<n;i++)indices.put(nodes.get(i),i);
        Map<MnaSystem.Element,Integer> sourceIndices=new HashMap<MnaSystem.Element,Integer>();int sourceIndex=n;
        for(MnaSystem.Element e:elements){if(e.getKind()==MnaSystem.Element.Kind.VOLTAGE_SOURCE){sourceIndices.put(e,sourceIndex++);stampSource(a,z,indices,sourceIndices.get(e),e);}else stampResistor(a,indices,e);}
        DenseLuSolver.Result solved=DenseLuSolver.solve(a,z);if(solved.getStatus()!=SolveStatus.SOLVED)return failure(solved.getStatus(),diagnostic(solved.getStatus()));
        double[] x=solved.getSolution();Map<NodeId,NodeResult> nodeResults=new LinkedHashMap<NodeId,NodeResult>();for(NodeId node:nodes)nodeResults.put(node,new NodeResult(node,x[indices.get(node)],ValueValidity.VALID));
        Map<BranchId,BranchResult> branchResults=new LinkedHashMap<BranchId,BranchResult>();for(MnaSystem.Element e:elements){double va=value(e.getA(),indices,x),vb=value(e.getB(),indices,x),v=va-vb,current;if(e.getKind()==MnaSystem.Element.Kind.RESISTOR)current=v/e.getValue();else current=x[sourceIndices.get(e)];branchResults.put(e.getId(),new BranchResult(e.getId(),v,current,v*current,ValueValidity.VALID));}
        return new NodalCircuitResult(SolveStatus.SOLVED,nodeResults,branchResults,Collections.<CircuitDiagnostic>emptyList(),solved.getResidual(),solved.getConditionEstimate());
    }
    private static int countSources(List<MnaSystem.Element> es){int n=0;for(MnaSystem.Element e:es)if(e.getKind()==MnaSystem.Element.Kind.VOLTAGE_SOURCE)n++;return n;}
    private static boolean hasReference(List<MnaSystem.Element> es){for(MnaSystem.Element e:es)if(e.getA().isReference()||e.getB().isReference())return true;return false;}
    private static Set<NodeId> connectedToReference(List<MnaSystem.Element> es){Set<NodeId> seen=new HashSet<NodeId>();Deque<NodeId> q=new ArrayDeque<NodeId>();q.add(NodeId.REFERENCE);while(!q.isEmpty()){NodeId x=q.remove();for(MnaSystem.Element e:es){NodeId next=null;if(e.getA().equals(x))next=e.getB();else if(e.getB().equals(x))next=e.getA();if(next!=null&&!next.isReference()&&seen.add(next))q.add(next);}}return seen;}
    private static void stampResistor(double[][] a,Map<NodeId,Integer> ix,MnaSystem.Element e){double g=1.0/e.getValue();add(a,ix,e.getA(),e.getA(),g);add(a,ix,e.getB(),e.getB(),g);add(a,ix,e.getA(),e.getB(),-g);add(a,ix,e.getB(),e.getA(),-g);}
    private static void stampSource(double[][] a,double[] z,Map<NodeId,Integer> ix,int k,MnaSystem.Element e){Integer p=ix.get(e.getA()),q=ix.get(e.getB());if(p!=null)a[p][k]+=1;if(q!=null)a[q][k]-=1;if(p!=null)a[k][p]+=1;if(q!=null)a[k][q]-=1;z[k]+=e.getValue();}
    private static void add(double[][] a,Map<NodeId,Integer> ix,NodeId r,NodeId c,double v){Integer i=ix.get(r),j=ix.get(c);if(i!=null&&j!=null)a[i][j]+=v;}
    private static double value(NodeId n,Map<NodeId,Integer> ix,double[] x){Integer i=ix.get(n);return i==null?0:x[i];}
    private static DiagnosticCode diagnostic(SolveStatus s){switch(s){case SINGULAR_MATRIX:return DiagnosticCode.SINGULAR_MATRIX;case ILL_CONDITIONED_MATRIX:return DiagnosticCode.ILL_CONDITIONED_MATRIX;case RESIDUAL_TOO_LARGE:return DiagnosticCode.RESIDUAL_TOO_LARGE;case CONFLICTING_CONSTRAINTS:return DiagnosticCode.CONFLICTING_CONSTRAINTS;default:return DiagnosticCode.NON_FINITE_VALUE;}}
    private static NodalCircuitResult failure(SolveStatus s,DiagnosticCode c){List<CircuitDiagnostic> d=Collections.singletonList(new CircuitDiagnostic(c,CircuitDiagnostic.Severity.ERROR,Collections.emptyList()));return new NodalCircuitResult(s,Collections.<NodeId,NodeResult>emptyMap(),Collections.<BranchId,BranchResult>emptyMap(),d,Double.NaN,Double.NaN);}
}
