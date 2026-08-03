/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ICP_NUMBER?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
