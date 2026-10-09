import { observedFetch } from './transport.mjs';

const scenario = import.meta.env.KCOPS_BROWSER_SCENARIO;
const evidence = document.getElementById('fixture-evidence')!;
evidence.style.cssText = 'padding:1rem;border:2px solid #fbbf24;background:#111827;color:#f9fafb;font:14px/1.5 system-ui;overflow-wrap:anywhere';
const heading = document.createElement('h1');
heading.style.cssText = 'font-size:1.1rem;margin:0 0 .5rem';
heading.textContent = `LOCAL DEVELOPMENT FIXTURE — ${scenario}`;
const description = document.createElement('p');
description.textContent = 'Real local IdP, production AuthProvider/API client and backend. Only fixed /me requests are observed. Expired scenario deliberately delays the first request after credential creation. No operational writes or new grants.';
const status = document.createElement('pre');
status.style.cssText = 'white-space:pre-wrap;margin:.5rem 0 0';
status.setAttribute('role', 'status');
status.textContent = 'Awaiting the real sign-in and identity request. Signature null means verification unavailable, not valid.';
evidence.append(heading, description, status);
const observations: object[] = [];
window.fetch = observedFetch({
  scenario,
  nativeFetch: window.fetch.bind(window),
  onObservation(value: object) {
    observations.push(value);
    if (observations.length > 12) observations.shift();
    status.textContent = observations.map(item => JSON.stringify(item)).join('\n');
  },
});

// Install the observer before any production authentication/transport code executes.
void import('../../ui/src/main');
