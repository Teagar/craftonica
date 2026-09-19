package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DcNodalSolverTest {
    private static final NodeId OUT=NodeId.named("out"), MID=NodeId.named("mid");
    private static BranchId branch(String kind,int ordinal){return new BranchId(new BlockPosition(ordinal,0,0),kind,0);}
    private NodalCircuitResult solve(MnaSystem.Builder b){return new DcNodalSolver().solve(b.build());}

    @Test public void resistorSimpleUsesTheveninSource(){
        MnaSystem.Builder b=MnaSystem.builder();b.thevenin(branch("source",0),OUT,NodeId.named("internal"),5,10);b.resistor(branch("load",1),OUT,NodeId.REFERENCE,220);
        NodalCircuitResult r=solve(b);assertTrue(r.isSolved());assertEquals(5.0/230.0,r.getBranchResult(branch("load",1)).getCurrent(),1e-12);assertEquals(5*220/230.0,r.getNodeResult(OUT).getVoltage(),1e-12);
    }
    @Test public void twoTerminalTheveninSourceIsReferencedToItsNegativeTerminal(){
        NodeId negative=NodeId.named("negative"), internal=NodeId.named("sensor-internal");
        BranchId sensor=branch("analog_sensor",0), bias=branch("bias",1), load=branch("load",2);
        MnaSystem.Builder b=MnaSystem.builder();
        b.thevenin(sensor,OUT,negative,internal,2.5,1000.0);
        b.resistor(bias,negative,NodeId.REFERENCE,1.0);
        b.resistor(load,OUT,negative,10000.0);
        NodalCircuitResult r=solve(b);
        assertTrue(r.isSolved());
        assertEquals(2.5*10000.0/11000.0,r.getNodeResult(OUT).getVoltage()-r.getNodeResult(negative).getVoltage(),1e-12);
        assertEquals(2.5/11000.0,r.getBranchResult(load).getCurrent(),1e-12);
    }
    @Test public void dividerAndParallelHaveKcl(){
        MnaSystem.Builder d=MnaSystem.builder();d.thevenin(branch("source",0),OUT,NodeId.named("i"),5,10);d.resistor(branch("top",1),OUT,MID,1000);d.resistor(branch("bottom",2),MID,NodeId.REFERENCE,1000);NodalCircuitResult dr=solve(d);
        assertEquals(2.4875621890547263e-3,dr.getBranchResult(branch("top",1)).getCurrent(),1e-12);assertEquals(2.4875621890547263,MID.equals(MID)?dr.getNodeResult(MID).getVoltage():0,1e-9);
        MnaSystem.Builder p=MnaSystem.builder();p.thevenin(branch("source",0),OUT,NodeId.named("i"),5,10);p.resistor(branch("a",1),OUT,NodeId.REFERENCE,220);p.resistor(branch("b",2),OUT,NodeId.REFERENCE,1000);NodalCircuitResult pr=solve(p);double ia=pr.getBranchResult(branch("a",1)).getCurrent(),ib=pr.getBranchResult(branch("b",2)).getCurrent();assertEquals(ia+ib, -pr.getBranchResult(branch("source",0)).getCurrent(),1e-12);
    }
    @Test public void twoSourcesShortAndFailuresAreDiagnosed(){
        MnaSystem.Builder two=MnaSystem.builder();two.voltageSource(branch("a",0),OUT,NodeId.REFERENCE,5);two.voltageSource(branch("b",1),OUT,NodeId.REFERENCE,10);NodalCircuitResult conflict=solve(two);assertEquals(SolveStatus.CONFLICTING_CONSTRAINTS,conflict.getStatus());
        MnaSystem.Builder shorted=MnaSystem.builder();shorted.thevenin(branch("source",0),OUT,NodeId.named("i"),5,10);shorted.resistor(branch("short",1),OUT,NodeId.REFERENCE,1e-9);NodalCircuitResult sr=solve(shorted);assertTrue(sr.isSolved());assertEquals(0.5,sr.getBranchResult(branch("short",1)).getCurrent(),1e-9);assertEquals(0.0,sr.getNodeResult(OUT).getVoltage(),1e-9);assertEquals(DiagnosticCode.SHORT_CIRCUIT,sr.getDiagnostics().get(1).getCode());
        MnaSystem.Builder floating=MnaSystem.builder();floating.resistor(branch("floating",0),OUT,MID,10);assertEquals(SolveStatus.MISSING_REFERENCE,solve(floating).getStatus());
    }
    @Test public void insertionOrderDoesNotChangeResults(){
        MnaSystem.Builder a=MnaSystem.builder();a.resistor(branch("load",1),OUT,NodeId.REFERENCE,220);a.thevenin(branch("source",0),OUT,NodeId.named("i"),5,10);
        MnaSystem.Builder b=MnaSystem.builder();b.thevenin(branch("source",0),OUT,NodeId.named("i"),5,10);b.resistor(branch("load",1),OUT,NodeId.REFERENCE,220);
        assertEquals(solve(a).getNodeResult(OUT).getVoltage(),solve(b).getNodeResult(OUT).getVoltage(),0);assertEquals(solve(a).getBranchResults().keySet(),solve(b).getBranchResults().keySet());
    }

    @Test public void orientedCurrentsAndPowerObeyMnaSigns() {
        NodeId internalFive = NodeId.named("internal-five");
        NodeId internalTen = NodeId.named("internal-ten");
        BranchId five = branch("five", 0), ten = branch("ten", 1);
        MnaSystem.Builder builder = MnaSystem.builder();
        builder.thevenin(five, OUT, internalFive, 5.0, 10.0);
        builder.thevenin(ten, OUT, internalTen, 10.0, 10.0);
        NodalCircuitResult result = solve(builder);
        assertTrue(result.isSolved());
        assertEquals(7.5, result.getNodeResult(OUT).getVoltage(), 1e-12);
        assertEquals(0.25, result.getBranchResult(five).getCurrent(), 1e-12);
        assertEquals(-0.25, result.getBranchResult(ten).getCurrent(), 1e-12);
        assertTrue(result.getBranchResult(five).getAbsorbedPower() > 0.0);
        assertTrue(result.getBranchResult(ten).getAbsorbedPower() < 0.0);
    }
}
