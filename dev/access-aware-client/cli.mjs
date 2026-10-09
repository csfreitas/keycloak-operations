import { createConfigurationClient } from './client.mjs';
import { createFixtureHost } from './fixtures.mjs';

// Deliberately no endpoint, token, provider, arbitrary file or executable argument.
let client;
try {
  const [command, persona, scopeId, ...extra] = process.argv.slice(2);
  if (command !== 'demo' || !persona || extra.length) throw new Error();
  client = createConfigurationClient(createFixtureHost(persona));
  const answer = await client.inspect({ scopeIds: scopeId === undefined ? [] : [scopeId] });
  process.stdout.write(JSON.stringify({ fixture: 'SYNTHETIC_OFFLINE_ONLY', answer }, null, 2) + '\n');
} catch {
  process.stderr.write('DEMO_FAILED: use demo <rhea|chris|partial|empty> [exact-scope-id]\n');
  process.exitCode = 1;
} finally { client?.close(); }
