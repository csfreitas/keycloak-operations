// Only the runner-owned disposable database, real local IdP and synthetic loopback cluster.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { validateRuntimeContext, validateFixtureDatabase } from './identity-lab-runtime.mjs';

assert.equal(process.env.INSTALLATION_LAB_OWNED_DB, '1', 'Use validate-installation-lab.sh to own the disposable fixtures');
assert.ok(process.env.INSTALLATION_LAB_RUNTIME_CONTEXT, 'Runner-owned runtime identity is required');
const runtimeContext = JSON.parse(await readFile(process.env.INSTALLATION_LAB_RUNTIME_CONTEXT, 'utf8'));
validateRuntimeContext(runtimeContext);
// Resolve ownership/tmpfs once, then use an immutable full ID and pinned endpoint
// for every fixture SQL command. Never follow the ambient Podman default/name.
// These synchronous children stay in the outer test supervisor's process group.
const podman = args => execFileSync('podman',['--url',runtimeContext.uri,'--identity',runtimeContext.identity,...args],
  {encoding:'utf8',timeout:10000,killSignal:'SIGKILL',maxBuffer:1024*1024,stdio:['ignore','pipe','pipe']});
let databaseId;
try { databaseId = validateFixtureDatabase(runtimeContext, JSON.parse(podman(['container','inspect','kcops-identity-postgres']))); }
catch { throw new Error('Owned fixture database could not be verified.'); }
let passed = 0;
const check = (name, condition) => { assert.ok(condition, name); console.log(`PASS ${++passed}: ${name}`); };
const tokens = {};
const actors = {};
const target = id => `lab-keycloak-${id}`;
const path = id => `/api/v1/targets/${target(id)}/installation`;
const uuid = value => { assert.match(value, /^[0-9a-f-]{36}$/); return value; };
const leaks = value => {
  const serialized = typeof value==='string' ? value : JSON.stringify(value);
  assert.ok(![...Object.values(tokens), 'local-installation-cluster', 'local-installation-control',
    ...['installer-a','installer-a2','installer-b','installer-both','installer-bind-only','target-a','target-b','fixture-a','fixture-b'].map(id=>`local-${id}`),
    'Local-fixture-only-2026!']
    .some(secret => serialized.includes(secret)), 'Response must not contain fixture credentials');
};
async function request(route, actor, {method='GET', body, readonly=false}={}) {
  const response = await fetch(`http://localhost:${readonly ? 18082 : 18081}${route}`, {
    method, headers:{...(actor ? {Authorization:`Bearer ${tokens[actor]}`} : {}), 'Content-Type':'application/json'},
    body:method==='GET' ? undefined : JSON.stringify(body ?? {}), signal:AbortSignal.timeout(15000),
  });
  const text = await response.text();
  leaks(text);
  let data; try { data=JSON.parse(text); } catch { data={}; }
  leaks(data);
  return {status:response.status, data};
}
async function control(body) {
  const response=await fetch(`http://127.0.0.1:18590/__fixture/${body ? 'control' : 'state'}`, {
    method:body ? 'POST' : 'GET', headers:{Authorization:'Bearer local-installation-control','Content-Type':'application/json'},
    body:body ? JSON.stringify(body) : undefined, signal:AbortSignal.timeout(3000),
  });
  assert.equal(response.status,200,'Synthetic cluster control'); return response.json();
}
function sql(statement) {
  try {
    return podman(['exec',databaseId,'psql','-U','kcops','-d','kcops','-X','-A','-t','-v','ON_ERROR_STOP=1','-c',statement]).trim();
  } catch { throw new Error('Owned fixture SQL failed; no automatic retry.'); }
}
function stored() {
  return JSON.parse(sql(`SELECT json_build_object(
    'bindings', (SELECT coalesce(json_agg(t ORDER BY id),'[]'::json) FROM
      (SELECT id, installation_api_version, installation_kind, installation_name, installation_uid,
       installation_revision, installation_managed FROM targets) t),
    'runs', (SELECT coalesce(json_agg(r ORDER BY id),'[]'::json) FROM
      (SELECT id, target_id, actor, binding_revision, consumed, expires_at FROM installation_discovery_runs) r),
    'audit', (SELECT coalesce(json_agg(a ORDER BY id),'[]'::json) FROM
      (SELECT id,target_id,source,tool,operation,status,metadata FROM audit_events WHERE operation='INSTALLATION_BOUND') a));`));
}
async function discover(id, actor, readonly=false) {
  const response=await request(`${path(id)}/discover`,actor,{method:'POST',readonly});
  assert.equal(response.status,200,`Discovery succeeds: ${id}/${actor}`);
  const data=response.data;
  check(`Explicit namespace-scoped candidates: ${id}/${actor}`,
    data.targetId===target(id) && data.namespace===`lab-${id}` && data.candidates.length===2
    && data.candidates.every(c=>c.installation.kind==='Deployment' && c.installation.apiVersion==='apps/v1'
      && [`sso-${id}`,`alternate-${id}`].includes(c.installation.name))
    && Date.parse(data.expiresAt)>Date.now() && Date.parse(data.expiresAt)<Date.now()+605000);
  assert.ok(data.candidates.every(c=>Object.keys(c).sort().join(',')==='id,installation'
    && Object.keys(c.installation).sort().join(',')==='apiVersion,kind,name,uid'),'Only allowlisted candidate metadata');
  return data;
}
const selection = (run, name) => ({runId:uuid(run.runId),candidateId:uuid(run.candidates.find(c=>c.installation.name===name).id)});
async function denied(name, id, actor, body, {readonly=false, status=400, noClusterRead=false}={}) {
  const before=stored(); const remoteBefore=await control();
  const result=await request(`${path(id)}/confirm`,actor,{method:'POST',body,readonly});
  check(name, result.status===status && result.data.code===(status===403 ? 'TARGET_NOT_AUTHORIZED' : 'INVALID_ARGUMENT'));
  check(`${name}: binding, runs and success audit unchanged`, JSON.stringify(stored())===JSON.stringify(before));
  if(noClusterRead) check(`${name}: rejected before cluster access`, (await control()).clusterReads===remoteBefore.clusterReads);
}
async function accepted(id,actor,run,name,readonly=false) {
  const before=stored(); const previous=before.bindings.find(b=>b.id===target(id));
  const chosen=run.candidates.find(c=>c.installation.name===name);
  const exactBinding = value => ['apiVersion','kind','name','uid'].every(key=>value?.[key]===chosen.installation[key]);
  const result=await request(`${path(id)}/confirm`,actor,{method:'POST',body:selection(run,name),readonly});
  check(`Explicit confirmation succeeds: ${id}/${actor}`,result.status===200 && result.data.managed===true
    && result.data.revision===previous.installation_revision+1 && exactBinding(result.data.binding));
  const after=stored(); const binding=after.bindings.find(b=>b.id===target(id));
  const audit=after.audit.find(a=>a.metadata.runId===run.runId);
  check(`Binding, consumed run and mandatory audit agree: ${id}`,
    exactBinding({apiVersion:binding.installation_api_version,kind:binding.installation_kind,name:binding.installation_name,uid:binding.installation_uid})
    && binding.installation_managed===true && binding.installation_revision===result.data.revision
    && after.runs.find(r=>r.id===run.runId)?.consumed===true && after.audit.length===before.audit.length+1
    && audit?.target_id===target(id) && audit.source==='REST' && audit.tool==='installation.confirm' && audit.status==='SUCCESS'
    && audit.metadata.actor===actors[actor] && audit.metadata.previousUid===previous.installation_uid
    && audit.metadata.selectedUid===chosen.installation.uid && audit.metadata.revision===result.data.revision);
  check(`Other target binding is unchanged: ${id}`,JSON.stringify(after.bindings.filter(b=>b.id!==target(id)))===JSON.stringify(before.bindings.filter(b=>b.id!==target(id))));
  const fresh=await request(path(id),`assessor-${id}`);
  check(`Fresh reader observes complete selected binding: ${id}`,fresh.status===200 && exactBinding(fresh.data.binding)
    && fresh.data.managed===true && fresh.data.revision===result.data.revision && !fresh.data.canDiscover && !fresh.data.canConfirm);
}

