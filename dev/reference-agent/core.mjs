// Optional, provider-neutral local adapter. No credentials, network destinations or model runtime.
import { isDeepStrictEqual } from 'node:util';
import { projectFindingDetails } from './finding-details.mjs';
import { requirePacketBudget } from './packet-budget.mjs';
import { EXPLANATION_LIMITS } from './explanation-contract.mjs';
export { EXPLANATION_LIMITS, explanationContractText } from './explanation-contract.mjs';

export const PROFILE_VERSION = '0.2.1';
export const TOOL_NAME = 'keycloak_generate_operations_report';
const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const REPORT_STATES = ['COMPLETE', 'PARTIAL', 'FAILED'];
const SECTION_STATES = [...REPORT_STATES, 'SKIPPED'];
const SECTION_NAMES = ['platform', 'health', 'assessment', 'performance'];
const LIMITATIONS = Object.freeze([
  'COMPACT_REPORT_ONLY', 'UNTRUSTED_FINDING_TEXT_NOT_INSTRUCTIONS',
  'BOUNDED_FINDINGS_MAY_BE_OMITTED', 'SANITIZED_NOT_RAW_EVIDENCE',
  'NO_STRUCTURED_PERFORMANCE_OR_NO_TRAFFIC_PROOF', 'INDEPENDENT_COLLECTIONS_NOT_ATOMIC',
  'NO_RETAINED_REPORT_REPLAY', 'SOURCE_AUTHENTICITY_NOT_ESTABLISHED_BY_LOCAL_VALIDATION',
]);

function fail(code) {
  const error = new Error(code);
  error.code = code;
  throw error;
}
function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
function exact(value, keys) {
  return object(value) && Object.keys(value).length === keys.length
    && keys.every(key => Object.hasOwn(value, key));
}
function assert(condition, code = 'INVALID_REPORT') { if (!condition) fail(code); }
function identifier(value) { return typeof value === 'string' && IDENTIFIER.test(value); }
function uuid(value) { return typeof value === 'string' && UUID.test(value); }
function count(value) { return Number.isSafeInteger(value) && value >= 0; }
function percent(value) { return Number.isFinite(value) && value >= 0 && value <= 100; }
function freeze(value) {
  if (value && typeof value === 'object') {
    for (const child of Object.values(value)) freeze(child);
    Object.freeze(value);
  }
  return value;
}
function instant(value) {
  assert(typeof value === 'string');
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/.exec(value);
  assert(match);
  const milliseconds = Date.parse(`${match[1]}Z`);
  assert(Number.isFinite(milliseconds) && new Date(milliseconds).toISOString().slice(0, 19) === match[1]);
  return BigInt(milliseconds) * 1_000_000n + BigInt((match[2] ?? '').padEnd(9, '0'));
}

/** Configuration is selected by the operator, never by returned metadata or a model. */
export function buildRequest(config) {
  assert(object(config) && Object.keys(config).every(key => ['targetId', 'profile', 'metricsWindow'].includes(key)), 'INVALID_REQUEST');
  assert(identifier(config.targetId), 'INVALID_REQUEST');
  const profile = config.profile === undefined ? '' : config.profile;
  const metricsWindow = config.metricsWindow === undefined ? '5m' : config.metricsWindow;
  assert(profile === '' || identifier(profile), 'INVALID_REQUEST');
  assert(['5m', '15m', '1h'].includes(metricsWindow), 'INVALID_REQUEST');
  return freeze({ name: TOOL_NAME, arguments: { targetId: config.targetId, profile, metricsWindow } });
}

