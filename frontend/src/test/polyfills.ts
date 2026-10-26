import { EventSource } from 'eventsource';
import { installMatchMedia } from './matchMedia';

// jsdom has no EventSource; MSW's sse() handlers and the live-verdict hook need one.
// This file runs before setup.ts so the global exists when the mock handlers are created.
globalThis.EventSource ??= EventSource as unknown as typeof globalThis.EventSource;

installMatchMedia();

// Arco components measure themselves; jsdom has no ResizeObserver.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
