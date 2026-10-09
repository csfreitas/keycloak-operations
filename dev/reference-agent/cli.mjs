// Offline only: no network, credential lookup, model invocation or file writes.
import { open } from 'node:fs/promises';
import { constants } from 'node:fs';
import { deterministicFallback, evaluateExplanation } from './core.mjs';

async function readJson(path) {
  const file = await open(path, constants.O_RDONLY | constants.O_NONBLOCK);
  try {
    const stat = await file.stat();
    if (!stat.isFile() || stat.size > 1_048_576) throw new Error();
    const bytes = Buffer.alloc(1_048_577);
    let size = 0;
    while (size < bytes.length) {
      const result = await file.read(bytes, size, bytes.length - size, null);
      if (!result.bytesRead) break;
      size += result.bytesRead;
    }
    if (size > 1_048_576) throw new Error();
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, size)));
  } finally { await file.close(); }
}

try {
  const [command, targetId, reportPath, proposalPath, ...extra] = process.argv.slice(2);
  if (extra.length || !targetId || !reportPath || !['fallback', 'evaluate'].includes(command)
      || (command === 'evaluate') !== Boolean(proposalPath)) throw new Error();
  const report = await readJson(reportPath);
  const result = command === 'fallback' ? deterministicFallback(report, targetId)
    : evaluateExplanation(report, targetId, await readJson(proposalPath));
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
} catch {
  // Never echo an input path, parser excerpt, source payload or unreviewed explanation.
  process.stderr.write('Reference-agent input rejected. Use fallback <target-id> <report.json> or evaluate <target-id> <report.json> <proposal.json>.\n');
  process.exitCode = 1;
}
