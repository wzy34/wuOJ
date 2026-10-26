interface ImportMetaEnv {
  /** "true" in mock mode (`pnpm dev:mock`). */
  readonly VITE_API_MOCK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
