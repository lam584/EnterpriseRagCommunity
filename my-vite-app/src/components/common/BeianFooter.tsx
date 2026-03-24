import { useEffect, useState } from 'react';
import beianIcon from '../../assets/images/备案编号图标.png';
import { getPublicSiteConfig, type PublicSiteConfig } from '../../services/publicSiteConfigService';

export interface BeianFooterProps {
  className?: string;
  linkClassName?: string;
  iconClassName?: string;
}

export default function BeianFooter(props: BeianFooterProps) {
  const { className, linkClassName, iconClassName } = props;
  const [siteConfig, setSiteConfig] = useState<PublicSiteConfig | null>(null);

  useEffect(() => {
    let cancelled = false;
    getPublicSiteConfig()
      .then((cfg) => {
        if (!cancelled) setSiteConfig(cfg);
      })
      .catch(() => {
        if (!cancelled) setSiteConfig({ beianText: null, beianHref: null, copyrightText: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const beianText = (siteConfig?.beianText ?? '').trim();
  const beianHref = siteConfig?.beianHref ?? 'https://beian.miit.gov.cn/';
  const showBeian = beianText.length > 0;

  if (!showBeian) return null;

  return (
    <div className={className ?? 'text-xs text-center text-gray-400'}>
      <a
        href={beianHref}
        target="_blank"
        rel="noreferrer"
        className={linkClassName ?? 'inline-flex items-center gap-1'}
      >
        <img src={beianIcon} alt="备案编号" className={iconClassName ?? 'w-4 h-4'} />
        <span>{beianText}</span>
      </a>
    </div>
  );
}
