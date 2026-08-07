import '@testing-library/jest-dom';

// Mock import.meta.env for tests
Object.defineProperty(import.meta, 'env', {
  value: {
    VITE_API_BASE_URL: 'http://localhost:8081',
    VITE_AUTH_MODE: 'OPEN_LAB',
  },
  writable: true,
  configurable: true,
});

// Suppress noisy React Router future flag warnings in tests
const originalWarn = console.warn;
console.warn = (...args: unknown[]) => {
  if (typeof args[0] === 'string' && args[0].includes('React Router Future Flag Warning')) return;
  originalWarn(...args);
};
