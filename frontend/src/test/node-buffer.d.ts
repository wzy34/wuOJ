// The app's TypeScript config has no Node types; test/polyfills.ts needs only this from Node.
declare module 'node:buffer' {
  export const File: typeof globalThis.File;
}
