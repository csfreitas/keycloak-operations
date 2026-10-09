import { useEffect, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { confirmInstallation, discoverInstallations, fetchInstallation } from '../api/installations';
import type { InstallationDiscovery, InstallationState } from '../api/installations';

export function InstallationPage() {
  const { targetId } = useOutletContext<{ targetId: string }>();
  return <InstallationPanel key={targetId} targetId={targetId} />;
}

function InstallationPanel({ targetId }: { targetId: string }) {
  const [state, setState] = useState<InstallationState | null>(null);
  const [run, setRun] = useState<InstallationDiscovery | null>(null);
  const [selected, setSelected] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [now, setNow] = useState(Date.now());
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    let active = true;
    fetchInstallation(targetId).then(value => {
      if (!active) return;
      if (value.targetId !== targetId) throw new Error('Target mismatch; reload this page.');
      setState(value);
    }).catch(e => { if (active) setError(e.message); });
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => { active = false; alive.current = false; clearInterval(timer); };
  }, [targetId]);

  const expired = run !== null && (!Number.isFinite(Date.parse(run.expiresAt)) || Date.parse(run.expiresAt) <= now);
  const candidate = run?.candidates.find(item => item.id === selected);
  async function discover() {
    setBusy(true); setError(''); setMessage(''); setRun(null); setSelected(''); setAcknowledged(false);
    try {
      const value = await discoverInstallations(targetId);
      if (!alive.current) return;
      if (value.targetId !== targetId) throw new Error('Target mismatch; discovery discarded.');
      setRun(value); setNow(Date.now());
    } catch (e) { if (alive.current) setError((e as Error).message); }
    finally { if (alive.current) setBusy(false); }
  }

  async function confirm() {
    if (!run || !candidate || !acknowledged || expired || !state?.canConfirm) return;
    setBusy(true); setError('');
    try {
      const value = await confirmInstallation(targetId, run.runId, candidate.id);
      if (!alive.current) return;
      if (value.targetId !== targetId) throw new Error('Target mismatch; reload binding status.');
      setState(value); setMessage('Installation binding saved and audited. No cluster resource was changed.');
    } catch (e) { if (alive.current) setError(`${(e as Error).message} Discover again before retrying.`); }
    finally {
      if (alive.current) { setRun(null); setSelected(''); setAcknowledged(false); setBusy(false); }
    }
  }

  return <section aria-label="Installation binding">
    <div className="page-header"><h2 className="page-header__title">Installation</h2></div>
    <p>Target: <strong>{targetId}</strong>. Candidates are configuration resources, not proof of Keycloak identity or health.</p>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {!state && !error && <p role="status">Loading installation permissions…</p>}
    {state && <div className="card">
      <p>Approved connection: {state.clusterId ?? 'Not configured'} · Namespace: {state.namespace ?? 'Not configured'}</p>
      <h3>Current binding</h3>
      {state.binding ? <p>{state.binding.kind} / {state.binding.name}<br />UID: <code>{state.binding.uid}</code></p> : <p>No installation confirmed.</p>}
      <p>Revision {state.revision} · {state.managed ? 'Confirmed through platform' : 'Server configuration'}</p>
      {!state.canDiscover && <p>Discovery requires a persisted target, an approved cluster connection and the DISCOVER permission.</p>}
      {!state.canConfirm && <p>Confirmation requires DISCOVER and BIND permissions with global read-only mode disabled.</p>}
      <button className="btn btn--secondary" disabled={busy || !state.canDiscover} onClick={discover}>Discover candidates</button>
    </div>}
    {run && <div className="card" style={{ marginTop: 'var(--space-4)' }}>
      <p>Review candidates in {run.namespace}. Expires: {new Date(run.expiresAt).toLocaleString()}.</p>
      <p>Workload metadata is not proof that a resource runs Keycloak. Verify ownership before binding.</p>
      {expired && <p role="alert">Discovery expired. Discover again.</p>}
      {run.candidates.length === 0 && <p>No supported candidate resources found in this namespace.</p>}
      <fieldset disabled={busy || expired}>
        <legend>Select the exact installation</legend>
        {run.candidates.map(item => <label key={item.id} style={{ display: 'block', marginBottom: 'var(--space-3)' }}>
          <input type="radio" name="installation" value={item.id} checked={selected === item.id}
            onChange={() => { setSelected(item.id); setAcknowledged(false); }} />
          {' '}{item.installation.kind} / {item.installation.name} · {item.installation.apiVersion}<br />
          UID: <code>{item.installation.uid}</code>
        </label>)}
      </fieldset>
      {candidate && <>
        <p>This replaces {state?.binding ? `UID ${state.binding.uid}` : 'the unbound state'} with UID <code>{candidate.installation.uid}</code> for {targetId}.</p>
        <label><input type="checkbox" checked={acknowledged} disabled={busy || expired}
          onChange={event => setAcknowledged(event.target.checked)} /> I reviewed the target, namespace and resource UID.</label>
      </>}
      <div><button className="btn btn--primary" onClick={confirm}
        disabled={busy || expired || !candidate || !acknowledged || !state?.canConfirm}>Confirm installation</button></div>
    </div>}
  </section>;
}
