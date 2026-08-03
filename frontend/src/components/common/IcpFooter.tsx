import { siteConfig } from "@/config/site";

export function IcpFooter() {
  if (!siteConfig.icpNumber) {
    return null;
  }

  return (
    <footer className="site-icp-footer" aria-label="网站备案信息">
      <a
        href={siteConfig.icpRegistryUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="site-icp-footer__link"
      >
        {siteConfig.icpNumber}
      </a>
    </footer>
  );
}
