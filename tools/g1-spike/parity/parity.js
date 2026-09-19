const buf=new DataView(new ArrayBuffer(8));
function bits(d){buf.setFloat64(0,d);let h='';for(let i=0;i<8;i++)h+=buf.getUint8(i).toString(16).padStart(2,'0');return h.replace(/^0+/,'');}
function jointAngleAtan2(ax,ay,bx,by,cx,cy){
  const v1x=ax-bx,v1y=ay-by,v2x=cx-bx,v2y=cy-by;
  const cross=v1x*v2y-v1y*v2x,dot=v1x*v2x+v1y*v2y;
  return Math.atan2(Math.abs(cross),dot)*180.0/Math.PI;
}
function jointAngleAcos(ax,ay,bx,by,cx,cy){
  const v1x=ax-bx,v1y=ay-by,v2x=cx-bx,v2y=cy-by;
  const n1=Math.sqrt(v1x*v1x+v1y*v1y),n2=Math.sqrt(v2x*v2x+v2y*v2y);
  let cos=(v1x*v2x+v1y*v2y)/(n1*n2);
  if(cos>1)cos=1; if(cos<-1)cos=-1;
  return Math.acos(cos)*180.0/Math.PI;
}
let reps=5,perRep=40,atan2Sum=0,acosSum=0,repCount=0,phase="TOP",phases="",samples="";
const bottomEnter=100.0,topEnter=150.0;
for(let r=0;r<reps;r++)for(let f=0;f<perRep;f++){
  const t=f/perRep;
  const target=120.0-50.0*Math.cos(2.0*Math.PI*t);
  const rad=target*Math.PI/180.0;
  const hipX=Math.sin(rad),hipY=-Math.cos(rad);
  const a1=jointAngleAtan2(hipX,hipY,0,0,0,-1);
  const a2=jointAngleAcos(hipX,hipY,0,0,0,-1);
  atan2Sum+=a1;acosSum+=a2;
  let next=phase;
  if(phase==="TOP"&&a1<topEnter)next="DESCENDING";
  else if(phase==="DESCENDING"&&a1<=bottomEnter)next="BOTTOM";
  else if(phase==="BOTTOM"&&a1>bottomEnter)next="ASCENDING";
  else if(phase==="ASCENDING"&&a1>=topEnter)next="TOP";
  if(next!==phase){if(phase==="ASCENDING"&&next==="TOP")repCount++;phase=next;phases+=next[0];}
  const idx=r*perRep+f;
  if(idx%20===0)samples+="f"+idx+":"+bits(a1)+":"+bits(a2)+" ";
}
console.log("PLATFORM=JS");
console.log("REPS="+repCount);
console.log("PHASES="+phases);
console.log("ATAN2SUM_BITS="+bits(atan2Sum));
console.log("ACOSSUM_BITS="+bits(acosSum));
console.log("SAMPLES="+samples.trim());
