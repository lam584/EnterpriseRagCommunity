import { useEffect, useState } from 'react';
import beianIcon from '../../assets/images/备案编号图标.png';
import { getPublicSiteConfig, type PublicSiteConfig } from '../../services/publicSiteConfigService';
import { applySiteConfigIfActive, fallbackSiteConfigIfActive } from '../common/beianFooterShared';

export interface AuthFooterProps {
  className?: string;
}

const DEFAULT_COPYRIGHT = '©2026 版权所有。';

export default function AuthFooter(props: AuthFooterProps) {
  const { className } = props;
  const [siteConfig, setSiteConfig] = useState<PublicSiteConfig | null>(null);

  useEffect(() => {
    let cancelled = false;
    getPublicSiteConfig()
      .then((cfg) => {
        applySiteConfigIfActive(cancelled, setSiteConfig, cfg);
      })
      .catch(() => {
        fallbackSiteConfigIfActive(cancelled, setSiteConfig);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const beianText = (siteConfig?.beianText ?? '').trim();
  const beianHref = siteConfig?.beianHref ?? 'https://beian.miit.gov.cn/';
  const copyrightText = (siteConfig?.copyrightText ?? DEFAULT_COPYRIGHT).trim() || DEFAULT_COPYRIGHT;
  const showBeian = beianText.length > 0;

  return (
    <div className={className ?? 'text-center text-white p-4'}>
      <p className="text-sm mt-4">{copyrightText}</p>
      {showBeian ? (
        <a
          href={beianHref}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1"
        >
          <img src={beianIcon} alt="" aria-hidden="true" className="w-4 h-4" />
          <span>{beianText}</span>
        </a>
      ) : null}
    </div>
  );
}
