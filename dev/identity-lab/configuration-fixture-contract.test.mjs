import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const idp = JSON.parse(read('./idp/operations.json'));
const target = JSON.parse(read('./target-a/target.json'));
const properties = Object.fromEntries(read('./configuration.properties').split('\n')
  .filter(line => line && !line.startsWith('#')).map(line => {
    const equal = line.indexOf('=');
    return [line.slice(0, equal), line.slice(equal + 1)];
  }));

test('two human configuration operators have no broad or service-account grant', () => {
  for (const [name, role] of [['rhea-realm', 'config-realm'], ['chris-client', 'config-client']]) {
    const user = idp.users.find(entry => entry.username === name);
    assert.equal(user.enabled, true);
    assert.equal(user.serviceAccountClientId, undefined);
    assert.deepEqual(user.realmRoles, [role]);
    assert.equal(user.clientRoles, undefined);
    assert.deepEqual(user.credentials, [{ type: 'password', value: 'Local-fixture-only-2026!', temporary: false }]);
    assert.ok(idp.roles.realm.some(entry => entry.name === role));
  }
  assert.equal(Object.keys(properties).some(key => key.startsWith('platform.authorization.grants.')), false);
  assert.equal(properties['mcp.read-only'], 'true');
});

test('separate human MCP client requires code/PKCE with exact callback and audience', () => {
  const client = idp.clients.find(entry => entry.clientId === 'keycloak-config-mcp');
  assert.equal(client.publicClient, true);
  assert.equal(client.standardFlowEnabled, true);
  assert.equal(client.directAccessGrantsEnabled, false);
  assert.notEqual(client.implicitFlowEnabled, true);
  assert.notEqual(client.serviceAccountsEnabled, true);
  assert.equal(client.secret, undefined);
  assert.equal(client.attributes['pkce.code.challenge.method'], 'S256');
  assert.deepEqual(client.redirectUris, ['http://127.0.0.1:18300/']);
  assert.deepEqual(client.webOrigins, ['http://127.0.0.1:18300']);
  assert.equal(client.protocolMappers[0].config['included.client.audience'], 'keycloak-operations');
});

test('outbound account is distinct and has only view-realm/view-clients fixture assignments', () => {
  const user = target.users.find(entry => entry.serviceAccountClientId === 'configuration-reader');
  assert.deepEqual(user.clientRoles, { 'realm-management': ['view-realm', 'view-clients'] });
  assert.equal(user.realmRoles, undefined);
  const client = target.clients.find(entry => entry.clientId === 'configuration-reader');
  assert.equal(client.serviceAccountsEnabled, true);
  assert.equal(client.standardFlowEnabled, false);
  assert.equal(client.directAccessGrantsEnabled, false);
  assert.equal(properties['mcp.targets.lab-keycloak-a.keycloak.client-id'], client.clientId);
  assert.equal(properties['mcp.credentials.lab-a.client-secret'], client.secret);
  assert.equal(properties['mcp.targets.lab-keycloak-a.keycloak.url'], 'http://localhost:18080');
  assert.equal(properties['mcp.targets.lab-keycloak-a.observability.metrics.type'], 'NONE');
  assert.equal(properties['mcp.targets.lab-keycloak-b.observability.metrics.type'], 'NONE');
});

test('valid and deliberately stale handles use exact pins and separate client/channel grants', () => {
  const portal = target.clients.find(entry => entry.clientId === 'portal-a');
  const fields = {
    REALM: ['registrationAllowed', 'resetPasswordAllowed', 'bruteForceProtected', 'verifyEmail'],
    CLIENT: ['enabled', 'publicClient', 'standardFlowEnabled', 'directAccessGrantsEnabled', 'serviceAccountsEnabled'],
  };
  for (const [scope, kind] of [['realm-settings', 'REALM'], ['realm-stale', 'REALM'],
    ['portal-settings', 'CLIENT'], ['client-stale', 'CLIENT']]) {
    const property = name => properties[`platform.configuration-reads.scopes.${scope}.${name}`];
    assert.equal(property('target-id'), 'lab-keycloak-a');
    assert.equal(property('realm'), target.realm);
    assert.equal(property('kind'), kind);
    assert.equal(property('role'), kind === 'REALM' ? 'config-realm' : 'config-client');
    assert.equal(property('rest-client-ids'), 'keycloak-ops-ui');
    assert.equal(property('mcp-client-ids'), 'keycloak-config-mcp');
    assert.deepEqual(property('fields').split(','), fields[kind]);
    assert.equal(property('realm-id'), scope === 'realm-stale' ? 'agt2-realm-a-old' : target.id);
    if (kind === 'CLIENT') assert.equal(property('client-id'), scope === 'client-stale' ? 'agt2-portal-a-old' : portal.id);
    for (const field of fields[kind]) assert.equal(typeof (kind === 'REALM' ? target : portal)[field], 'boolean');
  }
  assert.notEqual(target.id, 'agt2-realm-a-old');
  assert.notEqual(portal.id, 'agt2-portal-a-old');
});