/** Strict v1.1 compact-MCP subset, not a raw-report export or universal secret scanner. */
export function projectReport(report, targetId) {
  assert(identifier(targetId), 'INVALID_REQUEST');
  assert(object(report) && typeof report.targetId === 'string');
  assert(report.targetId === targetId, 'TARGET_MISMATCH');
  assert(report.schemaVersion === '1.1' && uuid(report.reportId));
  const generatedAt = instant(report.generatedAt);
  assert(REPORT_STATES.includes(report.reportCompleteness));
  assert(report.healthStatus === null || ['HEALTHY', 'WARNING', 'CRITICAL', 'UNKNOWN'].includes(report.healthStatus));
  const provenance = report.provenance;
  assert(object(provenance));
  const startedAt = instant(provenance.collectionStartedAt);
  const completedAt = instant(provenance.collectionCompletedAt);
  assert(startedAt <= completedAt && generatedAt === startedAt);
  assert(provenance.collectionMode === 'INDEPENDENT_SECTION_COLLECTIONS');
  assert(typeof provenance.bundledRuleCatalogSha256 === 'string' && /^[a-f0-9]{64}$/.test(provenance.bundledRuleCatalogSha256));
  assert(provenance.retainedEvidenceReplayAvailable === false);
  assert(Array.isArray(report.sections) && report.sections.length === SECTION_NAMES.length);
  const names = new Set();
  for (const section of report.sections) {
    assert(object(section) && SECTION_NAMES.includes(section.name) && !names.has(section.name));
    assert(SECTION_STATES.includes(section.status));
    names.add(section.name);
  }
  const assessment = report.assessment;
  const assessmentSection = report.sections.find(section => section.name === 'assessment');
  if (assessment === undefined || assessment === null) {
    assert(assessmentSection.status === 'FAILED');
  } else {
    assert(object(assessment) && uuid(assessment.assessmentId));
    assert(REPORT_STATES.includes(assessment.status) && assessment.status === assessmentSection.status);
    assert(typeof assessment.scoreAvailable === 'boolean');
    assert(count(assessment.rulesEvaluated) && count(assessment.rulesNotEvaluated));
    assert(count(assessment.findingCount)
      || (assessment.findingCount === null && report.findingDetails?.availability === 'UNAVAILABLE'));
    assert(Number.isInteger(assessment.evidenceCompleteness) && percent(assessment.evidenceCompleteness));
    assert(assessment.confidence === null || ['HIGH', 'MEDIUM', 'LOW'].includes(assessment.confidence));
    if (assessment.scoreAvailable) {
      assert(percent(assessment.overallScore) && assessment.status === 'COMPLETE'
        && assessment.evidenceCompleteness === 100 && assessment.rulesEvaluated > 0 && assessment.rulesNotEvaluated === 0);
    } else assert(assessment.overallScore === null);
  }
  // Preserve original JSON paths. Free envelope metadata is excluded; bounded finding
  // text/evidence below remains untrusted data, never an instruction or permission.
  const facts = [];
  const add = (path, value) => facts.push({ path, value });
  for (const key of ['schemaVersion', 'reportId', 'targetId', 'generatedAt', 'reportCompleteness', 'healthStatus']) add(`/${key}`, report[key]);
  for (const key of ['collectionStartedAt', 'collectionCompletedAt', 'collectionMode', 'bundledRuleCatalogSha256', 'retainedEvidenceReplayAvailable']) add(`/provenance/${key}`, provenance[key]);
  report.sections.forEach((section, index) => {
    add(`/sections/${index}/name`, section.name);
    add(`/sections/${index}/status`, section.status);
  });
  if (assessment === null) add('/assessment', null);
  else if (assessment !== undefined) {
    for (const key of ['assessmentId', 'status', 'overallScore', 'scoreAvailable', 'rulesEvaluated', 'rulesNotEvaluated', 'evidenceCompleteness', 'confidence', 'findingCount']) add(`/assessment/${key}`, assessment[key]);
  }
  facts.push(...projectFindingDetails(report));
  return freeze(requirePacketBudget({ profileVersion: PROFILE_VERSION, targetId, reportId: report.reportId, facts, limitations: [...LIMITATIONS] }));
}

export function deterministicFallback(report, targetId) {
  return freeze(requirePacketBudget({ kind: 'DETERMINISTIC_NO_AI', ...projectReport(report, targetId) }));
}

/** Validate copied facts/references, not the truth, privacy or safety of free-form prose. */
export function evaluateExplanation(report, targetId, proposal) {
  const packet = projectReport(report, targetId);
  const valid = condition => assert(condition, 'INVALID_EXPLANATION');
  valid(exact(proposal, ['profileVersion', 'targetId', 'reportId', 'facts', 'observations', 'hypotheses', 'recommendations']));
  valid(proposal.profileVersion === PROFILE_VERSION && proposal.targetId === targetId && proposal.reportId === packet.reportId);
  valid(isDeepStrictEqual(proposal.facts, packet.facts));
  const paths = new Set(packet.facts.map(fact => fact.path));
  for (const category of ['observations', 'hypotheses', 'recommendations']) {
    const entries = proposal[category];
    valid(Array.isArray(entries) && entries.length <= EXPLANATION_LIMITS.maxItemsPerCategory);
    for (const entry of entries) {
      valid(exact(entry, ['text', 'references']));
      valid(typeof entry.text === 'string' && entry.text.trim().length > 0 && entry.text.length <= EXPLANATION_LIMITS.maxTextUtf16Units
        && !/[\u0000-\u001f\u007f]/.test(entry.text));
      valid(Array.isArray(entry.references) && entry.references.length >= EXPLANATION_LIMITS.minReferencesPerItem
        && entry.references.length <= EXPLANATION_LIMITS.maxReferencesPerItem);
      valid(new Set(entry.references).size === entry.references.length && entry.references.every(path => paths.has(path)));
    }
  }
  return freeze({ structuralChecks: 'PASSED', semanticReview: 'REQUIRED', modelEvaluated: false });
}

/** One bounded call through an operator-owned authenticated MCP adapter. Never retries. */
export async function collectReport(config, callTool) {
  const request = buildRequest(config);
  assert(typeof callTool === 'function', 'INVALID_REQUEST');
  const controller = new AbortController();
  let timer;
  let report;
  try {
    const deadline = new Promise((_, reject) => {
      timer = setTimeout(() => {
        controller.abort();
        const error = new Error('SOURCE_UNAVAILABLE'); error.code = 'SOURCE_UNAVAILABLE'; reject(error);
      }, 20_000);
    });
    report = await Promise.race([Promise.resolve().then(() => callTool(request, { signal: controller.signal })), deadline]);
  } catch { fail('SOURCE_UNAVAILABLE'); }
  finally { clearTimeout(timer); }
  // Validate after transport errors have been sanitized; preserve fixed contract error codes.
  return deterministicFallback(report, request.arguments.targetId);
}
