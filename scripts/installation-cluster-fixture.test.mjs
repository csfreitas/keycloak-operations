import assert from 'node:assert/strict';
import http from 'node:http';
import { test } from 'node:test';
import {
  CLUSTER_TOKEN, CONTROL_TOKEN, MAX_BODY_BYTES, MAX_TRACE_ENTRIES, startFixture,
} from './installation-cluster-fixture.mjs';

async function fixtureFor(t) {
  const fixture = await startFixture({ port: 0 });
  t.after(() => fixture.close());
  const request = async (path, { token = CLUSTER_TOKEN, method = 'GET', body, headers = {} } = {}) => {
    const response = await fetch(`${fixture.url}${path}`, {
      method, headers: { authorization: `Bearer ${token}`, ...headers },
      ...(body === undefined ? {} : { body }), signal: AbortSignal.timeout(5_000),
    });
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  };
  const state = () => request('/__fixture/state', { token: CONTROL_TOKEN });
  const control = body => request('/__fixture/control', {
    token: CONTROL_TOKEN, method: 'POST', body: JSON.stringify(body),
    headers: { 'content-type': 'application/json' },
  });
  return { ...fixture, request, state, control };
}

test('synthetic discovery and UID reads are scoped to the two explicit namespaces', async t => {
  const { request, state, server } = await fixtureFor(t);
  assert.equal(server.address().address, '127.0.0.1');
  for (const suffix of ['a', 'b']) {
    const base = `/apis/apps/v1/namespaces/lab-${suffix}`;
    const result = await request(`${base}/deployments?limit=101`);
    assert.equal(result.status, 200);
    assert.equal(result.body.kind, 'DeploymentList');
    assert.deepEqual(result.body.items.map(item => item.metadata), [
      { namespace: `lab-${suffix}`, name: `sso-${suffix}`, uid: `uid-${suffix}-1` },
      { namespace: `lab-${suffix}`, name: `alternate-${suffix}`, uid: `uid-${suffix}-2` },
    ]);
    const item = await request(`${base}/deployments/sso-${suffix}`);
    assert.equal(item.body.metadata.uid, `uid-${suffix}-1`);
    assert.equal(item.body.apiVersion, 'apps/v1');
    assert.equal(item.body.kind, 'Deployment');
    assert.deepEqual((await request(`${base}/statefulsets?limit=101`)).body.items, []);
    assert.equal((await request(`${base}/statefulsets/missing`)).status, 404);
  }
  assert.equal((await request('/apis/apps/v1/namespaces/default/deployments')).status, 404);
  assert.equal((await request('/apis/apps/v1/namespaces/lab-a/deployments/sso-b')).status, 404);
  assert.deepEqual((await request('/apis')).body.groups.map(group => group.name), ['apps']);
  for (const path of ['/apis/k8s.keycloak.org', '/apis/k8s.keycloak.org/v2beta1/namespaces/lab-a/keycloaks']) {
    assert.equal((await request(path)).status, 404);
  }
  for (const path of ['/api', '/api/v1', '/apis/apps', '/apis/apps/v1', '/version']) {
    assert.equal((await request(path)).status, 200);
  }
  const evidence = (await state()).body;
  assert.equal(evidence.synthetic, true);
  assert.equal(evidence.clusterWrites, 0);
  assert.equal(evidence.clusterReads, 18);
  assert.equal(evidence.trace.length, 18);
  assert.equal(evidence.readRequests[0].path, '/apis/apps/v1/namespaces/lab-a/deployments?limit=101');
  assert.ok(evidence.trace.every(entry => Object.keys(entry).sort().join(',') === 'method,path,status'));
});

test('control authentication is separate and cluster credentials cannot mutate fixture state', async t => {
  const { request, state } = await fixtureFor(t);
  assert.equal((await request('/apis', { token: 'private-value-must-not-appear' })).status, 401);
  assert.equal((await request('/apis', { token: CONTROL_TOKEN })).status, 401);
  assert.equal((await request('/__fixture/state')).status, 401);
  assert.equal((await request('/__fixture/control', {
    method: 'POST', body: '{"operation":"deny","enabled":true}',
    headers: { 'content-type': 'application/json' },
  })).status, 401);
  const evidence = (await state()).body;
  assert.equal(evidence.permissionDenied, false);
  assert.equal(evidence.clusterReads, 2);
  const serialized = JSON.stringify(evidence);
  for (const value of ['authorization', CLUSTER_TOKEN, CONTROL_TOKEN, 'private-value-must-not-appear']) {
    assert.equal(serialized.includes(value), false);
  }
});

test('cluster mutation attempts are rejected, counted and never applied', async t => {
  const { request, state } = await fixtureFor(t);
  const path = '/apis/apps/v1/namespaces/lab-a/deployments/sso-a';
  for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
    const response = await request(path, {
      method, body: '{"metadata":{"uid":"uid-overwrite"}}',
      headers: { 'content-type': 'application/json' },
    });
    assert.equal(response.status, 405);
  }
  assert.equal((await request(path, { method: 'DELETE', token: 'invalid' })).status, 401);
  assert.equal((await request(path)).body.metadata.uid, 'uid-a-1');
  const evidence = (await state()).body;
  assert.equal(evidence.clusterWrites, 5);
  assert.equal(evidence.clusterReads, 1);
  assert.deepEqual(evidence.trace.map(entry => entry.method), ['POST', 'PUT', 'PATCH', 'DELETE', 'DELETE', 'GET']);
});