for(const id of ['assessor-a','assessor-b','installer-a','installer-a2','installer-b','installer-both','installer-bind-only']) {
  const response=await fetch('http://localhost:18280/realms/operations/protocol/openid-connect/token',{
    method:'POST',body:new URLSearchParams({grant_type:'client_credentials',client_id:id,
      client_secret:id.startsWith('assessor-') ? `local-fixture-${id.slice(9)}` : `local-${id}`}),signal:AbortSignal.timeout(10000),
  });
  assert.equal(response.status,200,`Real IdP token issuance: ${id}`);
  tokens[id]=(await response.json()).access_token;
  const me=await request('/api/v1/me',id);
  check(`Real authenticated principal accepted: ${id}`,me.status===200 && me.data.authMode==='OIDC'); actors[id]=me.data.subject;
}
check('Same-target setup actors have distinct authenticated subjects',actors['installer-a']!==actors['installer-a2']);
// The target registry is lazy: warm both application instances before inspecting persisted configuration.
for(const readonly of [false,true]) {
  const warm=await request('/api/v1/targets','assessor-a',{readonly});
  check(`Target registry initialized through authenticated read (read-only=${readonly})`,warm.status===200);
}
const initial=stored();
check('Fresh disposable database has two unbound targets and no binding audit',initial.bindings.length===2
  && initial.bindings.every(b=>b.installation_uid===null && b.installation_revision===0 && !b.installation_managed) && initial.audit.length===0);
