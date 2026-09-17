package br.com.craftonica.electrical.nodal;

import java.util.*;

/** Dense LU with fixed tolerances; no library or machine-dependent ordering. */
public strictfp final class DenseLuSolver {
    public static final double PIVOT_TOLERANCE=1e-12, RESIDUAL_TOLERANCE=1e-9, CONDITION_LIMIT=1e12;
    public static final class Result {
        private final double[] solution; private final SolveStatus status; private final double residual, condition;
        Result(double[] x,SolveStatus s,double r,double c){solution=x;status=s;residual=r;condition=c;}
        public double[] getSolution(){return solution==null?null:solution.clone();} public SolveStatus getStatus(){return status;}
        public double getResidual(){return residual;} public double getConditionEstimate(){return condition;}
    }
    private DenseLuSolver() { }
    public static Result solve(double[][] input,double[] rhs){
        if(input==null||rhs==null||input.length!=rhs.length)return new Result(null,SolveStatus.NON_FINITE_VALUE,Double.NaN,Double.NaN);
        int n=rhs.length;if(n==0)return new Result(new double[0],SolveStatus.SOLVED,0,1);
        double[][] a=new double[n][n];double[] b=rhs.clone();double[] scales=new double[n];
        for(int i=0;i<n;i++){if(input[i]==null||input[i].length!=n||!finite(b[i]))return bad();for(int j=0;j<n;j++){a[i][j]=input[i][j];if(!finite(a[i][j]))return bad();scales[i]=Math.max(scales[i],Math.abs(a[i][j]));}if(scales[i]==0)scales[i]=1;}
        double min=Double.POSITIVE_INFINITY,max=0;
        for(int k=0;k<n;k++){
            int pivot=k;double best=-1;
            for(int i=k;i<n;i++){double candidate=Math.abs(a[i][k])/scales[i];if(candidate>best){best=candidate;pivot=i;}}
            if(!(best>PIVOT_TOLERANCE)){
                boolean conflict=false;for(int i=k;i<n;i++){double row=0;for(int j=k;j<n;j++)row=Math.max(row,Math.abs(a[i][j]));if(row<=PIVOT_TOLERANCE&&Math.abs(b[i])>PIVOT_TOLERANCE)conflict=true;}
                return new Result(null,conflict?SolveStatus.CONFLICTING_CONSTRAINTS:SolveStatus.SINGULAR_MATRIX,Double.NaN,Double.NaN);
            }
            if(pivot!=k){double[] tr=a[k];a[k]=a[pivot];a[pivot]=tr;double tb=b[k];b[k]=b[pivot];b[pivot]=tb;double ts=scales[k];scales[k]=scales[pivot];scales[pivot]=ts;}
            double p=Math.abs(a[k][k]);min=Math.min(min,p);max=Math.max(max,p);
            for(int i=k+1;i<n;i++){double factor=a[i][k]/a[k][k];a[i][k]=factor;for(int j=k+1;j<n;j++)a[i][j]-=factor*a[k][j];b[i]-=factor*b[k];}
        }
        double[] x=new double[n];for(int i=n-1;i>=0;i--){double sum=b[i];for(int j=i+1;j<n;j++)sum-=a[i][j]*x[j];x[i]=sum/a[i][i];if(!finite(x[i]))return bad();}
        double cond=max/min; if(cond>CONDITION_LIMIT)return new Result(null,SolveStatus.ILL_CONDITIONED_MATRIX,Double.NaN,cond);
        double residual=0,norm=0;for(int i=0;i<n;i++){double sum=0;for(int j=0;j<n;j++)sum+=input[i][j]*x[j];residual=Math.max(residual,Math.abs(sum-rhs[i]));norm=Math.max(norm,Math.abs(rhs[i]));}
        residual/=Math.max(1,norm);if(!finite(residual))return bad();if(residual>RESIDUAL_TOLERANCE)return new Result(null,SolveStatus.RESIDUAL_TOO_LARGE,residual,cond);
        return new Result(x,SolveStatus.SOLVED,residual,cond);
    }
    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    private static Result bad(){return new Result(null,SolveStatus.NON_FINITE_VALUE,Double.NaN,Double.NaN);}
}
