// ===== SCENE3D — Full-Page WebGL Parallax Background =====
// 4-layer depth field: stars, mid-field dust, near orbs, floating rings.
// Camera Z scrolls with page scroll to create true depth parallax.

(function () {
  'use strict';

  const canvas = document.getElementById('scene3dCanvas');
  if (!canvas) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  const isMobile = window.innerWidth <= 768;

  // ──────────────────────────────────────────────
  // RENDERER
  // ──────────────────────────────────────────────
  const renderer = new THREE.WebGLRenderer({
    canvas,
    alpha: true,
    antialias: false,
    powerPreference: 'low-power',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setClearColor(0x000000, 0);

  // ──────────────────────────────────────────────
  // SCENE + CAMERA
  // ──────────────────────────────────────────────
  const scene = new THREE.Scene();

  // Wide FOV so depth feels dramatic
  const camera = new THREE.PerspectiveCamera(
    75,
    window.innerWidth / window.innerHeight,
    0.1,
    800
  );
  camera.position.set(0, 0, 0);

  // ──────────────────────────────────────────────
  // COLOUR PALETTE (brand rust/orange tones)
  // ──────────────────────────────────────────────
  const BRAND_COLORS = [
    new THREE.Color('#c45d3e'),  // rust
    new THREE.Color('#d4845a'),  // rust light
    new THREE.Color('#e8a07a'),  // peach
    new THREE.Color('#facdb4'),  // blush
    new THREE.Color('#8b3a22'),  // rust deep
    new THREE.Color('#ffffff'),  // white accent
  ];

  function brandColor(bias) {
    // bias 0-1: 0 = darker, 1 = lighter
    const idx = Math.floor(bias * (BRAND_COLORS.length - 1));
    return BRAND_COLORS[Math.min(idx, BRAND_COLORS.length - 1)].clone();
  }

  // ──────────────────────────────────────────────
  // SHARED ROUND-SPRITE SHADER
  // ──────────────────────────────────────────────
  const spriteVert = `
    attribute float size;
    attribute float alpha;
    varying float vAlpha;
    varying vec3 vColor;
    uniform float uPR;

    void main() {
      vAlpha = alpha;
      vColor = color;
      vec4 mv = modelViewMatrix * vec4(position, 1.0);
      gl_PointSize = size * uPR * (300.0 / -mv.z);
      gl_Position = projectionMatrix * mv;
    }
  `;

  const spriteFrag = `
    varying float vAlpha;
    varying vec3 vColor;

    void main() {
      float d = length(gl_PointCoord - vec2(0.5));
      if (d > 0.5) discard;
      float glow = 1.0 - smoothstep(0.0, 0.5, d);
      glow = pow(glow, 1.6);
      gl_FragColor = vec4(vColor, glow * vAlpha);
    }
  `;

  function makeSpriteMaterial(pr) {
    return new THREE.ShaderMaterial({
      uniforms: { uPR: { value: pr } },
      vertexShader: spriteVert,
      fragmentShader: spriteFrag,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      vertexColors: true,
    });
  }

  const PR = renderer.getPixelRatio();

  // ──────────────────────────────────────────────
  // LAYER 1 — DEEP STARFIELD (z: -100 to -400)
  // Far away, very slow parallax factor
  // ──────────────────────────────────────────────
  const STAR_COUNT = isMobile ? 600 : 1400;
  const starGeo = new THREE.BufferGeometry();
  const starPos = new Float32Array(STAR_COUNT * 3);
  const starCol = new Float32Array(STAR_COUNT * 3);
  const starSize = new Float32Array(STAR_COUNT);
  const starAlpha = new Float32Array(STAR_COUNT);
  const starPhase = new Float32Array(STAR_COUNT); // for twinkle

  for (let i = 0; i < STAR_COUNT; i++) {
    const spread = isMobile ? 80 : 130;
    starPos[i * 3]     = (Math.random() - 0.5) * spread * 2;
    starPos[i * 3 + 1] = (Math.random() - 0.5) * 120; // vertical scatter
    starPos[i * 3 + 2] = -120 - Math.random() * 280;  // deep z-range

    const c = brandColor(Math.random());
    // Desaturate distant stars slightly
    c.lerp(new THREE.Color(1, 1, 1), 0.3);
    starCol[i * 3]     = c.r;
    starCol[i * 3 + 1] = c.g;
    starCol[i * 3 + 2] = c.b;

    starSize[i] = 0.6 + Math.random() * 1.6;
    starAlpha[i] = 0.25 + Math.random() * 0.55;
    starPhase[i] = Math.random() * Math.PI * 2;
  }

  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
  starGeo.setAttribute('color',    new THREE.BufferAttribute(starCol, 3));
  starGeo.setAttribute('size',     new THREE.BufferAttribute(starSize, 1));
  starGeo.setAttribute('alpha',    new THREE.BufferAttribute(starAlpha, 1));

  const starMat = makeSpriteMaterial(PR);
  const starField = new THREE.Points(starGeo, starMat);
  scene.add(starField);

  // ──────────────────────────────────────────────
  // LAYER 2 — MID DUST CLOUD (z: -40 to -100)
  // Medium depth, moderate parallax
  // ──────────────────────────────────────────────
  const DUST_COUNT = isMobile ? 250 : 600;
  const dustGeo = new THREE.BufferGeometry();
  const dustPos = new Float32Array(DUST_COUNT * 3);
  const dustCol = new Float32Array(DUST_COUNT * 3);
  const dustSize = new Float32Array(DUST_COUNT);
  const dustAlpha = new Float32Array(DUST_COUNT);
  const dustPhase = new Float32Array(DUST_COUNT);
  const dustDrift = new Float32Array(DUST_COUNT * 2); // slow Y drift

  for (let i = 0; i < DUST_COUNT; i++) {
    const spread = isMobile ? 50 : 90;
    dustPos[i * 3]     = (Math.random() - 0.5) * spread * 2;
    dustPos[i * 3 + 1] = (Math.random() - 0.5) * 80;
    dustPos[i * 3 + 2] = -40 - Math.random() * 60;

    const c = brandColor(0.3 + Math.random() * 0.5);
    dustCol[i * 3]     = c.r;
    dustCol[i * 3 + 1] = c.g;
    dustCol[i * 3 + 2] = c.b;

    dustSize[i] = 2 + Math.random() * 5;
    dustAlpha[i] = 0.06 + Math.random() * 0.14;
    dustPhase[i] = Math.random() * Math.PI * 2;
    dustDrift[i * 2]     = (Math.random() - 0.5) * 0.004; // x drift
    dustDrift[i * 2 + 1] = (Math.random() - 0.5) * 0.003; // y drift
  }

  dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
  dustGeo.setAttribute('color',    new THREE.BufferAttribute(dustCol, 3));
  dustGeo.setAttribute('size',     new THREE.BufferAttribute(dustSize, 1));
  dustGeo.setAttribute('alpha',    new THREE.BufferAttribute(dustAlpha, 1));

  const dustMat = makeSpriteMaterial(PR);
  const dustField = new THREE.Points(dustGeo, dustMat);
  scene.add(dustField);

  // ──────────────────────────────────────────────
  // LAYER 3 — GLOWING ORBS
  // Attached to a pivot that follows the camera Z,
  // so they always stay at a fixed depth ahead —
  // no more "zooming through" them on scroll.
  // ──────────────────────────────────────────────
  const ORB_COUNT = isMobile ? 14 : 26;
  const orbGeo = new THREE.BufferGeometry();
  const orbPos = new Float32Array(ORB_COUNT * 3);
  const orbCol = new Float32Array(ORB_COUNT * 3);
  const orbSize = new Float32Array(ORB_COUNT);
  const orbAlpha = new Float32Array(ORB_COUNT);
  const orbPhase = new Float32Array(ORB_COUNT);
  const orbBaseY = new Float32Array(ORB_COUNT);
  // Each orb has a fixed depth offset from the pivot (–15 to –35)
  const orbDepthOffset = new Float32Array(ORB_COUNT);

  for (let i = 0; i < ORB_COUNT; i++) {
    const spread = isMobile ? 28 : 50;
    orbPos[i * 3]     = (Math.random() - 0.5) * spread * 2;
    const y = (Math.random() - 0.5) * 55;
    orbPos[i * 3 + 1] = y;
    orbBaseY[i] = y;
    orbDepthOffset[i] = -15 - Math.random() * 20; // stays -15 to -35 ahead
    orbPos[i * 3 + 2] = orbDepthOffset[i];         // set initially

    const c = brandColor(Math.random());
    orbCol[i * 3]     = c.r;
    orbCol[i * 3 + 1] = c.g;
    orbCol[i * 3 + 2] = c.b;

    // Smaller than before so they don't overwhelm at any scroll depth
    orbSize[i] = (isMobile ? 8 : 14) + Math.random() * (isMobile ? 14 : 22);
    orbAlpha[i] = 0.07 + Math.random() * 0.13;
    orbPhase[i] = Math.random() * Math.PI * 2;
  }

  orbGeo.setAttribute('position', new THREE.BufferAttribute(orbPos, 3));
  orbGeo.setAttribute('color',    new THREE.BufferAttribute(orbCol, 3));
  orbGeo.setAttribute('size',     new THREE.BufferAttribute(orbSize, 1));
  orbGeo.setAttribute('alpha',    new THREE.BufferAttribute(orbAlpha, 1));

  const orbMat = makeSpriteMaterial(PR);
  const orbField = new THREE.Points(orbGeo, orbMat);
  scene.add(orbField);

  // ──────────────────────────────────────────────
  // LAYER 4 — FOREGROUND MICRO SPARKS
  // Also camera-relative so they never disappear
  // behind the camera as user scrolls
  // ──────────────────────────────────────────────
  const SPARK_COUNT = isMobile ? 35 : 75;
  const sparkGeo = new THREE.BufferGeometry();
  const sparkPos = new Float32Array(SPARK_COUNT * 3);
  const sparkCol = new Float32Array(SPARK_COUNT * 3);
  const sparkSize = new Float32Array(SPARK_COUNT);
  const sparkAlpha = new Float32Array(SPARK_COUNT);
  const sparkPhase = new Float32Array(SPARK_COUNT);
  const sparkDepthOffset = new Float32Array(SPARK_COUNT);

  for (let i = 0; i < SPARK_COUNT; i++) {
    const spread = isMobile ? 16 : 32;
    sparkPos[i * 3]     = (Math.random() - 0.5) * spread * 2;
    sparkPos[i * 3 + 1] = (Math.random() - 0.5) * 28;
    sparkDepthOffset[i] = -5 - Math.random() * 8; // -5 to -13 ahead of cam
    sparkPos[i * 3 + 2] = sparkDepthOffset[i];

    const bright = new THREE.Color('#facdb4');
    bright.lerp(brandColor(Math.random()), 0.3);
    sparkCol[i * 3]     = bright.r;
    sparkCol[i * 3 + 1] = bright.g;
    sparkCol[i * 3 + 2] = bright.b;

    sparkSize[i] = 0.4 + Math.random() * 1.0;
    sparkAlpha[i] = 0.5 + Math.random() * 0.3;
    sparkPhase[i] = Math.random() * Math.PI * 2;
  }

  sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
  sparkGeo.setAttribute('color',    new THREE.BufferAttribute(sparkCol, 3));
  sparkGeo.setAttribute('size',     new THREE.BufferAttribute(sparkSize, 1));
  sparkGeo.setAttribute('alpha',    new THREE.BufferAttribute(sparkAlpha, 1));

  const sparkMat = makeSpriteMaterial(PR);
  const sparkField = new THREE.Points(sparkGeo, sparkMat);
  scene.add(sparkField);

  // ──────────────────────────────────────────────
  // SCROLL STATE
  // Each layer's parent object is offset in world-Z.
  // On scroll, we move the camera forward in Z (into the scene).
  // Because each layer lives at different Z depths, they appear
  // to pass at dramatically different speeds — true 3D parallax.
  // ──────────────────────────────────────────────
  let scrollY = 0;
  let smoothScrollY = 0;
  // Camera travels 30 units along Z across the full page.
  // Stars/dust are at -120 to -400 so they barely move (good).
  // Orbs/sparks now track camera Z, so they never get "passed".
  const SCROLL_DEPTH = 30;

  window.addEventListener('scroll', () => {
    scrollY = window.scrollY;
  }, { passive: true });

  function getScrollProgress() {
    const docH = document.documentElement.scrollHeight - window.innerHeight;
    return docH > 0 ? scrollY / docH : 0;
  }

  // ──────────────────────────────────────────────
  // ANIMATION LOOP
  // ──────────────────────────────────────────────
  const clock = new THREE.Clock();
  let frameId;

  function animate() {
    frameId = requestAnimationFrame(animate);
    const t = clock.getElapsedTime();
    const dt = Math.min(clock.getDelta(), 0.05);

    // Smooth scroll lerp
    smoothScrollY += (scrollY - smoothScrollY) * 0.06;
    const scrollProg = getScrollProgress();

    // ── Move camera through Z (core parallax mechanism) ──
    camera.position.z = scrollProg * SCROLL_DEPTH;

    // ── Subtle Y drift of camera (breathing feel) ──
    camera.position.y = Math.sin(t * 0.18) * 0.8;
    camera.position.x = Math.sin(t * 0.11) * 0.4;

    // ── Layer 1 (stars): rotate very slowly ──
    starField.rotation.y = t * 0.004;
    starField.rotation.x = Math.sin(t * 0.003) * 0.02;

    // Twinkle: update alpha buffer
    const sAlpha = starGeo.attributes.alpha.array;
    for (let i = 0; i < STAR_COUNT; i++) {
      starPhase[i] += 0.008 + Math.random() * 0.003;
      sAlpha[i] = (0.25 + Math.random() * 0.55) * (0.6 + 0.4 * Math.sin(starPhase[i]));
    }
    starGeo.attributes.alpha.needsUpdate = true;

    // ── Layer 2 (dust): slow drift + rotation ──
    const dPos = dustGeo.attributes.position.array;
    const dAlpha = dustGeo.attributes.alpha.array;
    for (let i = 0; i < DUST_COUNT; i++) {
      // Drift
      dPos[i * 3]     += dustDrift[i * 2];
      dPos[i * 3 + 1] += dustDrift[i * 2 + 1];

      // Wrap within bounds
      if (dPos[i * 3] >  90) dPos[i * 3] = -90;
      if (dPos[i * 3] < -90) dPos[i * 3] =  90;
      if (dPos[i * 3 + 1] >  80) dPos[i * 3 + 1] = -80;
      if (dPos[i * 3 + 1] < -80) dPos[i * 3 + 1] =  80;

      dustPhase[i] += 0.006;
      dAlpha[i] = (0.06 + Math.random() * 0.08) * (0.7 + 0.3 * Math.sin(dustPhase[i]));
    }
    dustGeo.attributes.position.needsUpdate = true;
    dustGeo.attributes.alpha.needsUpdate = true;

    // ── Layer 3 (orbs): follow camera Z + float ──
    // By setting Z = camera.position.z + offset, they stay
    // a fixed distance ahead — no more zoom-through oddness.
    const oPos = orbGeo.attributes.position.array;
    const oAlpha = orbGeo.attributes.alpha.array;
    for (let i = 0; i < ORB_COUNT; i++) {
      orbPhase[i] += 0.004 + i * 0.0003;
      oPos[i * 3 + 1] = orbBaseY[i] + Math.sin(orbPhase[i]) * 3.5;
      oPos[i * 3]    += Math.sin(t * 0.25 + i) * 0.004; // subtle X sway
      // Z tracks camera with fixed depth offset
      oPos[i * 3 + 2] = camera.position.z + orbDepthOffset[i];
      // Breathe alpha
      oAlpha[i] = (0.07 + Math.random() * 0.04) * (0.75 + 0.25 * Math.sin(orbPhase[i] * 1.3));
    }
    orbGeo.attributes.position.needsUpdate = true;
    orbGeo.attributes.alpha.needsUpdate = true;

    // ── Layer 4 (sparks): camera-relative + flicker ──
    const spPos = sparkGeo.attributes.position.array;
    const spAlpha = sparkGeo.attributes.alpha.array;
    for (let i = 0; i < SPARK_COUNT; i++) {
      spPos[i * 3 + 2] = camera.position.z + sparkDepthOffset[i];
      sparkPhase[i] += 0.03 + Math.random() * 0.02;
      spAlpha[i] = (0.45 + 0.35 * Math.sin(sparkPhase[i])) * (0.6 + Math.random() * 0.35);
    }
    sparkGeo.attributes.position.needsUpdate = true;
    sparkGeo.attributes.alpha.needsUpdate = true;

    renderer.render(scene, camera);
  }

  animate();

  // ──────────────────────────────────────────────
  // RESIZE
  // ──────────────────────────────────────────────
  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      const W = window.innerWidth;
      const H = window.innerHeight;
      camera.aspect = W / H;
      camera.updateProjectionMatrix();
      renderer.setSize(W, H);
      const pr = renderer.getPixelRatio();
      starMat.uniforms.uPR.value = pr;
      dustMat.uniforms.uPR.value = pr;
      orbMat.uniforms.uPR.value = pr;
      sparkMat.uniforms.uPR.value = pr;
    }, 150);
  });

  // Cleanup on page hide to free GPU memory
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      cancelAnimationFrame(frameId);
    } else {
      clock.getDelta(); // reset delta after pause
      animate();
    }
  });
})();