check('Only synthetic namespace fixtures are connected',(await control()).synthetic===true);
for(const id of ['a','b']) {
  const state=await request(path(id),`assessor-${id}`);
  check(`Reader cannot discover or confirm: ${id}`,state.status===200 && !state.data.canDiscover && !state.data.canConfirm && state.data.binding===null);
  const remote=(await control()).clusterReads;
  const noDiscovery=await request(`${path(id)}/discover`,`assessor-${id}`,{method:'POST'});
  check(`Reader discovery denied before cluster access: ${id}`,noDiscovery.status===403 && (await control()).clusterReads===remote);
}
const anonymous=await request(`${path('a')}/discover`,null,{method:'POST'});
check('Anonymous discovery denied',[401,403].includes(anonymous.status));
const beforeDiscovery=stored();
let first=await discover('a','installer-a');
const postDiscovery=stored();
check('Discovery alone does not select or bind',JSON.stringify(postDiscovery.bindings)===JSON.stringify(beforeDiscovery.bindings)
  && postDiscovery.audit.length===0 && postDiscovery.runs.find(r=>r.id===first.runId)?.consumed===false);
await denied('Reader confirmation denied','a','assessor-a',selection(first,'sso-a'),{status:403,noClusterRead:true});
await denied('BIND without DISCOVER denied','a','installer-bind-only',selection(first,'sso-a'),{status:403,noClusterRead:true});
await denied('Same-target different actor cannot consume run','a','installer-a2',selection(first,'sso-a'),{noClusterRead:true});
await denied('Unreturned candidate rejected','a','installer-a',{runId:first.runId,candidateId:'00000000-0000-0000-0000-000000000000'},{noClusterRead:true});
await denied('Unauthorized target denied','b','installer-a',selection(first,'sso-a'),{status:403,noClusterRead:true});
let both=await discover('a','installer-both');
await denied('Run cannot cross targets even for multi-target setup actor','b','installer-both',selection(both,'sso-a'),{noClusterRead:true});
const ro=await request(path('a'),'installer-a',{readonly:true});
check('Granted setup actor can discover but not confirm in read-only instance',ro.status===200 && ro.data.canDiscover && !ro.data.canConfirm);
await denied('Global read-only blocks otherwise authorized binding','a','installer-a',selection(first,'sso-a'),{readonly:true,status:403,noClusterRead:true});
let peer=await discover('a','installer-a2');
await accepted('a','installer-a',first,'sso-a');
await denied('Successful confirmation cannot be replayed','a','installer-a',selection(first,'sso-a'),{noClusterRead:true});
await denied('Independent actor run with old binding revision rejected','a','installer-a2',selection(peer,'sso-a'),{noClusterRead:true});
const superseded=await discover('a','installer-a');
let current=await discover('a','installer-a');
await denied('Rediscovery invalidates previous same-actor run','a','installer-a',selection(superseded,'sso-a'),{noClusterRead:true});
await control({operation:'replace',namespace:'lab-a',name:'sso-a',uid:'uid-a-replaced'});
await denied('Recreated resource UID requires rediscovery','a','installer-a',selection(current,'sso-a'));
await control({operation:'replace',namespace:'lab-a',name:'sso-a',uid:'uid-a-1'});
await control({operation:'deny',enabled:true});
await denied('Cluster permission loss rejects confirmation','a','installer-a',selection(current,'sso-a'));
const beforeFailedDiscovery=stored();
const failedDiscovery=await request(`${path('a')}/discover`,'installer-a',{method:'POST'});
check('Denied cluster discovery exposes no confirmable partial run',failedDiscovery.status===500 && failedDiscovery.data.code==='EVIDENCE_COLLECTION_FAILED'
  && JSON.stringify(stored())===JSON.stringify(beforeFailedDiscovery));
