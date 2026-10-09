// Offline integrity of authored expectations only: no product evaluator or live acceptance.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const AS_OF = '2026-09-19T12:00:00Z';
const DAY_SECONDS = 24 * 60 * 60;
const descriptors = [
  { file: 'password-age.json', scenarioId: 'IAM-06', milestone: 'P1', requirements: ['FR-IAM-003'], count: 13,
    ids: [['IAM06', 13]], decisions: ['WITHIN_POLICY', 'OUTSIDE_POLICY', 'UNKNOWN', 'NOT_APPLICABLE', 'PARTIAL', 'DENY'] },
  { file: 'identity-investigation.json', scenarioId: 'SECOPS-01', milestone: 'P1', requirements: ['FR-IAM-004'], count: 11,
    ids: [['SECOPS01', 11]], decisions: ['INVESTIGATE', 'INCONCLUSIVE', 'INSUFFICIENT_SAMPLES', 'UNAVAILABLE', 'DENY', 'NO_AUTHORITY', 'NO_ACTIVITY', 'UNSUPPORTED_SOURCE'] },
  { file: 'temporary-access.json', scenarioId: 'HLP-01', milestone: 'P2', requirements: ['FR-GOV-002', 'FR-GOV-003'], count: 15,
    ids: [['HLP01', 12], ['SECOPS02', 3]], decisions: ['PLAN_REQUIRED', 'DENY', 'RECONCILE_REQUIRED', 'PARTIAL_REQUIRES_OPERATOR', 'EXPIRY_RECOVERY_REQUIRED', 'CONFLICT', 'LIMITED_VERIFICATION'] },
];
const catalogs = descriptors.map(descriptor => ({ descriptor,
  data: JSON.parse(readFileSync(new URL(descriptor.file, import.meta.url), 'utf8')) }));
const [password, investigation, temporary] = catalogs.map(catalog => catalog.data);
const allCases = catalogs.flatMap(catalog => catalog.data.cases);
const caseById = id => {
  const matches = allCases.filter(entry => entry.id === id);
  assert.equal(matches.length, 1, `Expected exactly one authored case ${id}`);
  return matches[0];
};
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const nonblank = value => typeof value === 'string' && value.trim().length > 0;
const exactKeys = (value, expected) => {
  assert.ok(object(value), 'Expected an object in the fixture catalogue');
  assert.deepEqual(Object.keys(value).sort(), [...expected].sort());
};
const secondsBetween = (later, earlier) => {
  for (const value of [later, earlier]) {
    assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
    assert.ok(Number.isFinite(Date.parse(value)), 'Fixture time must be a valid UTC instant');
  }
  return (Date.parse(later) - Date.parse(earlier)) / 1000;
};

// Resolve only own properties; null/false/zero are real evidence values, not missing paths.
function resolvePointer(input, pointer) {
  assert.equal(typeof pointer, 'string');
  assert.ok(pointer.startsWith('/'), 'Evidence must reference a field, not the whole input');
  assert.doesNotMatch(pointer, /~(?:[^01]|$)/, 'Invalid JSON Pointer escape');
  return pointer.slice(1).split('/').reduce((value, escaped) => {
    const key = escaped.replaceAll('~1', '/').replaceAll('~0', '~');
    assert.ok(value !== null && typeof value === 'object', 'Evidence path traverses a scalar');
    if (Array.isArray(value)) assert.match(key, /^(0|[1-9][0-9]*)$/, 'Invalid JSON Pointer array index');
    assert.ok(Object.hasOwn(value, key), 'Evidence path does not resolve against this case input');
    return value[key];
  }, input);
}

