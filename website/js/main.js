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

// ===== ZOOM SECTION =====
function initZoom() {
  const section = document.querySelector('.zoom-section');
  const inner = document.querySelector('.zoom-inner');
  if (!section || !inner) return;

  window.addEventListener('scroll', () => {
    const rect = section.getBoundingClientRect();
    const scrollable = rect.height - window.innerHeight;
    const progress = Math.max(0, Math.min(1, -rect.top / scrollable));

    const scale = 0.82 + progress * 0.18;
    const opacity = 0.2 + progress * 0.8;

    inner.style.transform = `scale(${scale})`;
    inner.style.opacity = opacity;
  }, { passive: true });
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

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
  initReveal();
  initNav();
  initZoom();
  initBars();
});