test('UID replacement, permission denial and reset support negative confirmation scenarios', async t => {
  const { request, state, control } = await fixtureFor(t);
  const path = '/apis/apps/v1/namespaces/lab-a/deployments/sso-a';
  assert.equal((await control({ operation: 'replace', namespace: 'lab-a', name: 'sso-a', uid: 'uid-a-replaced' })).status, 200);
  assert.equal((await request(path)).body.metadata.uid, 'uid-a-replaced');
  assert.equal((await request('/apis/apps/v1/namespaces/lab-b/deployments/sso-b')).body.metadata.uid, 'uid-b-1');
  assert.equal((await control({ operation: 'deny', enabled: true })).status, 200);
  assert.equal((await request(path)).status, 403);
  assert.equal((await request('/apis')).status, 403);
  assert.equal((await state()).body.permissionDenied, true);
  assert.equal((await control({ operation: 'deny', enabled: false })).status, 200);
  assert.equal((await request(path)).status, 200);
  const reset = await control({ operation: 'reset' });
  assert.equal(reset.status, 200);
  assert.equal(reset.body.namespaces['lab-a']['sso-a'], 'uid-a-1');
  assert.equal(reset.body.permissionDenied, false);
  assert.equal(reset.body.clusterWrites, 0);
  assert.equal(reset.body.clusterReads, 0);
  assert.deepEqual(reset.body.trace, []);
  assert.deepEqual(reset.body.readRequests, []);
  assert.equal(reset.body.droppedTraceEntries, 0);
});

test('invalid and oversized control bodies cannot change fixture state', async t => {
  const { request, control, state, url } = await fixtureFor(t);
  for (const body of [null, [], {}, { operation: 'reset', extra: true },
    { operation: 'deny', enabled: 'true' },
    { operation: 'replace', namespace: 'foreign', name: 'sso-a', uid: 'uid-other' },
    { operation: 'replace', namespace: 'lab-a', name: 'sso-b', uid: 'uid-other' },
    { operation: 'replace', namespace: 'lab-a', name: 'sso-a', uid: 'uid-b-1' },
    { operation: 'replace', namespace: '__proto__', name: 'sso-a', uid: 'uid-other' },
    { operation: 'replace', namespace: { toString: null }, name: 'sso-a', uid: 'uid-other' },
    { operation: 'replace', namespace: 'lab-a', name: { toString: null }, uid: 'uid-other' },
    { operation: 'replace', namespace: 'lab-a', name: 'sso-a', uid: 'x'.repeat(200) },
  ]) assert.equal((await control(body)).status, 400);
  assert.equal((await request('/__fixture/control', {
    token: CONTROL_TOKEN, method: 'POST', body: 'not json', headers: { 'content-type': 'application/json' },
  })).status, 400);
  assert.equal((await request('/__fixture/control', {
    token: CONTROL_TOKEN, method: 'POST', body: '{}',
  })).status, 415);
  assert.equal((await request('/__fixture/control', {
    token: CONTROL_TOKEN, method: 'POST', body: 'x'.repeat(MAX_BODY_BYTES + 1),
    headers: { 'content-type': 'application/json' },
  })).status, 413);
  const chunkedStatus = await new Promise((resolveResponse, rejectResponse) => {
    const request = http.request(`${url}/__fixture/control`, {
      method: 'POST', headers: {
        authorization: `Bearer ${CONTROL_TOKEN}`, 'content-type': 'application/json',
        'transfer-encoding': 'chunked',
      },
    }, response => { response.resume(); response.on('end', () => resolveResponse(response.statusCode)); });
    request.on('error', rejectResponse);
    request.setTimeout(5_000, () => request.destroy(new Error('Fixture test timed out')));
    request.write('x'.repeat(MAX_BODY_BYTES));
    request.end('x');
  });
  assert.equal(chunkedStatus, 413);
  assert.equal((await request('/__fixture/state?unexpected=true', { token: CONTROL_TOKEN })).status, 400);
  assert.equal((await request('/__fixture/state', { token: CONTROL_TOKEN, method: 'POST' })).status, 405);
  const evidence = (await state()).body;
  assert.equal(evidence.namespaces['lab-a']['sso-a'], 'uid-a-1');
  assert.equal(evidence.permissionDenied, false);
  assert.equal(evidence.clusterWrites, 0);
});

test('request evidence remains bounded and reports dropped entries', async t => {
  const { request, state } = await fixtureFor(t);
  const requestCount = MAX_TRACE_ENTRIES + 3;
  for (let offset = 0; offset < requestCount; offset += 16) {
    const count = Math.min(16, requestCount - offset);
    const responses = await Promise.all(Array.from({ length: count }, () => request('/apis')));
    assert.ok(responses.every(response => response.status === 200));
  }
  assert.equal((await request(`/apis?oversized=${'x'.repeat(2_048)}`)).status, 414);
  const evidence = (await state()).body;
  assert.equal(evidence.trace.length, MAX_TRACE_ENTRIES);
  assert.equal(evidence.readRequests.length, MAX_TRACE_ENTRIES);
  assert.equal(evidence.clusterReads, requestCount + 1);
  assert.equal(evidence.droppedTraceEntries, 4);
  assert.equal(evidence.trace.at(-1).path, '[request target exceeded fixture bound]');
});

test('invalid listening ports are rejected before creating a server', async () => {
  for (const port of [-1, 65536, 1.5, '18590']) {
    await assert.rejects(startFixture({ port }), /Invalid fixture port/);
  }
});
