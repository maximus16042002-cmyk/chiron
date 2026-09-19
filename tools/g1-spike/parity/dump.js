const fs=require('fs');
let x=123456789|0;
function next(){x^=x<<13;x|=0;x^=x>>>17;x^=x<<5;x|=0;return x;}
function u(){return (next()>>>0)/4294967296.0;}
const n=200000; const buf=Buffer.alloc(n*5*8); let off=0;
for(let i=0;i<n;i++){
  const t=u()*360.0-180.0;
  const r=t*Math.PI/180.0;
  const s=Math.sin(r),c=Math.cos(r);
  const at=Math.atan2(s,c);
  const ac=Math.acos(Math.max(-1.0,Math.min(1.0,s)));
  const v1x=s,v1y=-c;
  const cross=v1x*(-1.0)-v1y*0.0, dot=v1y*(-1.0);
  const ang=Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
  for(const v of [s,c,at,ac,ang]){buf.writeDoubleBE(v,off);off+=8;}
}
fs.writeFileSync('js.bin',buf); console.log("wrote "+n);
