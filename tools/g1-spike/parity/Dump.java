import java.io.*;
public class Dump {
    static int x = 123456789;
    static int next(){ x^=x<<13; x^=x>>>17; x^=x<<5; return x; }
    static double u(){ return (next() & 0xFFFFFFFFL) / 4294967296.0; }
    public static void main(String[] a) throws Exception {
        int n=200000;
        DataOutputStream o=new DataOutputStream(new BufferedOutputStream(new FileOutputStream("jvm.bin")));
        for(int i=0;i<n;i++){
            double t=u()*360.0-180.0;
            double r=t*Math.PI/180.0;
            double s=Math.sin(r), c=Math.cos(r);
            double at=Math.atan2(s,c);
            double ac=Math.acos(Math.max(-1.0,Math.min(1.0,s)));
            double v1x=s,v1y=-c;
            double cross=v1x*(-1.0)-v1y*0.0, dot=v1y*(-1.0);
            double ang=Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
            o.writeDouble(s);o.writeDouble(c);o.writeDouble(at);o.writeDouble(ac);o.writeDouble(ang);
        }
        o.close(); System.out.println("wrote "+n);
    }
}
