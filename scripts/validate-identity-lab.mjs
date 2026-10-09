// Only disposable loopback fixtures. Never print tokens or credential responses.
import assert from 'node:assert/strict';
import { collectReport, evaluateExplanation, PROFILE_VERSION } from '../dev/reference-agent/core.mjs';
const base = 'http://localhost:18081';
const metricsMode = process.env.IDENTITY_LAB_METRICS === 'true';
const referenceAgent = process.env.IDENTITY_LAB_REFERENCE_AGENT === 'true';
let passed = 0;
function check(name, condition) { assert.ok(condition, name); console.log(`PASS ${++passed}: ${name}`); }
function performanceExpected(id) { return metricsMode && id === 'a'; }
function checkPerformanceSection(report, id, surface) {
  const section = report.sections?.find(s => s.name === 'performance');
  console.log(`OBSERVED: ${surface} ${id} performance=${section?.status ?? 'MISSING'}; message=${section?.message ?? 'NONE'}`);
  check(`${surface} performance section matches explicit configuration: ${id}`,
    report.sections?.some(s => s.name === 'performance' && s.status === (performanceExpected(id) ? 'COMPLETE' : 'SKIPPED')));
  if (performanceExpected(id)) {
    const missing = surface === 'MCP'
      ? report.markdown?.split('\n').find(line => line.startsWith('Missing evidence (not a pass): '))
      : report.assessment?.missingEvidence;
    check(`${surface} explicit heap policy reaches assessment evidence: ${id}`,
      missing != null && !missing.includes('metrics.jvm.heapPressure')
      && report.assessment?.profile === 'keycloak-production-performance' && report.assessment.rulesEvaluated >= 8);
  }
}
function rpcPayload(text) {
  const messages = text.trim().startsWith('data:') || text.includes('\ndata:')
    ? text.split('\n').filter(line => line.startsWith('data:')).map(line => JSON.parse(line.slice(5).trim()))
    : [JSON.parse(text)];
  const message = messages.find(m => m.result || m.error);
  assert.ok(message?.result && !message.error && !message.result.isError, 'MCP report succeeds');
  return message.result.structuredContent ?? JSON.parse(message.result.content.filter(c => c.type === 'text').map(c => c.text).join(''));
}
if (metricsMode) {
  const deadline = Date.now() + 60000;
  let ready = false;
  while (Date.now() < deadline && !ready) {
    try {
      const url = 'http://127.0.0.1:18490/api/v1/query?query=' + encodeURIComponent('up{job=~"identity-keycloak-[ab]"}');
      const response = await fetch(url, { signal: AbortSignal.timeout(3000) });
      const body = await response.json();
      ready = body.status === 'success' && body.data?.result?.length === 2
        && body.data.result.every(s => s.value[1] === '1')
        && ['lab-keycloak-a','lab-keycloak-b'].every(id => body.data.result.some(s => s.metric.target_id === id));
    } catch { /* Allow the owned Prometheus and first scrapes to become ready. */ }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 1000));
  }
  check('Real Prometheus scrapes both separately labelled local targets', ready);
}
async function token(id) {
  const response = await fetch('http://localhost:18280/realms/operations/protocol/openid-connect/token', {
    method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials', client_id: `assessor-${id}`, client_secret: `local-fixture-${id}` }),
  });
  assert.equal(response.status, 200, `Fixture token issuance: ${id}`);
  return (await response.json()).access_token;
}
async function request(path, bearer, options = {}) {
  return fetch(base + path, { ...options, signal: AbortSignal.timeout(20000), headers: { ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}), ...options.headers } });
}
for (const path of ['/api/v1/me', '/api/v1/targets', '/api/v1/events', '/mcp']) {
  const result = await request(path); check(`Anonymous denied ${path}`, [401,403].includes(result.status)); await result.body?.cancel();
}
const tokens = { a: await token('a'), b: await token('b') };
async function until(predicate, description) {
  const deadline = Date.now() + 10000;
  while (!predicate()) {
    assert.ok(Date.now() < deadline, description);
    await new Promise(resolve => setTimeout(resolve, 25));
  }
}
async function verifyEvents() {
  const streams = [];
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    for (const id of ['a', 'b']) {
      const response = await fetch(base + '/api/v1/events', {
        headers: { Authorization: `Bearer ${tokens[id]}` }, signal: controller.signal,
      });
      assert.equal(response.status, 200, 'Event stream accepted');
      const state = { id, events: [], error: null };
      streams.push(state);
      state.task = (async () => {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let pending = '';
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            pending += decoder.decode(value, { stream: true });
            let newline;
            while ((newline = pending.indexOf('\n')) >= 0) {
              const line = pending.slice(0, newline).trimEnd();
              pending = pending.slice(newline + 1);
              if (!line.startsWith('data:')) continue;
              let event = JSON.parse(line.slice(5).trim());
              if (typeof event === 'string') event = JSON.parse(event);
              state.events.push(event);
            }
          }
        } finally { reader.releaseLock(); }
      })().catch(error => { if (!controller.signal.aborted) state.error = error; });
    }
    await until(() => streams.every(s => s.events.some(e => e.type === 'hello')), 'Both event subscriptions ready');
    const runs = [];
    // Last A event is an ordering barrier after B; checking only connection success is insufficient.
    for (const id of ['a', 'b', 'a']) {
      const response = await request(`/api/v1/targets/lab-keycloak-${id}/health-checks`, tokens[id], { method: 'POST' });
      assert.equal(response.status, 200, `Authenticated health run: ${id}`);
      const run = await response.json();
      assert.ok(run.id, 'Persisted health run ID');
      runs.push({ id, runId: run.id });
    }
    await until(() => streams.every(s => runs.filter(r => r.id === s.id)
      .every(r => s.events.some(e => e.relatedId === r.runId))), 'Own health completion events arrive');
    for (const stream of streams) {
      assert.equal(stream.error, null, 'No event parser/transport failure');
      check(`SSE delivers own persisted health events: ${stream.id}`, stream.events.some(e => e.type === 'health_check_completed' && e.targetId === `lab-keycloak-${stream.id}`));
      check(`SSE excludes foreign target events: ${stream.id}`, stream.events.every(e => !e.targetId || e.targetId === `lab-keycloak-${stream.id}`));
    }
  } finally {
    clearTimeout(timer); controller.abort();
    await Promise.all(streams.map(s => s.task));
  }
}
async function mcpCheck(id) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' };
  const send = (method, params, requestId) => request('/mcp', tokens[id], { method: 'POST', headers, body: JSON.stringify({ jsonrpc: '2.0', ...(requestId ? { id: requestId } : {}), method, params }) });
  const initialized = await send('initialize', { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'identity-validation', version: '1' } }, 1);
  check(`Real authenticated MCP initializes: ${id}`, initialized.status === 200 && !!initialized.headers.get('Mcp-Session-Id'));
  headers['Mcp-Session-Id'] = initialized.headers.get('Mcp-Session-Id');
  await initialized.body?.cancel();
  try {
    const notification = await send('notifications/initialized', {});
    assert.ok([200,202,204].includes(notification.status)); await notification.body?.cancel();
    const listed = await send('tools/call', { name: 'keycloak_list_targets', arguments: {} }, 2);
    const text = await listed.text();
    check(`MCP target listing isolated: ${id}`, listed.status === 200 && text.includes(`lab-keycloak-${id}`) && !text.includes(`lab-keycloak-${id === 'a' ? 'b' : 'a'}`));
    const denied = await send('tools/call', { name: 'keycloak_get_target', arguments: { targetId: `lab-keycloak-${id === 'a' ? 'b' : 'a'}` } }, 3);
    check(`MCP foreign target denied: ${id}`, denied.status === 200 && (await denied.text()).includes('TARGET_NOT_AUTHORIZED'));
    let report;
    if (referenceAgent) {
      let calls = 0;
      let original;
      const packet = await collectReport({ targetId: `lab-keycloak-${id}` }, async (toolRequest, { signal }) => {
        calls++;
        const response = await fetch(base + '/mcp', {
          method: 'POST', headers: { ...headers, Authorization: `Bearer ${tokens[id]}` }, signal,
          redirect: 'error', body: JSON.stringify({ jsonrpc: '2.0', id: 4, method: 'tools/call', params: toolRequest }),
        });
        assert.equal(response.status, 200);
        report = rpcPayload(await response.text());
        original = JSON.stringify(report);
        return report;
      });
      check(`Reference profile uses one fixed scoped MCP collection: ${id}`, calls === 1
        && packet.kind === 'DETERMINISTIC_NO_AI' && packet.targetId === `lab-keycloak-${id}`
        && packet.reportId === report.reportId && packet.profileVersion === PROFILE_VERSION);
      check(`Reference facts preserve unavailable score and source bytes: ${id}`,
        packet.facts.some(f => f.path === '/assessment/overallScore' && f.value === null)
        && packet.facts.some(f => f.path === '/reportCompleteness' && f.value === report.reportCompleteness)
        && JSON.stringify(report) === original && !packet.facts.some(f => /markdown|message|DisplayName/.test(f.path)));
      const { profileVersion, targetId, reportId, facts } = packet;
      const evaluation = evaluateExplanation(report, targetId, {
        profileVersion, targetId, reportId, facts, observations: [], hypotheses: [], recommendations: [],
      });
      check(`Reference contract never claims model or semantic acceptance: ${id}`,
        evaluation.structuralChecks === 'PASSED' && evaluation.semanticReview === 'REQUIRED'
        && evaluation.modelEvaluated === false);
      const details = report.findingDetails;
      check(`Reference findings bind to this exact report and assessment: ${id}`,
        details.version === '1.0' && details.reportId === report.reportId
        && details.targetId === report.targetId && details.assessmentId === report.assessment.assessmentId
        && details.availability === 'AVAILABLE' && details.totalFindings === report.assessment.findingCount
        && details.returnedFindings > 0 && details.returnedFindings === details.items.length
        && details.totalFindings === details.returnedFindings + details.omittedFindings);
      const findingFacts = packet.facts.filter(f => f.path.startsWith('/findingDetails/items/'));
      for (const fact of findingFacts) {
        const value = fact.path.slice(1).split('/').reduce((source, part) =>
          source[part.replaceAll('~1', '/').replaceAll('~0', '~')], report);
        assert.deepEqual(fact.value, value);
      }
      check(`Reference finding evidence has exact source JSON pointers: ${id}`,
        findingFacts.some(f => f.path.includes('/finding/evidence/'))
        && JSON.stringify(report) === original);
    } else {
      const generated = await send('tools/call', { name: 'keycloak_generate_operations_report', arguments: { targetId: `lab-keycloak-${id}`, metricsWindow: '5m' } }, 4);
      assert.equal(generated.status, 200);
      report = rpcPayload(await generated.text());
    }
    check(`MCP report retains target identity and schema: ${id}`, report.targetId === `lab-keycloak-${id}` && report.schemaVersion === '1.1');
    checkPerformanceSection(report, id, 'MCP');
    check(`MCP Markdown projects unknown infrastructure: ${id}`,
      /"hpa"\s*:\s*null/.test(report.markdown) && /"topology"\s*:\s*null/.test(report.markdown));
    check(`MCP report preserves inconclusive score and unavailable replay: ${id}`, report.assessment?.scoreAvailable === false
      && report.assessment?.overallScore == null && report.provenance?.retainedEvidenceReplayAvailable === false);
    check(`MCP report contains deterministic Markdown without fixture secrets: ${id}`,
      report.markdown?.includes('## Performance') && ![tokens.a,tokens.b,'local-target-a','local-target-b','local-fixture-a','local-fixture-b'].some(s => JSON.stringify(report).includes(s)));
    const foreignReport = await send('tools/call', { name: 'keycloak_generate_operations_report', arguments: { targetId: `lab-keycloak-${id === 'a' ? 'b' : 'a'}` } }, 5);
    check(`MCP report rejects foreign target: ${id}`, foreignReport.status === 200 && (await foreignReport.text()).includes('TARGET_NOT_AUTHORIZED'));
  } finally { await request('/mcp', tokens[id], { method: 'DELETE', headers }); }
}
for (const id of ['a', 'b']) {
  const me = await request('/api/v1/me', tokens[id]);
  check(`Real signed Identity A accepted: ${id}`, me.status === 200 && (await me.json()).authMode === 'OIDC');
  const list = await request('/api/v1/targets', tokens[id]);
  const targets = await list.json();
  check(`Target listing isolated: ${id}`, list.status === 200 && targets.length === 1 && targets[0].id === `lab-keycloak-${id}`);
  check(`Own target readable: ${id}`, (await request(`/api/v1/targets/lab-keycloak-${id}`, tokens[id])).status === 200);
  check(`Foreign target denied: ${id}`, (await request(`/api/v1/targets/lab-keycloak-${id === 'a' ? 'b' : 'a'}`, tokens[id])).status === 403);
  const ownEnvironment = await request(`/api/v1/targets/lab-keycloak-${id}/environment`, tokens[id]);
  const ownEnvironmentBody = await ownEnvironment.json();
  check(`Environment discovery retains authorized target scope: ${id}`,
    ownEnvironment.status === 200 && ownEnvironmentBody.targetId === `lab-keycloak-${id}`);
  const foreignEnvironment = await request(`/api/v1/targets/lab-keycloak-${id === 'a' ? 'b' : 'a'}/environment`, tokens[id]);
  const foreignEnvironmentBody = await foreignEnvironment.json();
  check(`Environment discovery rejects foreign target: ${id}`,
    foreignEnvironment.status === 403 && foreignEnvironmentBody.code === 'TARGET_NOT_AUTHORIZED');
  const events = await request('/api/v1/events', tokens[id]);
  check(`Authenticated SSE connects: ${id}`, events.status === 200 && events.headers.get('content-type')?.includes('text/event-stream'));
  await events.body?.cancel();
  const plan = await request('/api/v1/changes/plan/client-update', tokens[id], { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetId: `lab-keycloak-${id}`, realm: `target-${id}`, clientId: `portal-${id}`, desiredState: { description: 'must not change' }, actor: 'fixture', idempotencyKey: 'denied-plan' }) });
  check(`Reader cannot plan a write: ${id}`, plan.status === 403);
  const upstream = await fetch(`http://localhost:${id === 'a' ? 18080 : 18180}/realms/target-${id}/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'operations-reader', client_secret: `local-target-${id}` }) });
  check(`Separate Identity B credential works: ${id}`, upstream.status === 200);
  const upstreamToken = (await upstream.json()).access_token;
  check(`Target credential cannot authenticate to the platform: ${id}`, (await request('/api/v1/me', upstreamToken)).status === 401);
  const clients = await fetch(`http://localhost:${id === 'a' ? 18080 : 18180}/admin/realms/target-${id}/clients`, { headers: { Authorization: `Bearer ${upstreamToken}` } });
  check(`Identity B can inspect target clients: ${id}`, clients.status === 200 && (await clients.json()).some(client => client.clientId === `portal-${id}`));
  const wrongSecret = await fetch(`http://localhost:${id === 'a' ? 18080 : 18180}/realms/target-${id}/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'operations-reader', client_secret: `local-target-${id === 'a' ? 'b' : 'a'}` }) });
  check(`Foreign Identity B credential denied: ${id}`, wrongSecret.status === 401);
  await mcpCheck(id);
}
await verifyEvents();
for (const id of ['a', 'b']) {
  const targetId = `lab-keycloak-${id}`;
  const response = await request(`/api/v1/targets/${targetId}/operations-reports`, tokens[id], { method: 'POST' });
  const report = await response.json();
  check(`Authenticated report retains target identity: ${id}`, response.status === 200 && report.targetId === targetId);
  checkPerformanceSection(report, id, 'REST');
  const inventory = report.environmentSnapshot?.summary?.inventory;
  check(`Report infrastructure defaults are unknown, not observed absence: ${id}`,
    inventory && ['cluster','keycloak','pods','topology','hpa','pdb','scheduling','probes','networking'].every(key => inventory[key] == null));
  if (performanceExpected(id)) {
    check('REST report contains real scoped JVM metrics', report.performance?.targetId === targetId
      && report.performance.providerStatus === 'AVAILABLE' && report.performance.jvm?.heapUsedBytes > 0);
    check('Missing container metrics are not invented as zero', report.performance.runtime?.memoryWorkingSetBytes == null);
    check('Available telemetry does not make incomplete assessment conclusive', report.assessment?.scoreAvailable === false && report.status === 'PARTIAL');
  } else {
    check(`Unconfigured metrics do not leak other target telemetry: ${id}`, report.performance == null);
  }
  const adminHealth = report.healthCheck?.components?.find(c => c.name === 'keycloak.adminApi');
  check(`Scoped readable fixture is not mislabeled as Admin API outage: ${id}`,
    adminHealth && ['HEALTHY', 'UNKNOWN'].includes(adminHealth.status)
      && !adminHealth.message?.includes('Admin API unreachable'));
  console.log(`OBSERVED: ${id} admin health=${adminHealth.status}; reason=${adminHealth.details?.reasonCode ?? 'NONE'}; versionAvailable=${adminHealth.details?.versionAvailable ?? 'UNKNOWN'}`);
  const overviewResponse = await request(`/api/v1/targets/${targetId}/status`, tokens[id]);
  const overview = await overviewResponse.json();
  check(`Uncollected infrastructure counts stay unknown: ${id}`, overviewResponse.status === 200
    && ['desiredReplicas', 'readyReplicas', 'podCount', 'zoneCount'].every(key => overview[key] == null));
  check(`Report does not claim retained replay: ${id}`, report.provenance?.retainedEvidenceReplayAvailable === false);
  const own = await request(`/api/v1/assessments/${report.assessment?.assessmentId}`, tokens[id]);
  check(`Report assessment history is readable by owner: ${id}`, own.status === 200);
  const foreign = await request(`/api/v1/assessments/${report.assessment?.assessmentId}`, tokens[id === 'a' ? 'b' : 'a']);
  check(`Report assessment history denied to foreign caller: ${id}`, foreign.status === 403);
  const serialized = JSON.stringify(report);
  check(`Report omits known fixture credentials: ${id}`, !['local-target-a','local-target-b','local-fixture-a','local-fixture-b',tokens.a,tokens.b].some(secret => serialized.includes(secret)));
}
const unmappedToken = await token('unmapped');
const unmapped = await request('/api/v1/targets', unmappedToken);
check('Authenticated principal without grants sees no targets', unmapped.status === 200 && (await unmapped.json()).length === 0);
const unmappedEnvironment = await request('/api/v1/targets/lab-keycloak-a/environment', unmappedToken);
const unmappedEnvironmentBody = await unmappedEnvironment.json();
check('Environment discovery denies authenticated principal without target grants',
  unmappedEnvironment.status === 403 && unmappedEnvironmentBody.code === 'TARGET_NOT_AUTHORIZED');
check('Wrong audience denied', (await request('/api/v1/me', await token('wrong-audience'))).status === 401);
const parts = tokens.a.split('.');
parts[1] = Buffer.from(JSON.stringify({ ...JSON.parse(Buffer.from(parts[1], 'base64url')), sub: 'tampered' })).toString('base64url');
check('Tampered signature denied', (await request('/api/v1/me', parts.join('.'))).status === 401);
const expired = await token('expired');
const exp = JSON.parse(Buffer.from(expired.split('.')[1], 'base64url')).exp;
assert.ok(exp * 1000 - Date.now() < 10000, 'Short-lived fixture must expire promptly');
await new Promise(resolve => setTimeout(resolve, Math.max(0, exp * 1000 - Date.now()) + 2200));
check('Expired real token denied', (await request('/api/v1/me', expired)).status === 401);
console.log(`Identity validation: ${passed} checks passed. Browser token lifecycle, immediate revocation and real cluster acceptance are separate gates.`);
