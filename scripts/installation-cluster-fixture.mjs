#!/usr/bin/env node
/**
 * Synthetic, loopback-only Kubernetes HTTP fixture for local installation checks.
 * These public fixture credentials never authorize a real cluster. The control
 * credential is deliberately different from the application's cluster credential.
 *
 * GET /__fixture/state returns bounded cluster request evidence and current UIDs.
 * POST /__fixture/control accepts exactly one of:
 *   { operation: 'reset' }
 *   { operation: 'deny', enabled: true | false }
 *   { operation: 'replace', namespace: 'lab-a', name: 'sso-a', uid: 'uid-a-replaced' }
 * Reset restores both namespaces and clears all trace/counters. Replace accepts
 * only the four existing fixture deployment names and a bounded synthetic UID.
 */
import http from 'node:http';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const CLUSTER_TOKEN = 'local-installation-cluster';
export const CONTROL_TOKEN = 'local-installation-control';
export const MAX_BODY_BYTES = 4_096;
export const MAX_TRACE_ENTRIES = 1_000;
const MAX_REQUEST_TARGET_BYTES = 2_048;
const HOST = '127.0.0.1';

const initialNamespaces = () => ({
  'lab-a': { 'sso-a': 'uid-a-1', 'alternate-a': 'uid-a-2' },
  'lab-b': { 'sso-b': 'uid-b-1', 'alternate-b': 'uid-b-2' },
});
const group = {
  apiVersion: 'v1', kind: 'APIGroup', name: 'apps',
  versions: [{ groupVersion: 'apps/v1', version: 'v1' }],
  preferredVersion: { groupVersion: 'apps/v1', version: 'v1' },
};
const deployment = (namespace, name, uid) => ({
  apiVersion: 'apps/v1', kind: 'Deployment', metadata: { namespace, name, uid },
});
const statusBody = (code, reason) => ({
  apiVersion: 'v1', kind: 'Status', status: 'Failure', code, reason,
  message: `Synthetic installation fixture: ${reason}`,
});
const exactKeys = (object, names) => Object.keys(object).sort().join(',') === names.sort().join(',');

function send(response, code, body) {
  response.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-installation-fixture': 'synthetic',
    // Closing after each small response also bounds idle fixture connections.
    connection: 'close',
  });
  response.end(JSON.stringify(body));
}

function readControlBody(request) {
  return new Promise((resolveBody, rejectBody) => {
    const chunks = [];
    let size = 0;
    request.on('data', chunk => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        chunks.length = 0;
        rejectBody(413);
      } else {
        chunks.push(chunk);
      }
    });
    request.on('end', () => {
      if (size > MAX_BODY_BYTES) return;
      try { resolveBody(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
      catch { rejectBody(400); }
    });
    request.on('error', () => rejectBody(400));
    request.on('aborted', () => rejectBody(400));
  });
}

