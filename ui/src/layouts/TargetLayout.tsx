import { NavLink, Outlet, useParams } from 'react-router-dom';
import { TargetContextBar } from '../components/TargetContextBar';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { useTargetOverview } from '../hooks/useTargetOverview';

interface NavTab {
  path: string;
  label: string;
  end?: boolean;
}

const NAV_TABS: NavTab[] = [
  { path: '', label: 'Overview', end: true },
  { path: 'health', label: 'Health' },
  { path: 'assessment', label: 'Assessment' },
  { path: 'performance', label: 'Performance' },
  { path: 'infrastructure', label: 'Infrastructure' },
  { path: 'history', label: 'History' },
];

function TargetTabs({ targetId }: { targetId: string }) {
  return (
    <nav className="target-tabs" aria-label="Target sections">
      {NAV_TABS.map((tab) => (
        <NavLink
          key={tab.path}
          to={`/targets/${encodeURIComponent(targetId)}${tab.path ? '/' + tab.path : ''}`}
          end={tab.end ?? false}
          className={({ isActive }) =>
            'target-tabs__link' + (isActive ? ' target-tabs__link--active' : '')
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}

/**
 * TargetLayout resets entirely when targetId changes, preventing
 * stale data from target A appearing under target B.
 * The `key={targetId}` on this component (set in routes) ensures a full remount.
 */
export function TargetLayout() {
  const { targetId } = useParams<{ targetId: string }>();

  if (!targetId) return null;

  return <TargetLayoutInner targetId={targetId} />;
}

function TargetLayoutInner({ targetId }: { targetId: string }) {
  const { overview, loading, error, refresh } = useTargetOverview(targetId);

  const isPrd = overview?.environment === 'PRD';

  return (
    <div>
      {isPrd && (
        <div className="prd-warning-bar" role="banner" aria-label="Production environment warning">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          PRODUCTION ENVIRONMENT — changes have real impact
        </div>
      )}

      {loading && !overview && (
        <div style={{ padding: 'var(--space-4) var(--space-6)' }}>
          <LoadingState inline message="Loading target…" />
        </div>
      )}

      {error && !overview && (
        <ErrorState error={error} onRetry={refresh} title="Failed to load target" />
      )}

      {overview && (
        <>
          <TargetContextBar target={overview} />
          <TargetTabs targetId={targetId} />
        </>
      )}

      {!loading && !error && !overview && (
        <div style={{ padding: 'var(--space-4) var(--space-6)' }}>
          <TargetTabs targetId={targetId} />
        </div>
      )}

      <div className="page-content">
        <Outlet context={{ targetId, overview }} />
      </div>
    </div>
  );
}
