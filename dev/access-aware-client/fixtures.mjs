// Public synthetic fixtures only: no users, credentials, sockets or source environments.
import { randomUUID } from 'node:crypto';
import { ClientError, PROFILE } from './client.mjs';

export const realmScope = Object.freeze({ scopeId: 'synthetic-realm', targetId: 'synthetic-target-a',
  realm: 'synthetic', kind: 'REALM', fields: Object.freeze(['bruteForceProtected', 'enabled', 'verifyEmail']) });
export const clientScope = Object.freeze({ scopeId: 'synthetic-client', targetId: 'synthetic-target-a',
  realm: 'synthetic', kind: 'CLIENT', fields: Object.freeze(['enabled', 'publicClient']) });
export const wire = value => JSON.stringify({ content: [{ type: 'text', text: JSON.stringify(value) }] });
export const catalogueWire = scopes => JSON.stringify({ content: scopes.map(scope => ({ type: 'text', text: JSON.stringify(scope) })) });
export function observation(scope, partial = false) {
  return { schemaVersion: '1.0', observationId: randomUUID(), scope: structuredClone(scope),
    collectedAt: new Date().toISOString(), source: 'KEYCLOAK_ADMIN_API', productVersion: 'UNKNOWN',
    status: partial ? 'PARTIAL' : 'COMPLETE',
    facts: Object.fromEntries(scope.fields.map((field, index) => [field, partial && index === 0 ? null : field === 'enabled'])),
    missingFields: partial ? [scope.fields[0]] : [] };
}
export function createFixtureHost(persona) {
  if (!['rhea', 'chris', 'partial', 'empty'].includes(persona)) throw new ClientError('INVALID_REQUEST');
  const context = Symbol('synthetic session');
  const scopes = persona === 'empty' ? [] : [persona === 'chris' ? clientScope : realmScope];
  return { getContext: () => context, async callTool(request, options) {
    if (options.context !== context || options.signal.aborted) throw new ClientError('UNAUTHENTICATED');
    if (request.name === PROFILE.allowedTools[0]) return catalogueWire(scopes);
    if (request.name !== PROFILE.allowedTools[1]) throw new ClientError('NOT_AUTHORIZED');
    const scope = scopes.find(item => item.scopeId === request.arguments.scopeId);
    if (!scope) throw new ClientError('NOT_AUTHORIZED');
    return wire(observation(scope, persona === 'partial'));
  } };
}
