<template>
  <div ref="host" class="kage-bg" aria-hidden="true">
    <div class="grain" />
    <div class="vignette" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'

const host = ref()
const pointer = new THREE.Vector2()
const pointerTarget = new THREE.Vector2()
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
const coarsePointer = matchMedia('(pointer: coarse)')
let renderer, scene, camera, geometry, material, observer, raf
let running = false, last = 0, samples = 0, sampleTime = 0, renderScale = 1
let pulse = 0
let lastPointerX = innerWidth / 2
let lastPointerY = innerHeight / 2

const isLowPower = () => coarsePointer.matches || (navigator.deviceMemory || 8) <= 4 ||
  (navigator.hardwareConcurrency || 8) <= 4 || innerWidth < 768

const vertexShader = `
  varying vec2 vUv;
  void main(){ vUv=uv; gl_Position=vec4(position,1.0); }
`

const fragmentShader = `
  precision highp float;
  uniform float uTime,uLow,uPulse,uVelocity;
  uniform vec2 uResolution,uPointer,uClick;
  varying vec2 vUv;
  float hash(vec2 p){p=fract(p*vec2(123.34,345.45));p+=dot(p,p+34.345);return fract(p.x*p.y);}
  float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
  float fog(vec2 p){float v=0.0,a=.54;for(int i=0;i<4;i++){if(uLow>.5&&i>1)break;v+=a*noise(p);p=p*2.03+vec2(7.1,3.7);a*=.48;}return v;}
  void main(){
    vec2 uv=vUv,p=uv*2.0-1.0;p.x*=uResolution.x/max(uResolution.y,1.0);
    float aspect=uResolution.x/max(uResolution.y,1.0);
    vec2 cursor=vec2(uPointer.x*aspect,uPointer.y),clickPos=vec2(uClick.x*aspect,uClick.y);
    float cursorDist=length(p-cursor),t=uTime*.075;
    vec2 wake=normalize(p-cursor+vec2(.001))*exp(-cursorDist*2.7)*uVelocity*.1;
    float f=fog(p*1.12+vec2(t,-t*.56)+uPointer*.09+wake);
    vec3 color=mix(vec3(.012,.025,.043),vec3(.055,.145,.205),smoothstep(.24,.92,f)*.72);
    vec2 mp=vec2(.55,.18)+uPointer*.035;float md=length(p-mp);
    color+=vec3(.88,.09,.045)*(1.0-smoothstep(.105,.125,md))*.94;
    color+=vec3(.95,.12,.055)*exp(-md*5.2)*.34;
    float shaft=exp(-abs(p.x+.34*p.y+.28-sin(t)*.035)*4.6)*smoothstep(-.85,.8,p.y);
    color+=vec3(.12,.31,.39)*shaft*(.42+f*.46);
    float cursorGlow=exp(-cursorDist*3.7);
    color+=mix(vec3(.05,.32,.43),vec3(.92,.12,.055),smoothstep(.5,0.,cursorDist))*cursorGlow*(.34+uVelocity*.2);
    float ringDistance=length(p-clickPos);
    float ring=sin(ringDistance*46.0-uPulse*18.0)*exp(-ringDistance*4.0)*uPulse;
    color+=vec3(.95,.18,.08)*max(ring,0.0)*.72;
    float stars=step(.988+uLow*.007,hash(floor((p+t*.02)*78.0)))*smoothstep(.05,.8,f);
    color+=vec3(.95,.36,.16)*stars*(.38+.3*sin(uTime+f*12.0));
    float vig=smoothstep(1.35,.2,length((uv-.5)*vec2(1.18,.94)));
    color*=mix(.42,1.0,vig);color+=(hash(gl_FragCoord.xy+uTime)-.5)*.018;
    gl_FragColor=vec4(color,1.0);
  }
`

function resize() {
  if (!renderer || !host.value) return
  const { width, height } = host.value.getBoundingClientRect()
  if (!width || !height) return
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, isLowPower() ? 1 : 1.5) * renderScale)
  renderer.setSize(width, height, false)
  material.uniforms.uResolution.value.set(renderer.domElement.width, renderer.domElement.height)
  material.uniforms.uLow.value = isLowPower() ? 1 : 0
}

function onPointer(event) {
  pointerTarget.set(event.clientX / innerWidth * 2 - 1, -(event.clientY / innerHeight * 2 - 1))
  const speed = Math.min(1, Math.hypot(event.clientX - lastPointerX, event.clientY - lastPointerY) / 70)
  if (material) {
    material.uniforms.uVelocity.value = Math.max(material.uniforms.uVelocity.value, speed)
  }
  lastPointerX = event.clientX
  lastPointerY = event.clientY
}