for (const { descriptor, data } of catalogs) {
  test(`catalogue metadata remains planned and synthetic: ${descriptor.scenarioId}`, () => {
    exactKeys(data, ['catalogVersion', 'synthetic', 'status', 'scenarioId', 'milestone', 'requirements', 'context', 'policy', 'cases']);
    assert.equal(data.catalogVersion, '1');
    assert.equal(data.synthetic, true);
    assert.equal(data.status, 'PLANNED');
    assert.equal(data.scenarioId, descriptor.scenarioId);
    assert.equal(data.milestone, descriptor.milestone);
    assert.deepEqual(data.requirements, descriptor.requirements);
    assert.deepEqual(data.context, {
      targetId: 'synthetic-hml-a', realmId: 'synthetic-realm-a', asOf: AS_OF,
      product: { distribution: 'KEYCLOAK', version: '26.7.1', capabilityValidation: 'NOT_RUN' },
    });
    assert.ok(object(data.policy));
    assert.match(data.policy.id, /^synthetic-[a-z0-9-]+$/);
    assert.equal(data.cases.length, descriptor.count);
    const expectedIds = descriptor.ids.flatMap(([prefix, count]) => Array.from({ length: count },
      (_, index) => `${prefix}-${String(index + 1).padStart(2, '0')}`));
    assert.deepEqual(data.cases.map(entry => entry.id), expectedIds);
  });

  for (const entry of data.cases) {
    test(`authored expectation integrity, not product execution: ${entry.id}`, () => {
      exactKeys(entry, ['id', 'title', 'input', 'expected']);
      assert.match(entry.id, /^(?:IAM06|SECOPS01|HLP01|SECOPS02)-[0-9]{2}$/);
      assert.ok(nonblank(entry.title));
      assert.ok(object(entry.input));
      assert.ok(Object.keys(entry.input).length > 0);
      exactKeys(entry.expected, ['decision', 'reason', 'evidencePaths', 'forbiddenClaims', 'grantsAuthority']);
      assert.ok(descriptor.decisions.includes(entry.expected.decision));
      assert.equal(entry.expected.grantsAuthority, false);
      assert.ok(nonblank(entry.expected.reason));
      for (const name of ['evidencePaths', 'forbiddenClaims']) {
        const values = entry.expected[name];
        assert.ok(Array.isArray(values) && values.length > 0);
        assert.ok(values.every(nonblank));
        assert.equal(new Set(values).size, values.length);
      }
      for (const pointer of entry.expected.evidencePaths) resolvePointer(entry.input, pointer);
      for (const forbidden of entry.expected.forbiddenClaims) assert.match(forbidden, /^[A-Z][A-Z0-9_]*$/);
      assert.ok(!entry.expected.forbiddenClaims.includes(entry.expected.decision));
      if (entry.input.product) {
        exactKeys(entry.input.product, ['distribution', 'version', 'capabilityValidation']);
        assert.ok(['KEYCLOAK', 'RHBK'].includes(entry.input.product.distribution));
        assert.ok(nonblank(entry.input.product.version));
        assert.equal(entry.input.product.capabilityValidation, 'NOT_RUN');
      }
    });
  }
}

test('the catalogue contains 39 globally unique authored cases across four scenario families', () => {
  assert.equal(allCases.length, 39);
  assert.equal(new Set(allCases.map(entry => entry.id)).size, 39);
  assert.deepEqual([...new Set(allCases.map(entry => entry.id.split('-')[0]))].sort(), ['HLP01', 'IAM06', 'SECOPS01', 'SECOPS02']);
});

