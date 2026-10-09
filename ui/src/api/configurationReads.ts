import { apiClient } from './client';

export interface ConfigurationScope {
  scopeId: string;
  targetId: string;
  realm: string;
  kind: 'REALM' | 'CLIENT';
  fields: string[];
}

export interface ConfigurationObservation {
  schemaVersion: '1.0';
  observationId: string;
  scope: ConfigurationScope;
  collectedAt: string;
  source: 'KEYCLOAK_ADMIN_API';
  productVersion: 'UNKNOWN';
  status: 'COMPLETE' | 'PARTIAL';
  facts: Record<string, boolean | null>;
  missingFields: string[];
}

const FIELDS = {
  REALM: ['enabled', 'registrationAllowed', 'resetPasswordAllowed', 'bruteForceProtected', 'verifyEmail'],
  CLIENT: ['enabled', 'publicClient', 'standardFlowEnabled', 'implicitFlowEnabled', 'directAccessGrantsEnabled', 'serviceAccountsEnabled'],
};

function isScope(value: unknown): value is ConfigurationScope {
  if (!value || typeof value !== 'object') return false;
  const scope = value as ConfigurationScope;
  return typeof scope.scopeId === 'string' && /^[a-z][a-z0-9-]{0,63}$/.test(scope.scopeId)
    && typeof scope.targetId === 'string' && /^[a-zA-Z0-9._-]{1,128}$/.test(scope.targetId)
    && typeof scope.realm === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}$/.test(scope.realm)
    && (scope.kind === 'REALM' || scope.kind === 'CLIENT')
    && Array.isArray(scope.fields) && scope.fields.length > 0 && new Set(scope.fields).size === scope.fields.length
    && scope.fields.every(field => typeof field === 'string' && FIELDS[scope.kind].includes(field));
}

export async function fetchConfigurationScopes(): Promise<ConfigurationScope[]> {
  const scopes = await apiClient.get<unknown>('/configuration-reads');
  if (!Array.isArray(scopes) || scopes.length > 100 || !scopes.every(isScope)
      || new Set(scopes.map(scope => scope.scopeId)).size !== scopes.length) {
    throw new Error('Invalid configuration scopes. Reload this page.');
  }
  return scopes;
}

export function validateConfigurationObservation(value: unknown, selected: ConfigurationScope): ConfigurationObservation {
  if (!value || typeof value !== 'object') throw new Error('Invalid configuration observation.');
  const observation = value as ConfigurationObservation;
  const scope = observation.scope;
  if (!isScope(scope) || scope.scopeId !== selected.scopeId || scope.targetId !== selected.targetId
      || scope.realm !== selected.realm || scope.kind !== selected.kind
      || scope.fields.length !== selected.fields.length
      || scope.fields.some((field, index) => field !== selected.fields[index])) {
    throw new Error('Observation scope does not match the selected scope.');
  }
  if (observation.schemaVersion !== '1.0' || typeof observation.observationId !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(observation.observationId)
      || observation.source !== 'KEYCLOAK_ADMIN_API'
      || observation.productVersion !== 'UNKNOWN'
      || typeof observation.collectedAt !== 'string' || !Number.isFinite(Date.parse(observation.collectedAt))
      || !['COMPLETE', 'PARTIAL'].includes(observation.status)
      || !observation.facts || typeof observation.facts !== 'object' || Array.isArray(observation.facts)
      || Object.keys(observation.facts).length !== selected.fields.length
      || !Object.entries(observation.facts).every(([field, fact]) => selected.fields.includes(field)
        && (fact === null || typeof fact === 'boolean'))
      || !Array.isArray(observation.missingFields)
      || new Set(observation.missingFields).size !== observation.missingFields.length
      || !observation.missingFields.every(field => selected.fields.includes(field))) {
    throw new Error('Invalid configuration observation.');
  }
  const unknownFields = selected.fields.filter(field => observation.facts[field] === null);
  if (observation.status !== (unknownFields.length ? 'PARTIAL' : 'COMPLETE')
      || observation.missingFields.length !== unknownFields.length
      || !unknownFields.every(field => observation.missingFields.includes(field))) {
    throw new Error('Inconsistent configuration observation.');
  }
  return observation;
}

export async function inspectConfiguration(scope: ConfigurationScope): Promise<ConfigurationObservation> {
  const observation = await apiClient.get<unknown>(`/configuration-reads/${encodeURIComponent(scope.scopeId)}`);
  return validateConfigurationObservation(observation, scope);
}
