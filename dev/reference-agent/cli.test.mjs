// Offline subprocess tests. Every temporary input is synthetic and removed by its owner.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PROFILE_VERSION, deterministicFallback, projectReport } from './core.mjs';

const cliPath = fileURLToPath(new URL('./cli.mjs', import.meta.url));
const fixturePath = fileURLToPath(new URL('./fixtures/report-partial.json', import.meta.url));
const fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
const targetId = fixture.targetId;
const rejectedMessage = 'Reference-agent input rejected. Use fallback <target-id> <report.json> or evaluate <target-id> <report.json> <proposal.json>.\n';
const canary = 'SYNTHETIC_UNREVIEWED_INPUT_CANARY';

function run(args) {
  return spawnSync(process.execPath, [cliPath, ...args], {
    encoding: 'utf8', timeout: 5000, maxBuffer: 131_072,
  });
}

function completed(result, status) {
  assert.equal(result.error, undefined, 'CLI must complete before the subprocess deadline');
  assert.equal(result.signal, null, 'CLI must exit normally, not be killed');
  assert.equal(result.status, status);
}

function rejected(result) {
  completed(result, 1);
  assert.equal(result.stdout, '');
  assert.equal(result.stderr, rejectedMessage);
  assert.ok(!result.stderr.includes(canary));
}

async function withTemporaryDirectory(action) {
  const directory = await mkdtemp(join(tmpdir(), 'kcops-agt1-cli-'));
  try {
    return await action(directory);
  } finally {
    // The exact directory was created by this test; no shared or pre-existing path is removed.
    await rm(directory, { recursive: true, force: true });
  }
}

function validProposal() {
  return {
    profileVersion: PROFILE_VERSION,
    targetId,
    reportId: fixture.reportId,
    facts: structuredClone(projectReport(fixture, targetId).facts),
    observations: [{ text: `${canary}: completeness remains partial.`, references: ['/reportCompleteness'] }],
    hypotheses: [],
    recommendations: [],
  };
}

test('CLI fallback returns the exact deterministic projection of the actual synthetic fixture', () => {
  const result = run(['fallback', targetId, fixturePath]);
  completed(result, 0);
  assert.equal(result.stderr, '');
  assert.deepEqual(JSON.parse(result.stdout), deterministicFallback(fixture, targetId));
  assert.ok(!result.stdout.includes(fixture.markdown));
});

test('CLI evaluate returns structural review flags without echoing unreviewed narrative', async () => {
  await withTemporaryDirectory(async directory => {
    const proposalPath = join(directory, `${canary}-proposal.json`);
    await writeFile(proposalPath, JSON.stringify(validProposal()));
    const result = run(['evaluate', targetId, fixturePath, proposalPath]);
    completed(result, 0);
    assert.equal(result.stderr, '');
    assert.deepEqual(JSON.parse(result.stdout), {
      structuralChecks: 'PASSED', semanticReview: 'REQUIRED', modelEvaluated: false,
    });
    assert.ok(!result.stdout.includes(canary));
  });
});

test('CLI evaluate rejects altered facts and never echoes proposal prose or its path', async () => {
  await withTemporaryDirectory(async directory => {
    const proposalPath = join(directory, `${canary}-altered.json`);
    const proposal = validProposal();
    proposal.facts.find(fact => fact.path === '/reportCompleteness').value = 'COMPLETE';
    await writeFile(proposalPath, JSON.stringify(proposal));
    rejected(run(['evaluate', targetId, fixturePath, proposalPath]));
  });
});

test('CLI rejects malformed source and proposal JSON without parser excerpts or file names', async () => {
  await withTemporaryDirectory(async directory => {
    const malformedPath = join(directory, `${canary}-malformed.json`);
    await writeFile(malformedPath, `{"private":"${canary}", malformed JSON`);
    rejected(run(['fallback', targetId, malformedPath]));
    rejected(run(['evaluate', targetId, fixturePath, malformedPath]));
  });
});

test('CLI rejects invalid UTF-8 instead of accepting replacement characters in JSON metadata', async () => {
  await withTemporaryDirectory(async directory => {
    const malformedPath = join(directory, `${canary}-utf8.json`);
    const prefix = Buffer.from(`{"private":"${canary}`);
    await writeFile(malformedPath, Buffer.concat([prefix, Buffer.from([0xc3, 0x28]), Buffer.from('"}') ]));
    rejected(run(['fallback', targetId, malformedPath]));
  });
});

