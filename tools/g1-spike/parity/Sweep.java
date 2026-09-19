public class Sweep {
    static int x = 123456789;
    static int next(){ x^=x<<13; x^=x>>>17; x^=x<<5; return x; }
    static double u(){ return (next() & 0xFFFFFFFFL) / 4294967296.0; }
    public static void main(String[] a){
        long h1=0,h2=0,h3=0,h4=0,h5=0,h6=0; int n=1000000;
        for(int i=0;i<n;i++){
            double t = u()*360.0-180.0;
            double r = t*Math.PI/180.0;
            double s=Math.sin(r), c=Math.cos(r);
            double q=Math.sqrt(Math.abs(t)+1e-12);
            double at=Math.atan2(s,c);
            double ac=Math.acos(Math.max(-1.0,Math.min(1.0,s)));
            double ang;
            {
                double v1x=s,v1y=-c,v2x=0.0,v2y=-1.0;
                double cross=v1x*v2y-v1y*v2x, dot=v1x*v2x+v1y*v2y;
                ang=Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
            }
            h1^=Double.doubleToRawLongBits(s); h2^=Double.doubleToRawLongBits(c);
            h3^=Double.doubleToRawLongBits(q); h4^=Double.doubleToRawLongBits(at);
            h5^=Double.doubleToRawLongBits(ac); h6^=Double.doubleToRawLongBits(ang);
        }
        System.out.println("n="+n);
        System.out.println("sin="+Long.toHexString(h1));
        System.out.println("cos="+Long.toHexString(h2));
        System.out.println("sqrt="+Long.toHexString(h3));
        System.out.println("atan2="+Long.toHexString(h4));
        System.out.println("acos="+Long.toHexString(h5));
        System.out.println("angle="+Long.toHexString(h6));
    }
}
