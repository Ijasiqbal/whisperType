// ===== PARTICLE STREAM — Flowing river of glowing particles =====

(function () {
  const canvas = document.getElementById('blobCanvas');
  if (!canvas) return;

  const isMobile = window.innerWidth <= 640;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(60, canvas.clientWidth / canvas.clientHeight, 0.1, 200);
  camera.position.set(0, 0, isMobile ? 14 : 11);

  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: false });
  renderer.setSize(canvas.clientWidth, canvas.clientHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));

  const purple = new THREE.Color('#6c5ce7');
  const cyan = new THREE.Color('#00cec9');

  // ----- STREAM CURVE -----
  // S-curve path across the screen
  const curvePoints = [
    new THREE.Vector3(-16, -4, 0),
    new THREE.Vector3(-8, 3, -2),
    new THREE.Vector3(-2, -1, 1),
    new THREE.Vector3(4, 4, -1),
    new THREE.Vector3(10, -2, 2),
    new THREE.Vector3(18, 3, 0),
  ];
  const curve = new THREE.CatmullRomCurve3(curvePoints);

  // ----- STREAM PARTICLES -----
  const COUNT = isMobile ? 1500 : 3000;
  const geo = new THREE.BufferGeometry();
  const positions = new Float32Array(COUNT * 3);
  const colors = new Float32Array(COUNT * 3);
  const sizes = new Float32Array(COUNT);

  // Per-particle: position along curve (0-1), offset from center, speed
  const curveT = new Float32Array(COUNT);
  const offsetX = new Float32Array(COUNT);
  const offsetY = new Float32Array(COUNT);
  const speed = new Float32Array(COUNT);

  for (let i = 0; i < COUNT; i++) {
    curveT[i] = Math.random();
    // Spread particles around the curve with gaussian-ish distribution
    const spread = isMobile ? 0.8 : 1.0;
    offsetX[i] = (Math.random() - 0.5) * spread * (0.3 + Math.random());
    offsetY[i] = (Math.random() - 0.5) * spread * (0.3 + Math.random());
    speed[i] = 0.03 + Math.random() * 0.06;

    // Color: gradient along the curve
    const t = curveT[i];
    const c = new THREE.Color().lerpColors(purple, cyan, t);
    // Add some randomness
    c.r += (Math.random() - 0.5) * 0.08;
    c.g += (Math.random() - 0.5) * 0.08;
    c.b += (Math.random() - 0.5) * 0.08;
    colors[i * 3] = c.r;
    colors[i * 3 + 1] = c.g;
    colors[i * 3 + 2] = c.b;

    // Brighter particles near center, dimmer at edges
    const distFromCenter = Math.sqrt(offsetX[i] * offsetX[i] + offsetY[i] * offsetY[i]);
    sizes[i] = Math.max(0.3, (isMobile ? 1.8 : 1.3) - distFromCenter * 1.5) + Math.random() * 0.4;
  }

  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  geo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

  const material = new THREE.ShaderMaterial({
    uniforms: {
      uPixelRatio: { value: renderer.getPixelRatio() },
    },
    vertexShader: `
      attribute float size;
      varying vec3 vColor;
      varying float vAlpha;
      uniform float uPixelRatio;
      void main() {
        vColor = color;
        vec4 mvPos = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = size * uPixelRatio * (60.0 / -mvPos.z);
        gl_Position = projectionMatrix * mvPos;

        // Fade at edges of view
        float edgeFade = 1.0 - smoothstep(8.0, 14.0, abs(position.x));
        vAlpha = edgeFade;
      }
    `,
    fragmentShader: `
      varying vec3 vColor;
      varying float vAlpha;
      void main() {
        float d = length(gl_PointCoord - vec2(0.5));
        if(d > 0.5) discard;
        float glow = 1.0 - smoothstep(0.0, 0.5, d);
        glow = pow(glow, 1.8);
        gl_FragColor = vec4(vColor, glow * 0.75 * vAlpha);
      }
    `,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    vertexColors: true,
  });

  const stream = new THREE.Points(geo, material);
  scene.add(stream);

  // ----- SECOND STREAM (thinner, faster, brighter) -----
  const COUNT2 = isMobile ? 400 : 800;
  const geo2 = new THREE.BufferGeometry();
  const pos2 = new Float32Array(COUNT2 * 3);
  const col2 = new Float32Array(COUNT2 * 3);
  const sizes2 = new Float32Array(COUNT2);
  const curveT2 = new Float32Array(COUNT2);
  const offX2 = new Float32Array(COUNT2);
  const offY2 = new Float32Array(COUNT2);
  const speed2 = new Float32Array(COUNT2);

  for (let i = 0; i < COUNT2; i++) {
    curveT2[i] = Math.random();
    offX2[i] = (Math.random() - 0.5) * 0.25;
    offY2[i] = (Math.random() - 0.5) * 0.25;
    speed2[i] = 0.06 + Math.random() * 0.08;

    const c = new THREE.Color().lerpColors(cyan, new THREE.Color('#ffffff'), 0.3 + Math.random() * 0.3);
    col2[i * 3] = c.r;
    col2[i * 3 + 1] = c.g;
    col2[i * 3 + 2] = c.b;
    sizes2[i] = (isMobile ? 1.2 : 0.8) + Math.random() * 0.6;
  }

  geo2.setAttribute('position', new THREE.BufferAttribute(pos2, 3));
  geo2.setAttribute('color', new THREE.BufferAttribute(col2, 3));
  geo2.setAttribute('size', new THREE.BufferAttribute(sizes2, 1));

  const mat2 = new THREE.ShaderMaterial({
    uniforms: { uPixelRatio: { value: renderer.getPixelRatio() } },
    vertexShader: material.vertexShader,
    fragmentShader: material.fragmentShader,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    vertexColors: true,
  });

  const stream2 = new THREE.Points(geo2, mat2);
  scene.add(stream2);

  // ----- Helpers -----
  // Get position on curve with offset
  const _tangent = new THREE.Vector3();
  const _normal = new THREE.Vector3();
  const _binormal = new THREE.Vector3();
  const _up = new THREE.Vector3(0, 1, 0);

  function getStreamPos(t, offx, offy, out) {
    curve.getPointAt(t, out);
    curve.getTangentAt(t, _tangent);
    _normal.crossVectors(_tangent, _up).normalize();
    _binormal.crossVectors(_tangent, _normal).normalize();
    out.x += _normal.x * offx + _binormal.x * offy;
    out.y += _normal.y * offx + _binormal.y * offy;
    out.z += _normal.z * offx + _binormal.z * offy;
  }

  // ----- SCROLL -----
  let scrollY = 0;
  window.addEventListener('scroll', () => { scrollY = window.scrollY; }, { passive: true });

  // ----- ANIMATE -----
  const clock = new THREE.Clock();
  const tmpVec = new THREE.Vector3();

  function animate() {
    requestAnimationFrame(animate);
    const dt = Math.min(clock.getDelta(), 0.05);
    const t = clock.getElapsedTime();

    const scrollBoost = 1 + Math.min(scrollY / window.innerHeight, 0.6);

    // Main stream
    const pos = geo.attributes.position.array;
    for (let i = 0; i < COUNT; i++) {
      curveT[i] += speed[i] * dt * scrollBoost;
      if (curveT[i] > 1) curveT[i] -= 1;

      // Add gentle wave to offset
      const waveX = Math.sin(t * 1.5 + curveT[i] * 8) * 0.12;
      const waveY = Math.cos(t * 1.2 + curveT[i] * 6) * 0.1;

      getStreamPos(curveT[i], offsetX[i] + waveX, offsetY[i] + waveY, tmpVec);
      pos[i * 3] = tmpVec.x;
      pos[i * 3 + 1] = tmpVec.y;
      pos[i * 3 + 2] = tmpVec.z;
    }
    geo.attributes.position.needsUpdate = true;

    // Core stream
    const p2 = geo2.attributes.position.array;
    for (let i = 0; i < COUNT2; i++) {
      curveT2[i] += speed2[i] * dt * scrollBoost;
      if (curveT2[i] > 1) curveT2[i] -= 1;

      getStreamPos(curveT2[i], offX2[i], offY2[i], tmpVec);
      p2[i * 3] = tmpVec.x;
      p2[i * 3 + 1] = tmpVec.y;
      p2[i * 3 + 2] = tmpVec.z;
    }
    geo2.attributes.position.needsUpdate = true;

    // Subtle camera breathing
    camera.position.y = Math.sin(t * 0.2) * 0.3;

    renderer.render(scene, camera);
  }

  animate();

  // ----- RESIZE -----
  let rTimer;
  window.addEventListener('resize', () => {
    clearTimeout(rTimer);
    rTimer = setTimeout(() => {
      camera.aspect = canvas.clientWidth / canvas.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(canvas.clientWidth, canvas.clientHeight);
      const pr = renderer.getPixelRatio();
      material.uniforms.uPixelRatio.value = pr;
      mat2.uniforms.uPixelRatio.value = pr;
    }, 150);
  });
})();