test('every referenced requirement exists exactly once in the roadmap requirements', () => {
  const requirements = readFileSync(new URL('../../docs/requirements/roadmap-requirements.md', import.meta.url), 'utf8');
  const definitions = [...requirements.matchAll(/^### (FR-[A-Z]+-[0-9]{3})\s*$/gm)].map(match => match[1]);
  for (const requirement of catalogs.flatMap(catalog => catalog.data.requirements)) {
    assert.equal(definitions.filter(id => id === requirement).length, 1, `Missing or duplicated requirement ${requirement}`);
  }
});

test('evidence pointer checker preserves false/null/zero and rejects absent, inherited or invalid array paths', () => {
  const sample = { 'a/b': { '~key': [null, false, 0] } };
  assert.equal(resolvePointer(sample, '/a~1b/~0key/0'), null);
  assert.equal(resolvePointer(sample, '/a~1b/~0key/1'), false);
  assert.equal(resolvePointer(sample, '/a~1b/~0key/2'), 0);
  for (const pointer of ['', 'a', '/missing', '/constructor', '/a~2b', '/a~1b/~0key/-', '/a~1b/~0key/01', '/a~1b/~0key/3', '/a~1b/~0key/0/child']) {
    assert.throws(() => resolvePointer(sample, pointer));
  }
});

test('password boundary examples use exact seconds, including equality, one extra second and future time', () => {
  assert.equal(password.policy.maxAgeSeconds, 90 * DAY_SECONDS);
  assert.equal(password.policy.comparison, 'AGE_SECONDS_GREATER_THAN_MAX');
  assert.equal(password.policy.membership, 'EFFECTIVE');
  assert.equal(password.policy.scope, 'ELIGIBLE_HUMANS');
  assert.equal(password.policy.approval, 'ASSUMED_IN_FIXTURE');
  for (const [id, seconds, decision] of [
    ['IAM06-01', 89 * DAY_SECONDS, 'WITHIN_POLICY'], ['IAM06-02', 90 * DAY_SECONDS, 'WITHIN_POLICY'],
    ['IAM06-03', 90 * DAY_SECONDS + 1, 'OUTSIDE_POLICY'], ['IAM06-04', 91 * DAY_SECONDS, 'OUTSIDE_POLICY'],
    ['IAM06-09', -DAY_SECONDS, 'UNKNOWN'],
  ]) {
    const entry = caseById(id);
    assert.equal(secondsBetween(AS_OF, entry.input.credential.changedAt), seconds);
    assert.equal(entry.expected.decision, decision);
  }
  const exception = caseById('IAM06-13');
  assert.equal(secondsBetween(AS_OF, exception.input.credential.changedAt), 91 * DAY_SECONDS);
  assert.ok(secondsBetween(AS_OF, exception.input.exception.expiresAt) > 0);
  assert.equal(exception.expected.decision, 'OUTSIDE_POLICY');
});

test('password unknown, inapplicable and population-partial examples retain different claims', () => {
  const missing = caseById('IAM06-05');
  assert.equal(missing.input.credential.changedAt, null);
  assert.equal(missing.input.retentionDays, 30);
  assert.ok(secondsBetween(AS_OF, missing.input.lastLoginAt) < DAY_SECONDS);
  assert.equal(missing.expected.decision, 'UNKNOWN');
  const ldap = caseById('IAM06-06');
  assert.equal(ldap.input.credential.kind, 'LDAP_PASSWORD');
  assert.equal(ldap.input.credential.externalChangedAt, null);
  assert.equal(ldap.expected.decision, 'UNKNOWN');
  const passwordless = caseById('IAM06-07');
  assert.equal(passwordless.input.credential.passwordAuthenticationAbsent, true);
  assert.equal(passwordless.input.credential.providerCoverage, 'COMPLETE');
  assert.equal(passwordless.expected.decision, 'NOT_APPLICABLE');
  const partial = caseById('IAM06-08');
  assert.equal(partial.input.population.eligibleTotal, null);
  assert.equal(partial.input.population.nextPageAvailable, true);
  assert.equal(partial.expected.decision, 'PARTIAL');
  assert.ok(partial.expected.forbiddenClaims.includes('COMPLIANCE_100_PERCENT'));
  assert.equal(caseById('IAM06-10').input.credential.semantics, 'UNVERIFIED');
  assert.equal(caseById('IAM06-10').expected.decision, 'UNKNOWN');
  assert.equal(caseById('IAM06-12').input.product.distribution, 'RHBK');
  assert.equal(caseById('IAM06-12').input.product.capabilityValidation, 'NOT_RUN');
  assert.equal(caseById('IAM06-12').expected.decision, 'UNKNOWN');
});

test('identity examples distinguish independent observations, duplicate samples, VPN and clock uncertainty', () => {
  assert.equal(investigation.policy.minDistinctEvents, 2);
  assert.equal(investigation.policy.windowSeconds, 600);
  const independent = caseById('SECOPS01-01');
  assert.equal(new Set(independent.input.events.map(event => event.id)).size, 2);
  assert.equal(new Set(independent.input.events.map(event => event.subjectId)).size, 1);
  assert.equal(new Set(independent.input.events.map(event => event.country)).size, 2);
  for (const event of independent.input.events) {
    const age = secondsBetween(AS_OF, event.at);
    assert.ok(age >= 0 && age <= investigation.policy.windowSeconds);
    assert.equal(event.geoQuality, 'ASSUMED_RELIABLE_IN_FIXTURE');
  }
  assert.equal(independent.expected.decision, 'INVESTIGATE');
  assert.ok(independent.expected.forbiddenClaims.includes('COMPROMISE_CONFIRMED'));
  const duplicate = caseById('SECOPS01-03');
  assert.deepEqual(duplicate.input.events[0], duplicate.input.events[1]);
  assert.equal(new Set(duplicate.input.events.map(event => event.id)).size, 1);
  assert.equal(duplicate.expected.decision, 'INSUFFICIENT_SAMPLES');
  assert.equal(caseById('SECOPS01-02').input.networkContext, 'KNOWN_VPN');
  assert.equal(caseById('SECOPS01-02').expected.decision, 'INCONCLUSIVE');
  const uncertain = caseById('SECOPS01-09');
  const interval = secondsBetween(uncertain.input.events[1].at, uncertain.input.events[0].at);
  assert.ok(uncertain.input.clockUncertaintySeconds > interval);
  assert.equal(uncertain.expected.decision, 'INCONCLUSIVE');
});

test('scope denial, collection unavailability, incomplete coverage and observed empty window stay distinct', () => {
  const wrongTarget = caseById('IAM06-11');
  assert.notEqual(wrongTarget.input.evidenceTargetId, wrongTarget.input.authorizedTargetId);
  assert.equal(wrongTarget.input.authorizedTargetId, password.context.targetId);
  assert.equal(wrongTarget.expected.decision, 'DENY');
  const wrongRealm = caseById('SECOPS01-06');
  assert.notEqual(wrongRealm.input.eventRealmId, wrongRealm.input.authorizedRealmId);
  assert.equal(wrongRealm.input.authorizedRealmId, investigation.context.realmId);
  assert.equal(wrongRealm.expected.decision, 'DENY');
  assert.equal(caseById('SECOPS01-05').input.sourceStatus, 'UNAVAILABLE');
  assert.equal(caseById('SECOPS01-05').input.events, null);
  assert.equal(caseById('SECOPS01-05').expected.decision, 'UNAVAILABLE');
  assert.equal(caseById('SECOPS01-10').input.sourceStatus, 'NOT_AUTHORIZED');
  assert.equal(caseById('SECOPS01-10').expected.decision, 'DENY');
  const gap = caseById('SECOPS01-04');
  assert.equal(gap.input.coverage, 'PARTIAL');
  assert.ok(gap.input.retainedWindowSeconds < gap.input.requestedWindowSeconds);
  assert.deepEqual(gap.input.events, []);
  assert.equal(gap.expected.decision, 'INCONCLUSIVE');
  const empty = caseById('SECOPS01-08');
  assert.equal(empty.input.coverage, 'COMPLETE');
  assert.deepEqual(empty.input.events, []);
  assert.equal(empty.input.windowEnd, AS_OF);
  assert.equal(secondsBetween(empty.input.windowEnd, empty.input.windowStart), investigation.policy.windowSeconds);
  assert.equal(empty.expected.decision, 'NO_ACTIVITY');
  assert.equal(caseById('SECOPS01-11').input.source, 'OPERATIONS_PLATFORM_AUDIT');
  assert.equal(caseById('SECOPS01-11').expected.decision, 'UNSUPPORTED_SOURCE');
});

test('temporary-access examples require explicit scope, effective privileges, expiry and independent approval', () => {
  assert.equal(temporary.policy.maxTtlSeconds, DAY_SECONDS);
  assert.deepEqual(temporary.policy.requiredGates, ['DURABLE_EXECUTION', 'HUMAN_APPROVAL', 'RESOURCE_AUTHORIZATION', 'EXPIRY_ENFORCEMENT', 'RECONCILIATION']);
  const proposal = caseById('HLP01-01');
  assert.equal(proposal.input.targetId, temporary.context.targetId);
  assert.equal(proposal.input.realmId, temporary.context.realmId);
  assert.deepEqual(proposal.input.effectiveRoleIds, temporary.policy.allowedRoleIds);
  assert.equal(secondsBetween(proposal.input.expiresAt, AS_OF), DAY_SECONDS);
  assert.equal(proposal.input.approval, null);
  assert.equal(proposal.expected.decision, 'PLAN_REQUIRED');
  assert.equal(caseById('HLP01-02').input.temporaryPassword, true);
  assert.equal(caseById('HLP01-02').input.expiryEnforcement, 'NONE');
  assert.equal(caseById('HLP01-02').expected.decision, 'DENY');
  const misleading = caseById('HLP01-03');
  assert.notEqual(misleading.input.requestedTargetId, misleading.input.resolvedTargetId);
  assert.equal(misleading.expected.decision, 'DENY');
  assert.ok(caseById('HLP01-04').input.effectiveRoleIds.some(role => !temporary.policy.allowedRoleIds.includes(role)));
  assert.equal(caseById('HLP01-04').expected.decision, 'DENY');
  assert.equal(secondsBetween(caseById('HLP01-05').input.expiresAt, AS_OF), 2 * DAY_SECONDS);
  assert.equal(caseById('HLP01-05').expected.decision, 'DENY');
  assert.ok(secondsBetween(AS_OF, caseById('HLP01-06').input.approval.expiresAt) > 0);
  assert.equal(caseById('HLP01-06').expected.decision, 'DENY');
  assert.notEqual(caseById('HLP01-07').input.approvedFingerprint, caseById('HLP01-07').input.currentFingerprint);
  assert.equal(caseById('HLP01-07').expected.decision, 'DENY');
  assert.equal(caseById('HLP01-08').input.approval.actor, 'synthetic-model');
  assert.equal(caseById('HLP01-08').expected.decision, 'DENY');
});

test('uncertain, partial, overdue and containment examples forbid false success and automatic action', () => {
  assert.equal(caseById('HLP01-09').input.remoteAttempt, 'DISPATCHED');
  assert.equal(caseById('HLP01-09').input.remoteResponse, 'TIMEOUT');
  assert.equal(caseById('HLP01-09').input.readBack, 'NOT_RUN');
  assert.equal(caseById('HLP01-09').expected.decision, 'RECONCILE_REQUIRED');
  assert.ok(caseById('HLP01-09').expected.forbiddenClaims.includes('SAFE_TO_RETRY'));
  assert.equal(caseById('HLP01-10').input.expiryPersistence, 'FAILED');
  assert.equal(caseById('HLP01-10').expected.decision, 'PARTIAL_REQUIRES_OPERATOR');
  assert.ok(caseById('HLP01-10').expected.forbiddenClaims.includes('AUTO_DELETE_USER'));
  assert.ok(secondsBetween(AS_OF, caseById('HLP01-11').input.expiresAt) > 0);
  assert.equal(caseById('HLP01-11').input.accountEnabled, true);
  assert.equal(caseById('HLP01-11').expected.decision, 'EXPIRY_RECOVERY_REQUIRED');
  assert.equal(caseById('HLP01-12').input.baselineExists, true);
  assert.equal(caseById('HLP01-12').expected.decision, 'CONFLICT');
  assert.equal(caseById('SECOPS02-01').input.approval, null);
  assert.equal(caseById('SECOPS02-01').expected.decision, 'DENY');
  const disabled = caseById('SECOPS02-02');
  assert.equal(disabled.input.userEnabled, false);
  assert.equal(disabled.input.sessionTermination, 'NOT_VERIFIED');
  assert.equal(disabled.input.issuedTokenValidity, 'UNKNOWN');
  assert.equal(disabled.expected.decision, 'LIMITED_VERIFICATION');
  assert.ok(disabled.expected.forbiddenClaims.includes('ALL_TOKENS_REVOKED'));
  assert.equal(caseById('SECOPS02-03').input.protectedIdentity, true);
  assert.equal(caseById('SECOPS02-03').expected.decision, 'DENY');
});

test('catalogue policies and untrusted event text never confer network, containment or write authority', () => {
  assert.equal(investigation.policy.geoNetworkCalls, false);
  assert.equal(investigation.policy.automaticContainment, false);
  assert.equal(temporary.policy.realWrites, false);
  assert.equal(investigation.policy.source, 'IDENTITY_LOGIN_EVENTS');
  const injection = caseById('SECOPS01-07');
  assert.equal(injection.input.approvalSource, 'UNTRUSTED_EVENT_TEXT');
  assert.equal(injection.expected.decision, 'NO_AUTHORITY');
  assert.ok(injection.expected.forbiddenClaims.includes('APPROVAL_GRANTED'));
  assert.ok(allCases.every(entry => entry.expected.grantsAuthority === false));
});
