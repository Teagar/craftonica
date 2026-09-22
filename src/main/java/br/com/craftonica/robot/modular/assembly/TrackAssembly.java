package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Derived declared belt modules and explicit reasons why a module cannot transmit effort. */
public final class TrackAssembly {
    public enum Diagnostic { DRIVE_PATH_OPEN, STRUCTURAL_MOUNT_OPEN, INVALID_CONTACT_AREA, TRACK_LIMIT_EXCEEDED }
    private final List<Unit> units; private final List<Problem> diagnostics;
    TrackAssembly(List<Unit> units,List<Problem> diagnostics){
        this.units=Collections.unmodifiableList(new ArrayList<Unit>(units));
        this.diagnostics=Collections.unmodifiableList(new ArrayList<Problem>(diagnostics));
    }
    public List<Unit> getUnits(){return units;} public List<Problem> getDiagnostics(){return diagnostics;}
    public boolean isValid(){return diagnostics.isEmpty();}
    public static final class Unit {
        public final GridVector position;public final Direction rollingDirection;
        public final double contactLengthMetres,contactWidthMetres,contactAreaSquareMetres,sprocketRadiusMetres;
        public final boolean driven,mounted;
        Unit(GridVector position,Direction direction,double length,double width,double radius,boolean driven,boolean mounted){
            this.position=position;this.rollingDirection=direction;this.contactLengthMetres=length;
            this.contactWidthMetres=width;this.contactAreaSquareMetres=length*width;
            this.sprocketRadiusMetres=radius;this.driven=driven;this.mounted=mounted;
        }
        public boolean transmitsEffort(){return driven&&mounted&&contactAreaSquareMetres>0.0;}
    }
    public static final class Problem implements Comparable<Problem>{
        public final Diagnostic code;public final GridVector position;
        Problem(Diagnostic code,GridVector position){this.code=code;this.position=position;}
        @Override public int compareTo(Problem other){int x=Integer.compare(position.x,other.position.x);
            if(x!=0)return x;int y=Integer.compare(position.y,other.position.y);if(y!=0)return y;
            int z=Integer.compare(position.z,other.position.z);return z!=0?z:code.compareTo(other.code);}
    }
}
