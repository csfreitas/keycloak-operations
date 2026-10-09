// Report-local projection only. Returned text is untrusted data, never tool instructions.
import { MAX_PACKET_BYTES } from './packet-budget.mjs';

export const FINDING_DETAILS_VERSION = '1.0';
export const FINDING_LIMITS = Object.freeze({
  maxFindings: 20, maxDepth: 6, maxNodes: 256, maxTextCharacters: 8192,
});
const FIELDS = [
  'id', 'title', 'category', 'severity', 'status', 'description', 'impact',
  'recommendation', 'evidence', 'references', 'subjectType', 'subjectId', 'subjectName',
];
const ENVELOPE_FIELDS = [
  'version', 'reportId', 'targetId', 'assessmentId', 'availability', 'totalFindings',
  'returnedFindings', 'omittedFindings', 'limits', 'items',
];
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const STATUSES = ['OPEN', 'PASS', 'WARNING', 'FAIL', 'NOT_EVALUATED', 'SKIPPED'];

function require(condition) {
  if (!condition) {
    const error = new Error('INVALID_REPORT');
    error.code = 'INVALID_REPORT';
    throw error;
  }
}
function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
// JSON owns only enumerable data properties. Do not invoke getters or copy prototypes.
function entries(value) {
  const descriptors = Object.getOwnPropertyDescriptors(value);
  const keys = Reflect.ownKeys(descriptors);
  require(keys.every(key => typeof key === 'string' && descriptors[key].enumerable
    && Object.hasOwn(descriptors[key], 'value')));
  return keys.map(key => [key, descriptors[key].value]);
}
function exact(value, fields) {
  require(object(value));
  const keys = entries(value).map(([key]) => key);
  require(keys.length === fields.length && fields.every(key => keys.includes(key)));
}
function array(value, maxLength = FINDING_LIMITS.maxNodes) {
  require(Array.isArray(value) && value.length <= maxLength);
  const descriptors = Object.getOwnPropertyDescriptors(value);
  const keys = Reflect.ownKeys(descriptors);
  require(keys.length === value.length + 1 && keys.includes('length'));
  for (let index = 0; index < value.length; index++) {
    const descriptor = descriptors[String(index)];
    require(descriptor && descriptor.enumerable && Object.hasOwn(descriptor, 'value'));
  }
}
function count(value) { return Number.isSafeInteger(value) && value >= 0; }
function pointer(key) { return key.replaceAll('~', '~0').replaceAll('/', '~1'); }

function appendFinding(finding, path, add) {
  exact(finding, FIELDS);
  for (const field of FIELDS.filter(field => !['evidence', 'references'].includes(field))) {
    require(finding[field] === null || typeof finding[field] === 'string');
  }
  require(finding.severity === null || SEVERITIES.includes(finding.severity));
  require(finding.status === null || STATUSES.includes(finding.status));
  require(finding.evidence === null || object(finding.evidence));
  if (finding.references !== null) {
    array(finding.references);
    require(finding.references.every(reference => typeof reference === 'string'));
  }
  let nodes = 0;
  let characters = 0;
  const ancestors = new Set();
  const addText = text => {
    characters += text.length; // UTF-16 code units, matching Java String.length().
    require(characters <= FINDING_LIMITS.maxTextCharacters);
  };
  function visit(value, location, depth) {
    require(depth <= FINDING_LIMITS.maxDepth && ++nodes <= FINDING_LIMITS.maxNodes);
    if (value === null || typeof value === 'boolean') {
      add({ path: location, value });
    } else if (typeof value === 'string') {
      addText(value);
      add({ path: location, value });
    } else if (typeof value === 'number') {
      require(Number.isFinite(value) && (!Number.isInteger(value) || Number.isSafeInteger(value)));
      add({ path: location, value });
    } else {
      require((Array.isArray(value) || object(value)) && !ancestors.has(value));
      ancestors.add(value);
      if (Array.isArray(value)) {
        array(value);
        if (value.length === 0) add({ path: location, value: Object.freeze([]) });
        value.forEach((child, index) => visit(child, `${location}/${index}`, depth + 1));
      } else {
        const children = entries(value);
        require(children.length <= FINDING_LIMITS.maxNodes - nodes);
        if (children.length === 0) add({ path: location, value: Object.freeze({}) });
        for (const [key, child] of children) {
          addText(key);
          visit(child, `${location}/${pointer(key)}`, depth + 1);
        }
      }
      ancestors.delete(value);
    }
  }
  visit(finding, path, 0);
}

/** Strict findingDetails 1.0. Never repairs an invalid envelope or joins target history. */
export function projectFindingDetails(report) {
  const details = report.findingDetails;
  exact(details, ENVELOPE_FIELDS);
  require(details.version === FINDING_DETAILS_VERSION);
  require(details.reportId === report.reportId && details.targetId === report.targetId
    && details.assessmentId === (report.assessment?.assessmentId ?? null));
  exact(details.limits, Object.keys(FINDING_LIMITS));
  for (const [key, value] of Object.entries(FINDING_LIMITS)) require(details.limits[key] === value);
  array(details.items, FINDING_LIMITS.maxFindings);
  require(count(details.returnedFindings) && details.returnedFindings === details.items.length
    && details.returnedFindings <= FINDING_LIMITS.maxFindings);
  if (details.availability === 'UNAVAILABLE') {
    require(details.totalFindings === null && details.omittedFindings === null && details.returnedFindings === 0);
    require(report.assessment == null || report.assessment.findingCount === null);
  } else {
    require(details.availability === 'AVAILABLE' && report.assessment != null);
    require(count(details.totalFindings) && details.totalFindings === report.assessment.findingCount);
    require(details.returnedFindings <= details.totalFindings && count(details.omittedFindings)
      && details.omittedFindings === details.totalFindings - details.returnedFindings);
  }
  const facts = [];
  let factBytes = 2; // JSON array delimiters. Include each repeated/escaped path.
  const add = fact => {
    const bytes = Buffer.byteLength(JSON.stringify(fact), 'utf8');
    const nextBytes = factBytes + bytes + (facts.length === 0 ? 0 : 1);
    require(nextBytes <= MAX_PACKET_BYTES);
    factBytes = nextBytes;
    facts.push(fact);
  };
  for (const field of ENVELOPE_FIELDS.filter(field => !['limits', 'items'].includes(field))) {
    add({ path: `/findingDetails/${field}`, value: details[field] });
  }
  for (const field of Object.keys(FINDING_LIMITS)) {
    add({ path: `/findingDetails/limits/${field}`, value: details.limits[field] });
  }
  let previousIndex = -1;
  details.items.forEach((item, index) => {
    exact(item, ['sourceIndex', 'finding']);
    require(count(item.sourceIndex) && item.sourceIndex > previousIndex
      && item.sourceIndex < Math.min(FINDING_LIMITS.maxFindings, details.totalFindings));
    previousIndex = item.sourceIndex;
    add({ path: `/findingDetails/items/${index}/sourceIndex`, value: item.sourceIndex });
    appendFinding(item.finding, `/findingDetails/items/${index}/finding`, add);
  });
  if (details.items.length === 0) add({ path: '/findingDetails/items', value: Object.freeze([]) });
  return Object.freeze(facts.map(fact => Object.freeze(fact)));
}