/** Start on 127.0.0.1 only. Port 0 is supported for isolated node:test checks. */
export async function startFixture({ port = 18590 } = {}) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid fixture port');
  let namespaces = initialNamespaces();
  let permissionDenied = false;
  let clusterWrites = 0;
  let clusterReads = 0;
  let trace = [];
  let droppedTraceEntries = 0;

  const snapshot = () => ({
    synthetic: true, permissionDenied, namespaces,
    clusterWrites, clusterReads,
    readRequests: trace.filter(entry => entry.method === 'GET'),
    trace, droppedTraceEntries,
  });
  const record = (request, code) => {
    if (request.method === 'GET') clusterReads++;
    if (trace.length === MAX_TRACE_ENTRIES) {
      trace.shift();
      droppedTraceEntries++;
    }
    // Never record headers, bodies, bearer values or runtime configuration.
    const path = Buffer.byteLength(request.url) <= MAX_REQUEST_TARGET_BYTES
      ? request.url : '[request target exceeded fixture bound]';
    trace.push({ method: request.method, path, status: code });
  };

  const server = http.createServer({ maxHeaderSize: 8_192 }, async (request, response) => {
    const rawPath = request.url ?? '';
    const path = rawPath.split('?', 1)[0];
    const controlRoute = path === '/__fixture/state' || path === '/__fixture/control';
    const reply = (code, body) => {
      if (!controlRoute) record(request, code);
      send(response, code, body);
    };
    if (!controlRoute && request.method !== 'GET') clusterWrites++;
    if (Buffer.byteLength(rawPath) > MAX_REQUEST_TARGET_BYTES) {
      request.resume();
      reply(414, statusBody(414, 'RequestTargetTooLong'));
      return;
    }
    if (controlRoute) {
      if (request.headers.authorization !== `Bearer ${CONTROL_TOKEN}`) {
        request.resume();
        reply(401, statusBody(401, 'Unauthorized'));
        return;
      }
      if (rawPath !== path) {
        request.resume();
        reply(400, statusBody(400, 'BadRequest'));
        return;
      }
      if (path === '/__fixture/state' && request.method === 'GET') {
        request.resume();
        reply(200, snapshot());
        return;
      }
      if (path !== '/__fixture/control' || request.method !== 'POST') {
        request.resume();
        reply(405, statusBody(405, 'MethodNotAllowed'));
        return;
      }
      if (request.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
        request.resume();
        reply(415, statusBody(415, 'UnsupportedMediaType'));
        return;
      }
      const declaredSize = Number(request.headers['content-length'] ?? 0);
      if (declaredSize > MAX_BODY_BYTES) {
        request.resume();
        reply(413, statusBody(413, 'PayloadTooLarge'));
        return;
      }
      let body;
      try { body = await readControlBody(request); }
      catch (code) {
        if (!response.destroyed) reply(code, statusBody(code, code === 413 ? 'PayloadTooLarge' : 'BadRequest'));
        return;
      }
      if (!body || typeof body !== 'object' || Array.isArray(body)) {
        reply(400, statusBody(400, 'BadRequest'));
        return;
      }
      if (body.operation === 'reset' && exactKeys(body, ['operation'])) {
        namespaces = initialNamespaces();
        permissionDenied = false;
        clusterWrites = 0;
        clusterReads = 0;
        trace = [];
        droppedTraceEntries = 0;
      } else if (body.operation === 'deny' && exactKeys(body, ['operation', 'enabled'])
          && typeof body.enabled === 'boolean') {
        permissionDenied = body.enabled;
      } else if (body.operation === 'replace' && exactKeys(body, ['operation', 'namespace', 'name', 'uid'])
          && typeof body.namespace === 'string' && typeof body.name === 'string'
          && Object.hasOwn(namespaces, body.namespace)
          && Object.hasOwn(namespaces[body.namespace], body.name)
          && typeof body.uid === 'string' && /^uid-[a-z0-9-]{1,76}$/.test(body.uid)
          && !Object.entries(namespaces).some(([namespace, deployments]) =>
            Object.entries(deployments).some(([name, uid]) => uid === body.uid
              && (namespace !== body.namespace || name !== body.name)))) {
        namespaces[body.namespace][body.name] = body.uid;
      } else {
        reply(400, statusBody(400, 'BadRequest'));
        return;
      }
      reply(200, snapshot());
      return;
    }

    request.resume();
    if (request.headers.authorization !== `Bearer ${CLUSTER_TOKEN}`) {
      reply(401, statusBody(401, 'Unauthorized'));
      return;
    }
    if (request.method !== 'GET') {
      reply(405, statusBody(405, 'MethodNotAllowed'));
      return;
    }
    if (permissionDenied) {
      reply(403, statusBody(403, 'Forbidden'));
      return;
    }
    if (path === '/version') {
      reply(200, { major: '1', minor: '30', gitVersion: 'v1.30.0-synthetic' });
    } else if (path === '/api') {
      reply(200, { apiVersion: 'v1', kind: 'APIVersions', versions: ['v1'], serverAddressByClientCIDRs: [] });
    } else if (path === '/api/v1') {
      reply(200, { apiVersion: 'v1', kind: 'APIResourceList', groupVersion: 'v1', resources: [] });
    } else if (path === '/apis') {
      reply(200, { apiVersion: 'v1', kind: 'APIGroupList', groups: [group] });
    } else if (path === '/apis/apps') {
      reply(200, group);
    } else if (path === '/apis/apps/v1') {
      reply(200, {
        apiVersion: 'v1', kind: 'APIResourceList', groupVersion: 'apps/v1',
        resources: ['Deployment', 'StatefulSet'].map(kind => ({
          name: kind === 'Deployment' ? 'deployments' : 'statefulsets',
          singularName: kind.toLowerCase(), kind, namespaced: true, verbs: ['get', 'list'],
        })),
      });
    } else {
      const resource = /^\/apis\/apps\/v1\/namespaces\/(lab-[ab])\/(deployments|statefulsets)(?:\/([a-z0-9-]+))?$/.exec(path);
      if (!resource) {
        reply(404, statusBody(404, 'NotFound'));
        return;
      }
      const [, namespace, type, name] = resource;
      if (name) {
        if (type === 'deployments' && Object.hasOwn(namespaces[namespace], name)) {
          reply(200, deployment(namespace, name, namespaces[namespace][name]));
        } else {
          reply(404, statusBody(404, 'NotFound'));
        }
      } else {
        reply(200, {
          apiVersion: 'apps/v1', kind: type === 'deployments' ? 'DeploymentList' : 'StatefulSetList',
          metadata: { resourceVersion: '1' },
          items: type === 'deployments'
            ? Object.entries(namespaces[namespace]).map(([name, uid]) => deployment(namespace, name, uid)) : [],
        });
      }
    }
  });
  server.requestTimeout = 5_000;
  server.headersTimeout = 5_000;
  server.maxConnections = 32;
  server.setTimeout(5_000, socket => socket.destroy());
  await new Promise((resolveReady, rejectReady) => {
    server.once('error', rejectReady);
    server.listen(port, HOST, () => {
      server.removeListener('error', rejectReady);
      resolveReady();
    });
  });
  const address = server.address();
  return {
    server, url: `http://${HOST}:${address.port}`,
    close: () => new Promise((resolveClosed, rejectClosed) => {
      server.close(error => error ? rejectClosed(error) : resolveClosed());
      server.closeAllConnections();
    }),
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const fixture = await startFixture();
    process.stdout.write(`${JSON.stringify({ synthetic: true, ready: true, url: fixture.url })}\n`);
    const stop = async () => {
      try { await fixture.close(); process.exitCode = 0; }
      catch { process.exitCode = 1; }
    };
    process.once('SIGINT', stop);
    process.once('SIGTERM', stop);
  } catch {
    process.stderr.write('Synthetic installation fixture failed to start on 127.0.0.1:18590\n');
    process.exitCode = 1;
  }
}
