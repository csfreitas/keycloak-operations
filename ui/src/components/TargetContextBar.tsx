import type { TargetOverview, FleetItem } from '../api/types';
import { EnvBadge } from './EnvBadge';
import { StatusBadge } from './StatusBadge';

type TargetContext = Pick<
  TargetOverview | FleetItem,
  'targetId' | 'displayName' | 'environment' | 'productType' | 'healthStatus'
> & { productVersion?: string | null };

interface TargetContextBarProps {
  target: TargetContext;
}

export function TargetContextBar({ target }: TargetContextBarProps) {
  return (
    <div className="target-context-bar" data-testid="target-context-bar">
      <span className="target-context-bar__name">{target.displayName}</span>
      <div className="target-context-bar__meta">
        <EnvBadge env={target.environment} />
        <span>{target.productType}</span>
        {target.productVersion && <span>{target.productVersion}</span>}
        <StatusBadge status={target.healthStatus} size="sm" />
      </div>
      <span className="target-context-bar__id">{target.targetId}</span>
    </div>
  );
}
