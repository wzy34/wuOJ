import { File as NodeFile } from 'node:buffer';
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

// Requests in tests go through Node's fetch, which can't read jsdom's FormData and File: uploads would
// arrive empty. Node's own classes work with both fetch and the app's file handling (`file.text()`).
// Node's FormData isn't importable, so it is taken from a parsed form. jsdom's Blob stays, since
// Vitest's jsdom request support relies on it; tests and app code build uploads as Files.
const nodeForm = await new Response('', {
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
}).formData();
globalThis.FormData = nodeForm.constructor as typeof globalThis.FormData;
globalThis.File = NodeFile;
