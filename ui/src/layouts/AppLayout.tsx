import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useEvents } from '../hooks/useEvents';

function KeyIcon() {
  return (
    <svg
      className="topbar__brand-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <circle cx="8" cy="15" r="4" />
      <path d="M12 11l9 0M17 11v4M21 11v4" />
    </svg>
  );
}

export function AppLayout() {
  const { displayName, authMode } = useAuth();
  const { connected } = useEvents(true);

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="topbar__brand">
          <KeyIcon />
          <span>Keycloak Ops</span>
        </NavLink>

        <nav className="topbar__nav" aria-label="Main navigation">
          <NavLink
            to="/targets"
            className={({ isActive }) =>
              'topbar__nav-link' + (isActive ? ' topbar__nav-link--active' : '')
            }
          >
            Fleet
          </NavLink>
        </nav>

        <div className="topbar__spacer" />

        <div className="topbar__user">
          <span
            className="event-dot"
            style={{ background: connected ? 'var(--color-healthy)' : 'var(--color-unknown)' }}
            title={connected ? 'Events connected' : 'Events disconnected'}
            aria-label={connected ? 'Event stream connected' : 'Event stream disconnected'}
          />
          {authMode === 'OPEN_LAB' && (
            <span title="Open Lab mode — no authentication required">Open Lab</span>
          )}
          {displayName && <span>{displayName}</span>}
        </div>
      </header>

      <main className="page-content" style={{ maxWidth: 'none', padding: 0 }}>
        <Outlet />
      </main>
    </div>
  );
}
