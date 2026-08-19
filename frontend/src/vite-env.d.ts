/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_REGISTRATION_ENABLED?: string;
  readonly VITE_ICP_NUMBER?: string;
  readonly VITE_PUBLIC_SECURITY_RECORD_NUMBER?: string;
  readonly VITE_PUBLIC_SECURITY_RECORD_CODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
