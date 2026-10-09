import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigurationPage } from '../../ui/src/pages/ConfigurationPage';
import { invalidateSession, setAuthToken } from '../../ui/src/api/client';
import '../../ui/src/styles/global.css';
import './fixture.css';

function Fixture() {
  const [generation, setGeneration] = useState(0);
  const [requests, setRequests] = useState<{ sequence: number; path: string; state: string }[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const update = async () => {
      try {
        const response = await fetch('/__fixture/requests');
        if (!response.ok) throw new Error();
        const value = await response.json();
        if (active) setRequests(value);
      } catch { if (active) setError('Synthetic request log is unavailable.'); }
    };
    void update();
    const timer = setInterval(update, 500);
    return () => { active = false; clearInterval(timer); };
  }, []);

  async function scenario(mode: string) {
    try {
      const response = await fetch(`/__fixture/mode/${mode}`, { method: 'POST' });
      if (!response.ok) throw new Error();
      setError('');
      setAuthToken(null);
      setGeneration(value => value + 1);
    } catch { setError('Could not change the synthetic scenario.'); }
  }

  return <>
    <header className="fixture-header">
      <strong>SYNTHETIC UI FIXTURE — no backend, Keycloak or OIDC</strong>
      <p>Real Configuration page and API client; synthetic HTTP only. Realm A is slow, Client B is fast.</p>
      <div className="fixture-controls" aria-label="Synthetic scenarios">
        <button type="button" className="btn btn--secondary" onClick={() => void scenario('normal')}>Load normal scopes</button>
        <button type="button" className="btn btn--secondary" onClick={() => void scenario('empty')}>Empty list</button>
        <button type="button" className="btn btn--secondary" onClick={() => void scenario('error')}>List error</button>
        <button type="button" className="btn btn--secondary" onClick={() => invalidateSession()}>Simulate logout</button>
      </div>
      {error && <p role="alert">{error}</p>}
    </header>
    <main><ConfigurationPage key={generation} /></main>
    <aside className="fixture-log" aria-label="Synthetic request log">
      <h2>Synthetic API requests</h2>
      <p>Pending responses remain scheduled after switching scope. Only explicit inspections read a fixture value.</p>
      <ol>{requests.map(entry => <li key={entry.sequence}>
        <code>{entry.sequence} GET {entry.path}</code> — {entry.state}
      </li>)}</ol>
    </aside>
  </>;
}

const root = document.getElementById('root');
if (!root) throw new Error('Synthetic fixture root is missing.');
setAuthToken(null);
createRoot(root).render(<Fixture />);
