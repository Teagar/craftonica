package br.com.craftonica.electrical.nodal;
import br.com.craftonica.network.BlockPosition;
import java.util.*;
public final class CircuitDiagnostic implements Comparable<CircuitDiagnostic> {
    public enum Severity { INFO, WARNING, ERROR }
    private final DiagnosticCode code; private final Severity severity; private final List<BlockPosition> positions; private final String translationKey;
    public CircuitDiagnostic(DiagnosticCode code, Severity severity, List<BlockPosition> positions) { this(code,severity,positions,"craftonica.diagnostic."+code.name().toLowerCase(Locale.ENGLISH)); }
    public CircuitDiagnostic(DiagnosticCode code, Severity severity, List<BlockPosition> positions, String key) { this.code=code;this.severity=severity;this.positions=Collections.unmodifiableList(new ArrayList<BlockPosition>(positions));this.translationKey=key; }
    public DiagnosticCode getCode(){return code;} public Severity getSeverity(){return severity;} public List<BlockPosition> getPositions(){return positions;} public String getTranslationKey(){return translationKey;}
    @Override public int compareTo(CircuitDiagnostic d){int c=severity.compareTo(d.severity); if(c==0)c=code.compareTo(d.code); if(c!=0)return c; int n=Math.min(positions.size(),d.positions.size()); for(int i=0;i<n;i++){BlockPosition a=positions.get(i),b=d.positions.get(i);c=Integer.compare(a.x,b.x);if(c==0)c=Integer.compare(a.y,b.y);if(c==0)c=Integer.compare(a.z,b.z);if(c!=0)return c;} return Integer.compare(positions.size(),d.positions.size());}
}
