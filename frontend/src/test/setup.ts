import '@testing-library/jest-dom/vitest';

// Radix components (menus, popovers) measure and focus their content in ways
// jsdom does not implement. These are the standard shims so component tests
// exercise real interaction code instead of crashing on a missing browser API.
if (!('ResizeObserver' in globalThis)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}

Element.prototype.scrollIntoView = () => {};

if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
