const fs=require('fs');
const path=require('path');
const w=640,h=640;
for(const mode of ['equirect-180','equirect-360','fisheye-180','sbs-fisheye-180']) {
 const width=mode.startsWith('sbs') || mode==='equirect-360'?w*2:w;
 const data=Buffer.alloc(width*h*3);
 for(let y=0;y<h;y++)for(let x=0;x<width;x++){
  let lon,lat,valid=true;
  if(mode.includes('fisheye')) {
   const dx=((x%w)+.5)/w-.5,dy=.5-(y+.5)/h;
   const r=Math.hypot(dx,dy),theta=r*Math.PI;
   valid=r<=.5;
   const z=Math.cos(theta),s=Math.sin(theta)/(r||1);
   lon=Math.atan2(dx*s,z);lat=Math.asin(Math.max(-1,Math.min(1,dy*s)));
  } else {lon=((x+.5)/width-.5)*Math.PI*(mode==='equirect-360'?2:1);lat=(.5-(y+.5)/h)*Math.PI;}
  const degreeLon=lon*180/Math.PI,degreeLat=lat*180/Math.PI;
  const grid=Math.abs(degreeLon/15-Math.round(degreeLon/15))<.025 || Math.abs(degreeLat/15-Math.round(degreeLat/15))<.025;
  const center=Math.abs(degreeLon)<4 && Math.abs(degreeLat)<4;
  let color=!valid?[0,0,0]:center?[255,255,255]:grid?[220,220,220]:[degreeLon<0?200:35,degreeLat>0?180:40,110];
  if(mode.startsWith('sbs') && x>=w)color=[color[2],color[1],color[0]];
  const i=(y*width+x)*3;data[i]=color[0];data[i+1]=color[1];data[i+2]=color[2];
 }
 fs.writeFileSync(path.join('test-media',mode+'.ppm'),Buffer.concat([Buffer.from(`P6\n${width} ${h}\n255\n`),data]));
}

