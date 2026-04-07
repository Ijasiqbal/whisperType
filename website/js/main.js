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

function initMobileNav() {
  const toggle = document.querySelector('.menu-toggle');
  const close = document.querySelector('.menu-close');
  const mobileNav = document.querySelector('.mobile-nav');
  const links = document.querySelectorAll('.mobile-nav-links a');

  if (!toggle || !mobileNav) return;

  const backdrop = document.querySelector('.mobile-nav-backdrop');

  let savedScrollY = 0;

  toggle.addEventListener('click', () => {
    savedScrollY = window.scrollY;
    document.body.style.position = 'fixed';
    document.body.style.top = `-${savedScrollY}px`;
    document.body.style.width = '100%';
    mobileNav.classList.add('open');
  });

  const closeMenu = () => {
    mobileNav.classList.remove('open');
    document.body.style.position = '';
    document.body.style.top = '';
    document.body.style.width = '';
    window.scrollTo(0, savedScrollY);
  };

  if (close) close.addEventListener('click', closeMenu);
  if (backdrop) backdrop.addEventListener('click', closeMenu);

  links.forEach(link => {
    link.addEventListener('click', closeMenu);
  });

  // Also close from CTA button
  const cta = mobileNav.querySelector('.mobile-nav-cta');
  if (cta) cta.addEventListener('click', closeMenu);
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
    current: 0,
    target: 0,
  }));

  // Wait for hero animations to finish before applying parallax to hero elements
  const HERO_ANIM_DONE_MS = 1800;
  let heroReady = false;
  setTimeout(() => { heroReady = true; }, HERO_ANIM_DONE_MS);

  const LERP_FACTOR = 0.08;
  let running = false;

  function tick() {
    const viewH = window.innerHeight;
    let needsUpdate = false;

    items.forEach(item => {
      const { el, speed, isHero } = item;

      if (!heroReady && isHero) return;
      if (el.classList.contains('reveal') && !el.classList.contains('visible')) return;

      const rect = el.getBoundingClientRect();
      const elCenterY = rect.top + rect.height / 2;
      const offset = (elCenterY - viewH / 2) / viewH;

      item.target = Math.max(-80, Math.min(80, offset * speed * viewH * 0.5));
      item.current += (item.target - item.current) * LERP_FACTOR;

      if (Math.abs(item.target - item.current) > 0.1) {
        needsUpdate = true;
      } else {
        item.current = item.target;
      }

      el.style.transform = `translate3d(0, ${item.current.toFixed(1)}px, 0)`;
    });

    if (needsUpdate) {
      requestAnimationFrame(tick);
    } else {
      running = false;
    }
  }

  function startLoop() {
    if (!running) {
      running = true;
      requestAnimationFrame(tick);
    }
  }

  window.addEventListener('scroll', startLoop, { passive: true });

  // Run initial update after hero animations complete
  setTimeout(startLoop, HERO_ANIM_DONE_MS);
}

// ===== GRADIENT BORDER (improvement #4) =====
function initGradientBorders() {
  const cards = document.querySelectorAll('.step-card, .feature-card');
  if (!cards.length) return;

  cards.forEach(card => {
    let rafId = null;
    let angle = Math.random() * 360; // each card starts at different angle

    function spin() {
      angle = (angle + 1.2) % 360;
      card.style.setProperty('--gba', angle + 'deg');
      rafId = requestAnimationFrame(spin);
    }

    card.addEventListener('mouseenter', () => {
      card.classList.add('gb-active');
      if (!rafId) spin();
    });

    card.addEventListener('mouseleave', () => {
      card.classList.remove('gb-active');
      if (rafId) { cancelAnimationFrame(rafId); rafId = null; }
    });
  });
}

// ===== PRICING TOGGLE =====
function setRegion(region) {
  document.querySelectorAll('.toggle-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.region === region);
  });
  document.querySelectorAll('.india-price').forEach(el => {
    el.classList.toggle('hidden', region !== 'india');
  });
  document.querySelectorAll('.intl-price').forEach(el => {
    el.classList.toggle('hidden', region !== 'intl');
  });
}

function initPricingToggle() {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  const isIndia = tz === 'Asia/Kolkata' || tz === 'Asia/Calcutta';
  setRegion(isIndia ? 'india' : 'intl');
}


// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
  initReveal();
  initNav();
  initMobileNav();
  initAccuracy();
  initBars();
  initParallax();
  initGradientBorders();
  initPricingToggle();
});
