// ===== AMBIENT PARALLAX PARTICLES =====
// Scattered across the full page height, in depth layers that scroll at different speeds.

(function () {
  const canvas = document.getElementById('ambientParticles');
  if (!canvas) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  const ctx = canvas.getContext('2d');
  let W, docH;
  let particles = [];
  let scrollY = 0;
  let ticking = false;

  const isMobile = window.innerWidth <= 640;
  const COUNT = isMobile ? 40 : 80;

  const COLORS = [
    { r: 108, g: 92, b: 231 },  // purple
    { r: 0, g: 206, b: 201 },   // cyan
    { r: 200, g: 200, b: 220 }, // soft white
  ];

  // 3 depth layers: far (slow), mid, near (fast)
  const LAYERS = [
    { speed: 0.3, sizeRange: [1, 1.8], opacityRange: [0.04, 0.10], count: 0.45 },
    { speed: 0.6, sizeRange: [1.5, 2.5], opacityRange: [0.06, 0.14], count: 0.35 },
    { speed: 0.85, sizeRange: [2, 3.5], opacityRange: [0.08, 0.20], count: 0.20 },
  ];

  function resize() {
    W = window.innerWidth;
    docH = document.documentElement.scrollHeight;
    canvas.width = W;
    canvas.height = docH;
    canvas.style.height = docH + 'px';
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
          y: Math.random() * docH,
          r: rand(layer.sizeRange[0], layer.sizeRange[1]),
          opacity: rand(layer.opacityRange[0], layer.opacityRange[1]),
          color: color,
          speed: layer.speed,
          phase: Math.random() * Math.PI * 2,
          twinkleSpeed: 0.008 + Math.random() * 0.02,
        });
      }
    }
  }

  function draw() {
    ctx.clearRect(0, 0, W, docH);

    for (let i = 0; i < particles.length; i++) {
      const p = particles[i];

      // Parallax offset: each layer shifts by a different amount based on scroll
      // speed < 1 means it moves less than scroll = appears to lag behind (farther away)
      const parallaxShift = scrollY * (1 - p.speed);
      const drawY = p.y - parallaxShift;

      // Skip if off screen
      if (drawY < scrollY - 20 || drawY > scrollY + window.innerHeight + 20) continue;

      // Twinkle
      p.phase += p.twinkleSpeed;
      const flicker = 0.5 + 0.5 * Math.sin(p.phase);
      const alpha = p.opacity * flicker;

      ctx.beginPath();
      ctx.arc(p.x, drawY, p.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(${p.color.r}, ${p.color.g}, ${p.color.b}, ${alpha.toFixed(3)})`;
      ctx.fill();
    }

    ticking = false;
  }

  function onScroll() {
    scrollY = window.scrollY;
    if (!ticking) {
      requestAnimationFrame(draw);
      ticking = true;
    }
  }

  // Animate twinkle continuously
  function loop() {
    draw();
    requestAnimationFrame(loop);
  }

  // Debounced resize
  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      init();
      draw();
    }, 200);
  });

  window.addEventListener('scroll', onScroll, { passive: true });

  init();
  loop();
})();
