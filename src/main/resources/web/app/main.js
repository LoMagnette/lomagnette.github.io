import hljs from 'highlight.js';
import 'highlight.js/scss/github.scss';
import '@catppuccin/palette/css/catppuccin.css'
import mermaid from 'mermaid/dist/mermaid.esm.min.mjs';

hljs.highlightAll();
mermaid.initialize({ startOnLoad: true });

// Dark Mode Toggle
function initTheme() {
  const savedTheme = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', savedTheme);
}

function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute('data-theme');
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

  document.documentElement.setAttribute('data-theme', newTheme);
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
