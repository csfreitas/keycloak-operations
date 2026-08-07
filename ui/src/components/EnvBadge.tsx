import type { Environment } from '../api/types';

interface EnvBadgeProps {
  env: Environment | string;
}

export function EnvBadge({ env }: EnvBadgeProps) {
  const upper = String(env).toUpperCase();
  const isPrd = upper === 'PRD';

  let className = 'env-badge';
  if (upper === 'PRD') className += ' env-badge--prd';
  else if (upper === 'STG') className += ' env-badge--stg';
  else if (upper === 'DEV') className += ' env-badge--dev';
  else if (upper === 'TEST') className += ' env-badge--test';
  else className += ' env-badge--unknown';

  return (
    <span className={className} data-testid="env-badge" aria-label={`Environment: ${env}`}>
      {isPrd && <span className="env-badge__prd-dot" aria-hidden="true" />}
      {env}
    </span>
  );
}
