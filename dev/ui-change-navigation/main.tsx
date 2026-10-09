import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router-dom';
import { ChangeDetailPage } from '../../ui/src/pages/ChangeDetailPage';
import { ChangesPage } from '../../ui/src/pages/ChangesPage';
import '../../ui/src/styles/global.css';
import './fixture.css';

interface RequestEntry {
  sequence: number;
  method: string;
  path: string;
  state: string;
}

function Fixture() {
  const location = useLocation();
  const [requests, setRequests] = useState<RequestEntry[]>([]);
  const [fixtureError, setFixtureError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const update = async () => {
      try {
        const response = await fetch('/__fixture/requests');
        if (!response.ok) throw new Error('Synthetic request log is unavailable.');
        const entries = await response.json() as RequestEntry[];
        if (active) setRequests(entries);
      } catch {
        if (active) setFixtureError('Synthetic request log is unavailable.');
      }
    };
    void update();
    const timer = window.setInterval(() => { void update(); }, 250);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  async function reset() {
    const response = await fetch('/__fixture/reset', { method: 'POST' });
    setFixtureError(response.ok ? null : 'Wait for pending synthetic requests before resetting.');
  }

  return (
    <>
      <header className="fixture-header">
        <strong>SYNTHETIC UI FIXTURE — no backend, Keycloak, identity or real writes</strong>
        <p>Real Changes and ChangeDetail components; simulated HTTP replies only. Use these SPA links to retain the router while switching records.</p>
        <nav aria-label="Synthetic change scenarios">
          <Link to="/changes/synthetic-a">Detail A · slow read/action</Link>
          <Link to="/changes/synthetic-b">Detail B · fast read / slower action</Link>
          <Link to="/changes/synthetic-denied">Detail denied · 403</Link>
          <Link to="/changes/synthetic-missing">Detail missing · 404</Link>
          <Link to="/changes/synthetic-wrong-id">Detail wrong identity</Link>
          <Link to="/changes/synthetic-wrong-action">Action wrong target</Link>
        </nav>
        <nav aria-label="Synthetic list scenarios">
          <Link to="/changes?targetId=synthetic-target-a">List target A · slow</Link>
          <Link to="/changes?targetId=synthetic-target-b">List target B · fast</Link>
          <Link to="/changes">List without target</Link>
          <Link to="/changes?targetId=synthetic-target-mismatch">List wrong target</Link>
          <Link to="/changes?targetId=synthetic-target-denied">List denied · 403</Link>
          <Link to="/changes?targetId=synthetic-target-a&status=APPROVED">List target A · APPROVED fast</Link>
        </nav>
        <p className="fixture-route">Current route: <code>{location.pathname}{location.search}</code></p>
        <button type="button" className="btn btn--sm btn--secondary" onClick={() => { void reset().catch(() => setFixtureError('Could not reset synthetic records.')); }}>Reset synthetic records (when idle)</button>
        {fixtureError && <p role="alert">{fixtureError}</p>}
      </header>
      <main>
        <Routes>
          <Route path="/changes/:changeId" element={<ChangeDetailPage />} />
          <Route path="/changes" element={<ChangesPage />} />
          <Route path="*" element={<p className="fixture-home">Choose a synthetic scenario above. Fleet and target pages are outside this fixture.</p>} />
        </Routes>
      </main>
      <aside className="fixture-log" aria-label="Synthetic HTTP request log">
        <h2>Synthetic requests (last 80; no request bodies)</h2>
        <p>Pending replies remain scheduled after navigation. This deliberately exercises late-success/error/finally handling.</p>
        <ol>
          {requests.map((entry) => (
            <li key={entry.sequence}><code>{entry.sequence} {entry.method} {entry.path}</code> — {entry.state}</li>
          ))}
        </ol>
      </aside>
    </>
  );
}

const container = document.getElementById('root');
if (!container) throw new Error('Synthetic fixture root is missing.');
createRoot(container).render(<BrowserRouter><Fixture /></BrowserRouter>);