function onClick(event) {
  if (!material || reducedMotion.matches) return
  material.uniforms.uClick.value.set(event.clientX / innerWidth * 2 - 1, -(event.clientY / innerHeight * 2 - 1))
  pulse = 1
}

function frame(now) {
  if (!running) return
  const interval = reducedMotion.matches ? 120 : isLowPower() ? 32 : 0
  const elapsed = now - last
  if (elapsed >= interval) {
    last = now
    pointer.lerp(pointerTarget, reducedMotion.matches ? .025 : .055)
    material.uniforms.uPointer.value.copy(pointer)
    material.uniforms.uTime.value = now / 1000
    material.uniforms.uPulse.value = pulse
    pulse = Math.max(0, pulse - elapsed / 900)
    material.uniforms.uVelocity.value *= .9
    renderer.render(scene, camera)
    if (!reducedMotion.matches && ++samples >= 60) {
      const average = sampleTime / samples
      const next = average > 25 ? Math.max(.62, renderScale - .12) :
        average < 17 && !isLowPower() ? Math.min(1, renderScale + .06) : renderScale
      samples = sampleTime = 0
      if (next !== renderScale) { renderScale = next; resize() }
    } else sampleTime += Math.min(elapsed || 16, 80)
  }
  raf = requestAnimationFrame(frame)
}

function start() { if (!running && renderer) { running = true; last = performance.now(); raf = requestAnimationFrame(frame) } }
function stop() { running = false; if (raf) cancelAnimationFrame(raf); raf = undefined }
function onVisibility() { document.hidden ? stop() : start() }

onMounted(() => {
  try {
    renderer = new THREE.WebGLRenderer({ antialias: !isLowPower(), powerPreference: isLowPower() ? 'low-power' : 'high-performance' })
    renderer.setClearColor(0x05090f, 1)
    renderer.domElement.className = 'kage-canvas'
    host.value.prepend(renderer.domElement)
    scene = new THREE.Scene()
    camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1)
    geometry = new THREE.PlaneGeometry(2, 2)
    material = new THREE.ShaderMaterial({ uniforms: { uTime:{value:0}, uLow:{value:isLowPower()?1:0}, uPulse:{value:0}, uVelocity:{value:0}, uResolution:{value:new THREE.Vector2(1,1)}, uPointer:{value:pointer}, uClick:{value:new THREE.Vector2()} }, vertexShader, fragmentShader, depthTest:false, depthWrite:false })
    scene.add(new THREE.Mesh(geometry, material))
    observer = new ResizeObserver(resize); observer.observe(host.value)
    addEventListener('pointermove', onPointer, { passive:true }); addEventListener('pointerdown', onClick, { passive:true }); addEventListener('orientationchange', resize, { passive:true })
    document.addEventListener('visibilitychange', onVisibility)
    reducedMotion.addEventListener('change', resize); coarsePointer.addEventListener('change', resize)
    resize(); start()
  } catch (error) {
    host.value.classList.add('fallback')
    console.warn('WebGL background unavailable; using CSS fallback.', error)
  }
})

onBeforeUnmount(() => {
  stop(); observer?.disconnect()
  removeEventListener('pointermove', onPointer); removeEventListener('pointerdown', onClick); removeEventListener('orientationchange', resize)
  document.removeEventListener('visibilitychange', onVisibility)
  reducedMotion.removeEventListener('change', resize); coarsePointer.removeEventListener('change', resize)
  geometry?.dispose(); material?.dispose(); renderer?.dispose(); renderer?.forceContextLoss(); renderer?.domElement.remove()
  renderer = scene = camera = geometry = material = undefined
})
</script>

<style scoped>
.kage-bg{position:absolute;inset:0;overflow:hidden;pointer-events:none;background:radial-gradient(circle at 68% 38%,rgba(197,38,24,.32),transparent 22%),linear-gradient(135deg,#06111a,#020509 70%)}
.kage-bg :deep(.kage-canvas){display:block;width:100%;height:100%}
.grain,.vignette{position:absolute;inset:0}.grain{opacity:.075;mix-blend-mode:soft-light;background-image:url("data:image/svg+xml,%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.45'/%3E%3C/svg%3E")}
.vignette{background:linear-gradient(90deg,rgba(1,4,7,.38),transparent 52%,rgba(1,3,6,.2)),radial-gradient(ellipse at center,transparent 42%,rgba(0,2,5,.58) 100%)}
.fallback .grain{animation:drift 12s ease-in-out infinite alternate}@keyframes drift{to{transform:scale(1.06) translate3d(1.5%,-1%,0)}}
@media(prefers-reduced-motion:reduce){.fallback .grain{animation:none}}
</style>
