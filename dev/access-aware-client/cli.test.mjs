import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const cli = fileURLToPath(new URL('./cli.mjs', import.meta.url));
const run = args => spawnSync(process.execPath, [cli, ...args], { encoding: 'utf8', timeout: 5000,
  env: { PATH: process.env.PATH, LANG: 'C' } });
for (const [persona, scope, state] of [['rhea', undefined, 'CLARIFICATION_REQUIRED'],
  ['rhea', 'synthetic-realm', 'OBSERVED'], ['chris', 'synthetic-client', 'OBSERVED'],
  ['partial', 'synthetic-realm', 'OBSERVED'], ['empty', undefined, 'NO_AUTHORIZED_SCOPES']]) {
  test(`offline CLI ${persona} ${scope ?? 'catalogue'}`, () => {
    const result = run(['demo', persona, ...scope ? [scope] : []]);
    assert.equal(result.status, 0, result.stderr); assert.equal(result.stderr, '');
    const out = JSON.parse(result.stdout);
    assert.equal(out.fixture, 'SYNTHETIC_OFFLINE_ONLY');
    assert.equal(out.answer.state, state);
    assert.equal(out.answer.mode, 'DETERMINISTIC_NO_AI');
    if (persona === 'partial') assert.equal(out.answer.observations[0].status, 'PARTIAL');
  });
}
for (const args of [[], ['demo'], ['live', 'rhea'], ['demo', 'unknown'],
  ['demo', 'rhea', 'synthetic-client'], ['demo', 'rhea', 'http://CANARY_SECRET'],
  ['demo', 'rhea', 'synthetic-realm', '--token=CANARY_SECRET']]) {
  test(`CLI denies unsupported input without echo ${args.length} ${args[1] ?? ''}`, () => {
    const result = run(args);
    assert.equal(result.status, 1); assert.equal(result.stdout, '');
    assert.match(result.stderr, /^DEMO_FAILED:/); assert.ok(!result.stderr.includes('CANARY_SECRET'));
  });
}
