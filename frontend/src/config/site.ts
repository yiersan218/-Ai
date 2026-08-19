const publicSecurityRecordCode = import.meta.env.VITE_PUBLIC_SECURITY_RECORD_CODE?.trim() || "";
const registrationEnabled =
  (import.meta.env.VITE_REGISTRATION_ENABLED?.trim().toLowerCase() || "true") === "true";

export const siteConfig = {
  registrationEnabled,
  icpNumber: import.meta.env.VITE_ICP_NUMBER?.trim() || "",
  icpRegistryUrl: "https://beian.miit.gov.cn/",
  publicSecurityRecordNumber: import.meta.env.VITE_PUBLIC_SECURITY_RECORD_NUMBER?.trim() || "",
  publicSecurityRecordUrl: publicSecurityRecordCode
    ? `https://beian.mps.gov.cn/#/query/webSearch?code=${encodeURIComponent(publicSecurityRecordCode)}`
    : "https://beian.mps.gov.cn/"
} as const;
