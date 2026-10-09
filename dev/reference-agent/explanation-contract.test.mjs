// Offline regressions: do not invoke a model or repair retained model responses.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import * as core from './core.mjs';

const read = name => readFile(new URL(name, import.meta.url), 'utf8');
const report = JSON.parse(await read('./fixtures/report-partial.json'));
const descriptor = JSON.parse(await read('./profile.json'));
const bundleText = await read('../../docs/development/evidence/agt1-synthetic-model-trial-2026-09-19.json');
const bundle = JSON.parse(bundleText);
const expectedLimits = { maxItemsPerCategory: 20, maxTextUtf16Units: 1000, minReferencesPerItem: 1, maxReferencesPerItem: 10 };
const categories = ['observations', 'hypotheses', 'recommendations'];
const entry = () => ({ text: 'Synthetic observation only.', references: ['/healthStatus'] });
function candidate(source = report) {
  const { profileVersion, targetId, reportId, facts } = core.projectReport(source, source.targetId);
  return { profileVersion, targetId, reportId, facts, observations: [], hypotheses: [], recommendations: [] };
}
const accept = proposal => assert.equal(core.evaluateExplanation(report, report.targetId, proposal).structuralChecks, 'PASSED');
const reject = proposal => assert.throws(() => core.evaluateExplanation(report, report.targetId, proposal), e => e.code === 'INVALID_EXPLANATION');

test('one immutable limits definition is exposed by the evaluator and descriptor', () => {
  assert.deepEqual(core.EXPLANATION_LIMITS, expectedLimits);
  assert(Object.isFrozen(core.EXPLANATION_LIMITS));
  assert.deepEqual(descriptor.explanationLimits, expectedLimits);
});

for (const name of ['INSTRUCTIONS.md', 'README.md']) {
  test(`${name} includes the exact contract text derived from executable limits`, async () => {
    const text = await read(`./${name}`);
    assert.equal(typeof core.explanationContractText, 'function');
    const blocks = [...text.matchAll(/<!-- explanation-contract:start -->\n([\s\S]*?)\n<!-- explanation-contract:end -->/g)];
    assert.equal(blocks.length, 1);
    assert.equal(blocks[0][1], core.explanationContractText());
    assert(text.includes(`0.2.1`));
  });
}

test('empty narrative arrays are valid without proving semantic correctness', () => {
  assert.deepEqual(core.evaluateExplanation(report, report.targetId, candidate()), {
    structuralChecks: 'PASSED', semanticReview: 'REQUIRED', modelEvaluated: false,
  });
});

test('an otherwise valid proposal from profile 0.2.0 is not silently upgraded', () => {
  const p = candidate(); accept(p);
  p.profileVersion = '0.2.0'; reject(p);
});

for (const category of categories) {
  test(`${category}: 20 items accepted, 21 rejected without mutation`, () => {
    const valid = candidate(); valid[category] = Array.from({ length: 20 }, entry); accept(valid);
    const invalid = candidate(); invalid[category] = Array.from({ length: 21 }, entry);
    const before = structuredClone(invalid); reject(invalid); assert.deepEqual(invalid, before);
  });
  test(`${category}: 1 and 10 unique references accepted; 0, 11 and 12 rejected`, () => {
    for (const length of [1, 10, 0, 11, 12]) {
      const p = candidate(); p[category] = [{ text: 'Synthetic only.', references: p.facts.slice(0, length).map(f => f.path) }];
      if (length === 1 || length === 10) accept(p); else reject(p);
    }
  });
  test(`${category}: text bounds use UTF-16, not bytes or code points`, () => {
    for (const [text, valid] of [['x'.repeat(1000), true], ['x'.repeat(1001), false], ['😀'.repeat(500), true], ['😀'.repeat(501), false]]) {
      const p = candidate(); p[category] = [{ ...entry(), text }];
      if (valid) accept(p); else reject(p);
    }
  });
}

test('all C0 and DEL characters, decoded escapes and whitespace-only strings fail', () => {
  for (const code of [...Array.from({ length: 32 }, (_, i) => i), 127]) {
    const p = candidate(); p.observations = [{ ...entry(), text: `x${String.fromCharCode(code)}y` }]; reject(p);
  }
  for (const text of ['', ' ', '\u00a0', JSON.parse('"line\\nbreak"'), JSON.parse('"tab\\tvalue"')]) {
    const p = candidate(); p.observations = [{ ...entry(), text }]; reject(p);
  }
});

test('references are exact strings, unique per item, not across the whole explanation', () => {
  const good = candidate(); good.observations = [entry(), entry()];
  good.recommendations = [{ text: 'Unexecuted suggestion.', references: ['/findingDetails/items/0/finding/evidence/zone~1a~0b'] }]; accept(good);
  for (const references of [['/healthStatus', '/healthStatus'], [null], [1], ['/missing'], ['https://example.invalid'], ['/findingDetails/items/0/finding/evidence/zone/a~b']]) {
    const p = candidate(); p.observations = [{ ...entry(), references }]; reject(p);
  }
});

for (const c of bundle.cases) {
  test(`retained ${c.sourceId} failure stays byte-exact; overflowing prose still fails current limits`, () => {
    const artifact = name => bundle.artifacts.find(a => a.name === name);
    const original = artifact(`${c.id}.explanation.json`);
    assert.equal(createHash('sha256').update(original.text).digest('hex'), original.sha256);
    const raw = JSON.parse(original.text);
    const source = JSON.parse(artifact(`${c.sourceId}.report.json`).text);
    assert.equal(raw.profileVersion, '0.2.0');
    assert.deepEqual(raw.facts, core.projectReport(source, source.targetId).facts);
    assert.deepEqual(raw.observations.flatMap((v, i) => v.references.length > 10 ? [[i, v.references.length]] : []),
      c.sourceId === 'complete-risk' ? [[2, 11], [3, 12]] : [[3, 12]]);
    // Raw 0.2.0 remains invalid (also a version mismatch after the patch). Separately
    // build a NEW synthetic current-profile negative case, never a repaired response.
    assert.throws(() => core.evaluateExplanation(source, source.targetId, raw), e => e.code === 'INVALID_EXPLANATION');
    const synthetic = { ...candidate(source), ...Object.fromEntries(categories.map(k => [k, structuredClone(raw[k])])) };
    const before = structuredClone(synthetic);
    assert.throws(() => core.evaluateExplanation(source, source.targetId, synthetic), e => e.code === 'INVALID_EXPLANATION');
    assert.deepEqual(synthetic, before);
    assert.equal(original.text, artifact(`${c.id}.explanation.json`).text);
  });
}
