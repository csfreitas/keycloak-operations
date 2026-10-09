import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const realm = JSON.parse(readFileSync(new URL('./idp/operations.json', import.meta.url), 'utf8'));
const client = id => realm.clients.find(entry => entry.clientId === id);
const regular = client('keycloak-ops-ui');

test('normal public browser client remains unchanged for the positive control', () => {
  assert.equal(regular.attributes['access.token.lifespan'], '45');
  assert.equal(regular.protocolMappers.length, 1);
  assert.equal(regular.protocolMappers[0].config['included.client.audience'], 'keycloak-operations');
});

test('wrong-audience browser client changes only ID and the audience mapper', () => {
  const wrong = client('keycloak-ops-ui-wrong-audience');
  assert.deepEqual(wrong, { ...regular, clientId: wrong.clientId, protocolMappers: [] });
});

test('expired browser client changes only ID and access-token lifespan', () => {
  const expired = client('keycloak-ops-ui-expired');
  assert.deepEqual(expired, { ...regular, clientId: expired.clientId,
    attributes: { ...regular.attributes, 'access.token.lifespan': '2' } });
});

test('all browser clients retain PKCE, exact loopback callbacks and no confidential/direct grants', () => {
  for (const id of ['keycloak-ops-ui', 'keycloak-ops-ui-wrong-audience', 'keycloak-ops-ui-expired']) {
    const entry = client(id);
    assert.equal(entry.enabled, true);
    assert.equal(entry.publicClient, true);
    assert.equal(entry.standardFlowEnabled, true);
    assert.equal(entry.directAccessGrantsEnabled, false);
    assert.notEqual(entry.implicitFlowEnabled, true);
    assert.notEqual(entry.serviceAccountsEnabled, true);
    assert.equal(entry.secret, undefined);
    assert.equal(entry.attributes['pkce.code.challenge.method'], 'S256');
    assert.deepEqual(entry.redirectUris, ['http://127.0.0.1:18300/']);
    assert.deepEqual(entry.webOrigins, ['http://127.0.0.1:18300']);
    assert.equal(entry.attributes['post.logout.redirect.uris'], 'http://127.0.0.1:18300/');
  }
});
