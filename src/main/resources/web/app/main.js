import hljs from 'highlight.js';
import 'highlight.js/scss/github.scss';
import '@catppuccin/palette/css/catppuccin.css'
import mermaid from 'mermaid/dist/mermaid.esm.min.mjs';

hljs.highlightAll();
mermaid.initialize({ startOnLoad: true });

// Dark Mode Toggle
function applyTheme(theme) {
  const isDark = theme === 'dark';
  document.documentElement.setAttribute('data-theme', theme);

  // The Roq theme's Tailwind styles are keyed on a `dark` class on <html>, which its
  // head.html partial sets from prefers-color-scheme / its own `darkMode` key. Without
  // syncing, a visitor whose OS is dark gets the theme's dark colors (e.g. white prose
  // links) on top of our light palette. Writing `darkMode` also keeps the theme's
  // pre-paint script in agreement on the next page load.
  document.documentElement.classList.toggle('dark', isDark);
  localStorage.setItem('darkMode', String(isDark));
}

function initTheme() {
  applyTheme(localStorage.getItem('theme') || 'light');
}

function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute('data-theme');
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

  applyTheme(newTheme);
  localStorage.setItem('theme', newTheme);
}

// Initialize theme before DOMContentLoaded to prevent flash
initTheme();

document.addEventListener('DOMContentLoaded', () => {
  // Theme toggle button
  const themeToggle = document.querySelector('.theme-toggle');
  if (themeToggle) {
    themeToggle.addEventListener('click', toggleTheme);
  }

  // Mobile menu toggle
  const mobileMenuToggle = document.querySelector('.mobile-menu-toggle');
  const topNav = document.querySelector('.top-nav');

  if (mobileMenuToggle && topNav) {
    mobileMenuToggle.addEventListener('click', () => {
      const isOpen = topNav.classList.toggle('mobile-menu-open');
      mobileMenuToggle.setAttribute('aria-expanded', isOpen);
    });

    // Close menu when clicking outside
    document.addEventListener('click', (e) => {
      if (!topNav.contains(e.target) && topNav.classList.contains('mobile-menu-open')) {
        topNav.classList.remove('mobile-menu-open');
        mobileMenuToggle.setAttribute('aria-expanded', 'false');
      }
    });

    // Close menu on escape key
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && topNav.classList.contains('mobile-menu-open')) {
        topNav.classList.remove('mobile-menu-open');
        mobileMenuToggle.setAttribute('aria-expanded', 'false');
      }
    });
  }
});
