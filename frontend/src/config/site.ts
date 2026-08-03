export const siteConfig = {
  icpNumber: import.meta.env.VITE_ICP_NUMBER?.trim() || "",
  icpRegistryUrl: "https://beian.miit.gov.cn/"
} as const;
