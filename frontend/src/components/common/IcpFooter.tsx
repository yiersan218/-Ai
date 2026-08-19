import { siteConfig } from "@/config/site";

export function IcpFooter() {
  const hasIcpNumber = Boolean(siteConfig.icpNumber);
  const hasPublicSecurityRecord = Boolean(siteConfig.publicSecurityRecordNumber);

  if (!hasIcpNumber && !hasPublicSecurityRecord) {
    return null;
  }

  return (
    <footer className="site-icp-footer" aria-label="网站备案信息">
      <div className="site-icp-footer__items">
        {hasIcpNumber ? (
          <a
            href={siteConfig.icpRegistryUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="site-icp-footer__link"
          >
            {siteConfig.icpNumber}
          </a>
        ) : null}
        {hasIcpNumber && hasPublicSecurityRecord ? (
          <span className="site-icp-footer__separator" aria-hidden="true">
            ·
          </span>
        ) : null}
        {hasPublicSecurityRecord ? (
          <a
            href={siteConfig.publicSecurityRecordUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="site-icp-footer__link site-icp-footer__link--public-security"
          >
            <img
              src="/assets/image/head-logo.png"
              width={20}
              height={20}
              alt=""
              aria-hidden="true"
              className="site-icp-footer__icon"
            />
            <span>{siteConfig.publicSecurityRecordNumber}</span>
          </a>
        ) : null}
      </div>
    </footer>
  );
}
