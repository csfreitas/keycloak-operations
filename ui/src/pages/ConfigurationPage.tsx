import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchConfigurationScopes, inspectConfiguration } from '../api/configurationReads';
import type { ConfigurationObservation, ConfigurationScope } from '../api/configurationReads';
import { captureSession, isCurrentSession } from '../api/session';
import { EmptyState } from '../components/EmptyState';
import { ErrorState } from '../components/ErrorState';
import { LoadingState } from '../components/LoadingState';

const FIELD_LABELS: Record<string, string> = {
  enabled: 'Enabled', registrationAllowed: 'User registration', resetPasswordAllowed: 'Password reset',
  bruteForceProtected: 'Brute-force protection', verifyEmail: 'Email verification', publicClient: 'Public client',
  standardFlowEnabled: 'Standard flow', implicitFlowEnabled: 'Implicit flow',
  directAccessGrantsEnabled: 'Direct access grants', serviceAccountsEnabled: 'Service accounts',
};

export function ConfigurationPage() {
  const [scopes, setScopes] = useState<ConfigurationScope[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const generation = useRef(0);
  const selected = scopes.find(scope => scope.scopeId === selectedId);

  const load = useCallback(async () => {
    const requestGeneration = ++generation.current;
    const session = captureSession();
    if (!isCurrentSession(session)) {
      setScopes([]); setSelectedId(''); setLoading(false); setError(new Error('Session expired. Sign in again.'));
      return;
    }
    setLoading(true); setError(null); setScopes([]); setSelectedId('');
    try {
      const value = await fetchConfigurationScopes();
      if (requestGeneration === generation.current && isCurrentSession(session)) setScopes(value);
    } catch (err) {
      if (requestGeneration === generation.current && isCurrentSession(session)) setError(err as Error);
    } finally {
      if (requestGeneration === generation.current && isCurrentSession(session)) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const session = captureSession();
    const clear = () => {
      generation.current += 1;
      setScopes([]); setSelectedId(''); setLoading(false); setError(new Error('Session expired. Sign in again.'));
    };
    session.signal.addEventListener('abort', clear, { once: true });
    void load();
    return () => { generation.current += 1; session.signal.removeEventListener('abort', clear); };
  }, [load]);

  return <div className="page configuration-page">
    <header className="page-header"><div>
      <h1 className="page-header__title">Configuration</h1>
      <p className="page-header__subtitle">Inspect the configuration fields authorized for your account.</p>
    </div></header>
    {loading && <LoadingState message="Loading authorized scopes…" />}
    {!loading && error && <ErrorState error={error} title="Unable to load configuration scopes" onRetry={load} />}
    {!loading && !error && scopes.length === 0 && <EmptyState title="No authorized scopes"
      description="No configuration inspection scopes are available for your account." />}
    {!loading && !error && scopes.length > 0 && <>
      <div className="configuration-selector">
        <label htmlFor="configuration-scope">Configuration scope</label>
        <select id="configuration-scope" value={selectedId} onChange={event => setSelectedId(event.target.value)}>
          <option value="">Select a scope</option>
          {scopes.map(scope => <option key={scope.scopeId} value={scope.scopeId}>
            {scope.scopeId} · {scope.targetId} · {scope.realm} · {scope.kind === 'REALM' ? 'Realm' : 'Client'}
          </option>)}
        </select>
      </div>
      {!selected && <p>Select a scope, then run an inspection to read its current values.</p>}
      {selected && <ConfigurationInspection key={JSON.stringify(selected)} scope={selected} />}
    </>}
  </div>;
}

function ConfigurationInspection({ scope }: { scope: ConfigurationScope }) {
  const [observation, setObservation] = useState<ConfigurationObservation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const generation = useRef(0);
  const running = useRef(false);

  useEffect(() => () => { generation.current += 1; }, []);

  async function inspect() {
    if (running.current) return;
    running.current = true;
    const requestGeneration = ++generation.current;
    const session = captureSession();
    setObservation(null); setError(null); setBusy(true);
    try {
      const value = await inspectConfiguration(scope);
      if (requestGeneration !== generation.current || !isCurrentSession(session)) return;
      setObservation(value);
    } catch (err) {
      if (requestGeneration === generation.current && isCurrentSession(session)) setError(err as Error);
    } finally {
      if (requestGeneration === generation.current && isCurrentSession(session)) {
        running.current = false;
        setBusy(false);
      }
    }
  }

  return <section aria-label="Configuration inspection">
    <div className="card configuration-summary">
      <p>Target: <strong>{scope.targetId}</strong> · Realm: <strong>{scope.realm}</strong> · {scope.kind === 'REALM' ? 'Realm' : 'Client'} scope: <strong>{scope.scopeId}</strong></p>
      <p>Fields: {scope.fields.map(field => FIELD_LABELS[field]).join(', ')}.</p>
      <button type="button" className="btn btn--primary" disabled={busy} onClick={inspect}>Run inspection</button>
    </div>
    {busy && <LoadingState message="Inspecting configuration…" />}
    {error && <ErrorState error={error} title="Configuration inspection failed" />}
    {observation && <section aria-label="Inspection results" className="configuration-results">
      <h2>Inspection results</h2>
      <p role="status">{observation.status === 'COMPLETE' ? 'Complete observation' : 'Partial observation'}.</p>
      <p>Collected: <time dateTime={observation.collectedAt}>{new Date(observation.collectedAt).toLocaleString()}</time></p>
      <p>Source: Keycloak Admin API · Product version: Unknown</p>
      <p>Observation: <code>{observation.observationId}</code></p>
      <div className="data-table-wrapper"><table className="data-table">
        <caption className="configuration-caption">Observed values for {scope.scopeId}</caption>
        <thead><tr><th scope="col">Configuration field</th><th scope="col">Observed value</th></tr></thead>
        <tbody>{scope.fields.map(field => {
          const fact = observation.missingFields.includes(field) ? null : observation.facts[field];
          return <tr key={field}><th scope="row">{FIELD_LABELS[field]}</th>
            <td>{fact === true ? 'True' : fact === false ? 'False' : 'Unknown'}</td></tr>;
        })}</tbody>
      </table></div>
      <p>Unknown means the field was unavailable. These values are not a health assessment.</p>
    </section>}
  </section>;
}
