// ===== AMBIENT PARALLAX PARTICLES =====
// Viewport-sized canvas repositioned via CSS — avoids painting a full-page canvas.

(function () {
  const canvas = document.getElementById('ambientParticles');
  if (!canvas) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  const ctx = canvas.getContext('2d');
  let W, VH, docH;
  let particles = [];
  let scrollY = 0;
  let rafId = null;
  let hidden = false;

  const isMobile = window.innerWidth <= 768;
  // Fewer particles on mobile — viewport-only rendering means less overdraw
  const COUNT = isMobile ? 25 : 80;
  // Slow down twinkle on mobile to reduce per-frame CPU work
  const TWINKLE_SCALE = isMobile ? 0.4 : 1;

  const COLORS = [
    { r: 196, g: 93, b: 62 },  // Rust
    { r: 212, g: 132, b: 90 }, // RustLight
    { r: 139, g: 58, b: 34 }, // RustDeep
  ];

  // 3 depth layers: far (slow), mid, near (fast)
  const LAYERS = [
    { speed: 0.3, sizeRange: [1, 1.8], opacityRange: [0.04, 0.10], count: 0.45 },
    { speed: 0.6, sizeRange: [1.5, 2.5], opacityRange: [0.06, 0.14], count: 0.35 },
    { speed: 0.85, sizeRange: [2, 3.5], opacityRange: [0.08, 0.20], count: 0.20 },
  ];

  function resize() {
    W = window.innerWidth;
    VH = window.innerHeight;
    docH = document.documentElement.scrollHeight;
    // Canvas is only viewport-sized — much cheaper to paint
    canvas.width = W;
    canvas.height = VH;
    canvas.style.width = W + 'px';
    canvas.style.height = VH + 'px';
  }

  function rand(min, max) {
    return min + Math.random() * (max - min);
  }

  function init() {
    resize();
    particles = [];

    for (let li = 0; li < LAYERS.length; li++) {
      const layer = LAYERS[li];
      const n = Math.round(COUNT * layer.count);

      for (let i = 0; i < n; i++) {
        const color = COLORS[Math.floor(Math.random() * COLORS.length)];
        particles.push({
          x: Math.random() * W,
          // y is in document space (0..docH)
          y: Math.random() * docH,
          r: rand(layer.sizeRange[0], layer.sizeRange[1]),
          opacity: rand(layer.opacityRange[0], layer.opacityRange[1]),
          color,
          speed: layer.speed,
          phase: Math.random() * Math.PI * 2,
          twinkleSpeed: (0.008 + Math.random() * 0.02) * TWINKLE_SCALE,
        });
      }
    }
  }

  function draw() {
    ctx.clearRect(0, 0, W, VH);

    for (let i = 0; i < particles.length; i++) {
      const p = particles[i];

      // Parallax: layer moves at `speed` relative to scroll
      const parallaxShift = scrollY * (1 - p.speed);
      // Convert document-space y to viewport-space
      const drawY = p.y - scrollY - parallaxShift;

      // Skip particles outside the viewport
      if (drawY < -20 || drawY > VH + 20) continue;

      // Twinkle
      p.phase += p.twinkleSpeed;
      const flicker = 0.5 + 0.5 * Math.sin(p.phase);
      const alpha = p.opacity * flicker;

      ctx.beginPath();
      ctx.arc(p.x, drawY, p.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(${p.color.r}, ${p.color.g}, ${p.color.b}, ${alpha.toFixed(3)})`;
      ctx.fill();
    }
  }

  const FRAME_INTERVAL = 1000 / 30; // ~30fps is plenty for subtle twinkle
  let lastFrame = 0;

  function loop(now) {
    rafId = requestAnimationFrame(loop);
    if (now - lastFrame < FRAME_INTERVAL) return;
    lastFrame = now;
    scrollY = window.scrollY;
    draw();
  }

  function start() {
    if (!rafId) rafId = requestAnimationFrame(loop);
  }

  function stop() {
    if (rafId) { cancelAnimationFrame(rafId); rafId = null; }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) stop(); else start();
  });

  // Debounced resize
  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      init();
    }, 200);
  });

  init();
  start();
})();
