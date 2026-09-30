/// <reference types="vite/client" />

/**
 * Environment variables the SPA reads. Typing them here means a typo in
 * `import.meta.env.VITE_...` is a compile error instead of an `undefined` that
 * silently falls back to a default at runtime.
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
  readonly VITE_HLS_PUBLIC_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
