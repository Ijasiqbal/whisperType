// ===== (3D morph handled by morph.js) =====

// ===== SCROLL REVEAL =====
function initReveal() {
  const els = document.querySelectorAll('.reveal');
  if (!els.length) return;

  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) e.target.classList.add('visible');
    });
  }, { threshold: 0.12 });

  els.forEach(el => obs.observe(el));
}

// ===== NAV SCROLL =====
function initNav() {
  const nav = document.querySelector('nav');
  if (!nav) return;

  window.addEventListener('scroll', () => {
    nav.classList.toggle('scrolled', window.scrollY > 60);
  }, { passive: true });
}

// ===== ACCURACY SECTION =====
function initAccuracy() {
  const section = document.querySelector('.accuracy-section');
  if (!section) return;

  let counted = false;

  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        section.classList.add('in-view');
        if (!counted) {
          counted = true;
          animateCounters();
        }
      }
    });
  }, { threshold: 0.2 });

  obs.observe(section);
}

function animateCounters() {
  const counters = document.querySelectorAll('.stat-number[data-count]');
  counters.forEach(el => {
    const target = parseInt(el.dataset.count, 10);
    const prefix = el.dataset.prefix || '';
    const suffix = el.dataset.suffix || '';
    const duration = 1600;
    const startDelay = (parseFloat(el.dataset.delay) || 0) * 1000;

    setTimeout(() => {
      const start = performance.now();
      function tick(now) {
        const elapsed = now - start;
        const progress = Math.min(elapsed / duration, 1);
        // Ease out cubic
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.round(eased * target);
        el.textContent = prefix + current + suffix;
        if (progress < 1) requestAnimationFrame(tick);
      }
      requestAnimationFrame(tick);
    }, startDelay);
  });
}

// ===== COMPARISON BARS =====
function initBars() {
  const section = document.getElementById('compBars');
  if (!section) return;

  const obs = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        section.querySelectorAll('.bar-fill').forEach(bar => {
          bar.style.width = bar.dataset.w + '%';
        });
      }
    });
  }, { threshold: 0.25 });

  obs.observe(section);
}

// ===== PARALLAX =====
function initParallax() {
  // Disable on mobile — parallax feels janky on touch scroll
  if (window.innerWidth <= 640) return;

  const els = document.querySelectorAll('[data-parallax]');
  if (!els.length) return;

  // Cache speed values so we don't parseFloat on every scroll frame
  const items = Array.from(els).map(el => ({
    el,
    speed: parseFloat(el.dataset.parallax),
    isHero: !!el.closest('.hero'),
  }));

  // Wait for hero animations to finish before applying parallax to hero elements
  const HERO_ANIM_DONE_MS = 1800;
  let heroReady = false;
  setTimeout(() => { heroReady = true; }, HERO_ANIM_DONE_MS);

  let ticking = false;

  function update() {
    const viewH = window.innerHeight;

    items.forEach(({ el, speed, isHero }) => {
      // Skip hero elements until their entrance animation is done
      if (!heroReady && isHero) return;

      // Skip reveal elements that haven't become visible yet
      if (el.classList.contains('reveal') && !el.classList.contains('visible')) return;

      const rect = el.getBoundingClientRect();
      const elCenterY = rect.top + rect.height / 2;

      // How far the element center is from viewport center, normalized to -1..1
      const offset = (elCenterY - viewH / 2) / viewH;

      // Clamp max shift to 80px
      const shift = Math.max(-80, Math.min(80, offset * speed * viewH * 0.5));

      el.style.transform = `translate3d(0, ${shift.toFixed(1)}px, 0)`;
    });

    ticking = false;
  }

  window.addEventListener('scroll', () => {
    if (!ticking) {
      requestAnimationFrame(update);
      ticking = true;
    }
  }, { passive: true });

  // Run initial update after hero animations complete
  setTimeout(update, HERO_ANIM_DONE_MS);
}

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
  initReveal();
  initNav();
  initAccuracy();
  initBars();
  initParallax();
});
