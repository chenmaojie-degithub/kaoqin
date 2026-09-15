<template><div ref="host" class="interactive-shell-bg" aria-hidden="true"><canvas ref="canvas"/><div class="aurora aurora-a"/><div class="aurora aurora-b"/><div class="noise"/></div></template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props=defineProps({accent:{type:String,default:'#4f7cff'},intensity:{type:String,default:'subtle'}})
const host=ref(),canvas=ref();let context,frameId,resizeObserver,lastTime=0,visible=true
let width=0,height=0,dpr=1,nodes=[],waves=[]
const pointer={x:-1000,y:-1000,targetX:-1000,targetY:-1000,active:false}
const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches
const lowPower=reduced||navigator.hardwareConcurrency<=4||window.innerWidth<768

function rgb(hex){const value=hex.replace('#','');return [parseInt(value.slice(0,2),16),parseInt(value.slice(2,4),16),parseInt(value.slice(4,6),16)]}
function makeNode(){return{x:Math.random()*width,y:Math.random()*height,vx:(Math.random()-.5)*(lowPower?.12:.22),vy:(Math.random()-.5)*(lowPower?.12:.22),r:Math.random()*1.8+.5,phase:Math.random()*Math.PI*2}}
function resize(){const rect=host.value.getBoundingClientRect();width=Math.max(1,rect.width);height=Math.max(1,rect.height);dpr=Math.min(window.devicePixelRatio||1,lowPower?1:1.6);canvas.value.width=Math.round(width*dpr);canvas.value.height=Math.round(height*dpr);canvas.value.style.width=`${width}px`;canvas.value.style.height=`${height}px`;context.setTransform(dpr,0,0,dpr,0,0);const full=props.intensity==='full',count=full?Math.min(lowPower?30:64,Math.max(22,Math.floor(width*height/(lowPower?30000:22000)))):Math.min(lowPower?10:18,Math.max(8,Math.floor(width*height/72000)));nodes=Array.from({length:count},makeNode)}
function onPointer(event){pointer.targetX=event.clientX;pointer.targetY=event.clientY;pointer.active=true}
function onLeave(){pointer.active=false;pointer.targetX=-1000;pointer.targetY=-1000}
function onClick(event){if(reduced)return;waves.push({x:event.clientX,y:event.clientY,r:4,alpha:.42});if(waves.length>5)waves.shift()}
function step(time){if(!visible)return;const delta=Math.min(2,(time-lastTime)/16.67||1);lastTime=time;pointer.x+=(pointer.targetX-pointer.x)*.09;pointer.y+=(pointer.targetY-pointer.y)*.09;context.clearRect(0,0,width,height);const color=rgb(props.accent)
  const glow=context.createRadialGradient(pointer.x,pointer.y,0,pointer.x,pointer.y,260);glow.addColorStop(0,`rgba(${color.join(',')},.11)`);glow.addColorStop(1,`rgba(${color.join(',')},0)`);context.fillStyle=glow;context.fillRect(0,0,width,height)
  for(let i=0;i<nodes.length;i++){const node=nodes[i];const dx=pointer.x-node.x,dy=pointer.y-node.y,dist=Math.hypot(dx,dy)||1;if(pointer.active&&dist<180){const force=(180-dist)/180*(props.intensity==='full'?.035:.012);node.vx-=dx/dist*force;node.vy-=dy/dist*force}node.vx*=.993;node.vy*=.993;node.x+=node.vx*delta;node.y+=node.vy*delta;node.phase+=.012*delta;if(node.x<-10)node.x=width+10;if(node.x>width+10)node.x=-10;if(node.y<-10)node.y=height+10;if(node.y>height+10)node.y=-10;context.beginPath();context.arc(node.x,node.y,node.r+Math.sin(node.phase)*.25,0,Math.PI*2);context.fillStyle=`rgba(${color.join(',')},${.14+node.r*.055})`;context.fill();if(lowPower||props.intensity!=='full')continue;for(let j=i+1;j<nodes.length;j++){const other=nodes[j],distance=Math.hypot(node.x-other.x,node.y-other.y);if(distance<115){context.beginPath();context.moveTo(node.x,node.y);context.lineTo(other.x,other.y);context.strokeStyle=`rgba(${color.join(',')},${(1-distance/115)*.075})`;context.lineWidth=.7;context.stroke()}}}
  waves=waves.filter(wave=>wave.alpha>.01);for(const wave of waves){wave.r+=2.8*delta;wave.alpha*=.965;context.beginPath();context.arc(wave.x,wave.y,wave.r,0,Math.PI*2);context.strokeStyle=`rgba(${color.join(',')},${wave.alpha})`;context.lineWidth=1.4;context.stroke()}
  frameId=requestAnimationFrame(step)}
function onVisibility(){visible=!document.hidden;if(visible){lastTime=performance.now();frameId=requestAnimationFrame(step)}else cancelAnimationFrame(frameId)}
watch(()=>props.intensity,()=>context&&resize(),{flush:'post'})
onMounted(()=>{context=canvas.value.getContext('2d',{alpha:true});resizeObserver=new ResizeObserver(resize);resizeObserver.observe(host.value);window.addEventListener('pointermove',onPointer,{passive:true});window.addEventListener('pointerleave',onLeave);window.addEventListener('pointerdown',onClick,{passive:true});document.addEventListener('visibilitychange',onVisibility);resize();frameId=requestAnimationFrame(step)})
onBeforeUnmount(()=>{cancelAnimationFrame(frameId);resizeObserver?.disconnect();window.removeEventListener('pointermove',onPointer);window.removeEventListener('pointerleave',onLeave);window.removeEventListener('pointerdown',onClick);document.removeEventListener('visibilitychange',onVisibility);nodes=[];waves=[];context=null})
</script>

<style scoped>
.interactive-shell-bg{position:fixed;inset:0;overflow:hidden;pointer-events:none;background:linear-gradient(145deg,#f8fbff 0%,#eef4ff 45%,#f9fbff 100%)}canvas{position:absolute;inset:0}.aurora{position:absolute;border-radius:50%;filter:blur(18px);opacity:.38;mix-blend-mode:multiply;animation:float 14s ease-in-out infinite}.aurora-a{width:42vw;height:42vw;min-width:420px;min-height:420px;left:10%;top:-25%;background:radial-gradient(circle,var(--accent-soft),transparent 67%)}.aurora-b{width:34vw;height:34vw;min-width:360px;min-height:360px;right:-8%;bottom:-20%;background:radial-gradient(circle,rgba(115,224,255,.24),transparent 68%);animation-delay:-6s}.noise{position:absolute;inset:0;opacity:.035;background-image:url("data:image/svg+xml,%3Csvg viewBox='0 0 160 160' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.8'/%3E%3C/svg%3E")}@keyframes float{0%,100%{transform:translate3d(0,0,0) scale(1)}50%{transform:translate3d(7vw,8vh,0) scale(1.12)}}@media(prefers-reduced-motion:reduce){.aurora{animation:none}}
</style>
