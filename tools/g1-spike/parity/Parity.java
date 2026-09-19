public class Parity {
    static double jointAngleAtan2(double ax,double ay,double bx,double by,double cx,double cy){
        double v1x=ax-bx, v1y=ay-by, v2x=cx-bx, v2y=cy-by;
        double cross=v1x*v2y-v1y*v2x, dot=v1x*v2x+v1y*v2y;
        return Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
    }
    static double jointAngleAcos(double ax,double ay,double bx,double by,double cx,double cy){
        double v1x=ax-bx, v1y=ay-by, v2x=cx-bx, v2y=cy-by;
        double n1=Math.sqrt(v1x*v1x+v1y*v1y), n2=Math.sqrt(v2x*v2x+v2y*v2y);
        double cos=(v1x*v2x+v1y*v2y)/(n1*n2);
        if(cos>1.0)cos=1.0; if(cos<-1.0)cos=-1.0;
        return Math.acos(cos)*180.0/Math.PI;
    }
    public static void main(String[] a){
        int reps=5, perRep=40;
        double atan2Sum=0, acosSum=0;
        int repCount=0; String phase="TOP"; StringBuilder phases=new StringBuilder();
        StringBuilder samples=new StringBuilder();
        double bottomEnter=100.0, topEnter=150.0;
        for(int r=0;r<reps;r++){
            for(int f=0;f<perRep;f++){
                double t=(double)f/(double)perRep;
                double target=120.0-50.0*Math.cos(2.0*Math.PI*t);
                double rad=target*Math.PI/180.0;
                double hipX=Math.sin(rad), hipY=-Math.cos(rad);
                double a1=jointAngleAtan2(hipX,hipY,0.0,0.0,0.0,-1.0);
                double a2=jointAngleAcos(hipX,hipY,0.0,0.0,0.0,-1.0);
                atan2Sum+=a1; acosSum+=a2;
                String next=phase;
                if(phase.equals("TOP")&&a1<topEnter)next="DESCENDING";
                else if(phase.equals("DESCENDING")&&a1<=bottomEnter)next="BOTTOM";
                else if(phase.equals("BOTTOM")&&a1>bottomEnter)next="ASCENDING";
                else if(phase.equals("ASCENDING")&&a1>=topEnter)next="TOP";
                if(!next.equals(phase)){
                    if(phase.equals("ASCENDING")&&next.equals("TOP"))repCount++;
                    phase=next; phases.append(next.charAt(0));
                }
                int idx=r*perRep+f;
                if(idx%20==0) samples.append("f"+idx+":"+Long.toHexString(Double.doubleToRawLongBits(a1))+":"+Long.toHexString(Double.doubleToRawLongBits(a2))+" ");
            }
        }
        System.out.println("PLATFORM=JVM");
        System.out.println("REPS="+repCount);
        System.out.println("PHASES="+phases);
        System.out.println("ATAN2SUM_BITS="+Long.toHexString(Double.doubleToRawLongBits(atan2Sum)));
        System.out.println("ACOSSUM_BITS="+Long.toHexString(Double.doubleToRawLongBits(acosSum)));
        System.out.println("SAMPLES="+samples.toString().trim());
    }
}