test('CLI missing source and proposal paths produce only the fixed safe usage error', async () => {
  await withTemporaryDirectory(async directory => {
    const absentPath = join(directory, `${canary}-absent.json`);
    rejected(run(['fallback', targetId, absentPath]));
    rejected(run(['evaluate', targetId, fixturePath, absentPath]));
  });
});

test('CLI rejects source and proposal files larger than one MiB within the subprocess deadline', async () => {
  await withTemporaryDirectory(async directory => {
    const oversizedPath = join(directory, `${canary}-oversized.json`);
    await writeFile(oversizedPath, Buffer.alloc(1_048_577, 0x20));
    rejected(run(['fallback', targetId, oversizedPath]));
    rejected(run(['evaluate', targetId, fixturePath, oversizedPath]));
  });
});

test('CLI rejects a directory rather than treating non-regular input as a report or proposal', async () => {
  await withTemporaryDirectory(async directory => {
    rejected(run(['fallback', targetId, directory]));
    rejected(run(['evaluate', targetId, fixturePath, directory]));
  });
});

test('CLI rejects an unopened Unix FIFO promptly without waiting for a writer', { skip: process.platform === 'win32' }, async () => {
  await withTemporaryDirectory(async directory => {
    const fifoPath = join(directory, `${canary}-pipe`);
    const creation = spawnSync('mkfifo', [fifoPath], { encoding: 'utf8', timeout: 5000, maxBuffer: 4096 });
    completed(creation, 0);
    rejected(run(['fallback', targetId, fifoPath]));
    rejected(run(['evaluate', targetId, fixturePath, fifoPath]));
  });
});

test('CLI rejects foreign target scope without returning source identities or facts', () => {
  rejected(run(['fallback', 'synthetic-target-b', fixturePath]));
});

test('CLI refuses legacy reports and report-local binding mismatches without echoing source details', async () => {
  await withTemporaryDirectory(async directory => {
    const sourcePath = join(directory, `${canary}-source.json`);
    for (const change of [
      report => { delete report.findingDetails; },
      report => { report.findingDetails.targetId = 'synthetic-target-b'; },
      report => { report.findingDetails.reportId = '30000000-0000-4000-8000-000000000003'; },
      report => { report.findingDetails.assessmentId = '40000000-0000-4000-8000-000000000004'; },
    ]) {
      const source = structuredClone(fixture); change(source);
      source.targetDisplayName = canary;
      await writeFile(sourcePath, JSON.stringify(source));
      rejected(run(['fallback', targetId, sourcePath]));
    }
  });
});

test('CLI rejects a finding exceeding the declared budget instead of clipping its values', async () => {
  await withTemporaryDirectory(async directory => {
    const sourcePath = join(directory, `${canary}-oversized-finding.json`);
    const source = structuredClone(fixture);
    source.findingDetails.items[0].finding.title = canary + 'x'.repeat(8192);
    await writeFile(sourcePath, JSON.stringify(source));
    rejected(run(['fallback', targetId, sourcePath]));
  });
});

test('CLI explanation can cite an actual finding value while fabricated finding references fail closed', async () => {
  await withTemporaryDirectory(async directory => {
    const proposalPath = join(directory, `${canary}-finding-proposal.json`);
    const proposal = validProposal();
    proposal.observations[0].references = ['/findingDetails/items/0/finding/evidence/replicas'];
    await writeFile(proposalPath, JSON.stringify(proposal));
    const result = run(['evaluate', targetId, fixturePath, proposalPath]);
    completed(result, 0);
    assert.deepEqual(JSON.parse(result.stdout), { structuralChecks: 'PASSED', semanticReview: 'REQUIRED', modelEvaluated: false });
    assert.ok(!result.stdout.includes(canary));
    proposal.observations[0].references = ['/findingDetails/items/0/finding/evidence/fabricated-id'];
    await writeFile(proposalPath, JSON.stringify(proposal));
    rejected(run(['evaluate', targetId, fixturePath, proposalPath]));
  });
});

test('CLI usage is exact and rejects unknown commands, missing parameters and surplus arguments', () => {
  for (const args of [
    [],
    ['unknown', targetId, fixturePath],
    ['fallback'],
    ['fallback', targetId],
    ['fallback', targetId, fixturePath, `${canary}-extra`],
    ['evaluate', targetId, fixturePath],
    ['evaluate', targetId, fixturePath, `${canary}-proposal`, `${canary}-extra`],
  ]) rejected(run(args));
});