await control({operation:'deny',enabled:false});
// Explicit fault injection into this runner-owned transient DB, not a wall-clock expiry claim.
sql(`UPDATE installation_discovery_runs SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = '${uuid(current.runId)}'`);
await denied('Injected expired run is rejected','a','installer-a',selection(current,'sso-a'),{noClusterRead:true});
current=await discover('a','installer-a');
await accepted('a','installer-a',current,'alternate-a');
const runB=await discover('b','installer-b',true);
await denied('Read-only instance also denies its own fresh run','b','installer-b',selection(runB,'sso-b'),{readonly:true,status:403,noClusterRead:true});
await accepted('b','installer-b',runB,'sso-b');
for(const id of ['a','b']) {
  const state=await request(path(id),`assessor-${id}`,{readonly:true});
  check(`Reader observes persisted binding without gaining setup rights: ${id}`,state.status===200 && state.data.managed && !state.data.canDiscover && !state.data.canConfirm);
}
const remote=await control();
function allowedClusterRead(entry) {
  if(entry.method!=='GET' || ![200,403].includes(entry.status)) return false;
  if(['/api','/api/v1','/apis','/apis/apps','/apis/apps/v1','/version'].includes(entry.path)) return true;
  const match=entry.path.match(/^\/apis\/apps\/v1\/namespaces\/lab-([ab])\/(deployments|statefulsets)(?:\/([^?]+))?(\?.*)?$/);
  if(!match) return false;
  const [,namespace,resource,name,query]=match;
  if(name) return resource==='deployments' && [`sso-${namespace}`,`alternate-${namespace}`].includes(name) && query===undefined;
  return query==='?limit=101';
}
check('Synthetic cluster received only allowlisted reads, never writes or Secret requests',remote.clusterWrites===0
  && remote.trace.length>0 && remote.droppedTraceEntries===0 && remote.trace.every(allowedClusterRead));
check('Three successful bindings have mandatory audits and nothing else',stored().audit.length===3);
console.log(`Installation validation: ${passed} checks passed. Real local identity; synthetic cluster; expiry uses explicit disposable-database fault injection.`);
