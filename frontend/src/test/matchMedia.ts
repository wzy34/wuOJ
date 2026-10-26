/** A controllable `window.matchMedia` for jsdom, which has none. */
type Listener = (event: MediaQueryListEvent) => void;

let systemDark = false;
let viewportWidth = 1280;
const listeners = new Set<{ query: string; listener: Listener }>();

function matches(query: string): boolean {
  if (query.includes('prefers-color-scheme: dark')) return systemDark;
  const min = /min-width:\s*(\d+)px/.exec(query);
  if (min) return viewportWidth >= Number(min[1]);
  const max = /max-width:\s*(\d+)px/.exec(query);
  if (max) return viewportWidth <= Number(max[1]);
  return false;
}

export function installMatchMedia(): void {
  window.matchMedia = (query: string) => {
    const entries = new Map<Listener, { query: string; listener: Listener }>();
    const list: MediaQueryList = {
      get matches() {
        return matches(query);
      },
      media: query,
      onchange: null,
      addEventListener: (_type: string, listener: Listener) => {
        const entry = { query, listener };
        entries.set(listener, entry);
        listeners.add(entry);
      },
      removeEventListener: (_type: string, listener: Listener) => {
        const entry = entries.get(listener);
        if (entry) listeners.delete(entry);
      },
      addListener: (listener: Listener) => list.addEventListener('change', listener),
      removeListener: (listener: Listener) => list.removeEventListener('change', listener),
      dispatchEvent: () => false,
    } as MediaQueryList;
    return list;
  };
}

/** Simulates the operating system switching between light and dark mode. */
export function setSystemDark(dark: boolean): void {
  systemDark = dark;
  for (const { query, listener } of [...listeners]) {
    if (query.includes('prefers-color-scheme')) {
      listener({ matches: dark, media: query } as MediaQueryListEvent);
    }
  }
}

/** Simulates the window width for width media queries (1280 px by default). */
export function setViewportWidth(width: number): void {
  viewportWidth = width;
  for (const { query, listener } of [...listeners]) {
    if (query.includes('width'))
      listener({ matches: matches(query), media: query } as MediaQueryListEvent);
  }
}
