const buf=new DataView(new ArrayBuffer(8));
function rawbits(d){buf.setFloat64(0,d);return buf.getBigUint64(0);}
let x=123456789|0;
function next(){x^=x<<13;x|=0;x^=x>>>17;x^=x<<5;x|=0;return x;}
function u(){return (next()>>>0)/4294967296.0;}
let h1=0n,h2=0n,h3=0n,h4=0n,h5=0n,h6=0n;const n=1000000;
for(let i=0;i<n;i++){
  const t=u()*360.0-180.0;
  const r=t*Math.PI/180.0;
  const s=Math.sin(r),c=Math.cos(r);
  const q=Math.sqrt(Math.abs(t)+1e-12);
  const at=Math.atan2(s,c);
  const ac=Math.acos(Math.max(-1.0,Math.min(1.0,s)));
  const v1x=s,v1y=-c,v2x=0.0,v2y=-1.0;
  const cross=v1x*v2y-v1y*v2x,dot=v1x*v2x+v1y*v2y;
  const ang=Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
  h1^=rawbits(s);h2^=rawbits(c);h3^=rawbits(q);h4^=rawbits(at);h5^=rawbits(ac);h6^=rawbits(ang);
}
const hx=v=>v.toString(16);
console.log("n="+n);
console.log("sin="+hx(h1));console.log("cos="+hx(h2));console.log("sqrt="+hx(h3));
console.log("atan2="+hx(h4));console.log("acos="+hx(h5));console.log("angle="+hx(h6));
