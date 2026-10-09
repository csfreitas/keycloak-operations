// Offline observation of one Codex JSONL trial, not an execution or security attestation.
// Unknown protocol shapes deliberately require review instead of proving no actions.
export const MAX_TRIAL_EVENT_BYTES = 1_048_576;
export const MAX_TRIAL_EVENTS = 10_000;

const TOOL_TYPES = new Set(['command_execution', 'file_change', 'mcp_tool_call', 'web_search']);
const ITEM_EVENTS = new Set(['item.started', 'item.updated', 'item.completed']);
const ITEM_KEYS = {
  error: ['id', 'type', 'message'],
  agent_message: ['id', 'type', 'text'],
  reasoning: ['id', 'type', 'text'],
  todo_list: ['id', 'type', 'items'],
  command_execution: ['id', 'type', 'command', 'aggregated_output', 'exit_code', 'status'],
  file_change: ['id', 'type', 'changes', 'status'],
  mcp_tool_call: ['id', 'type', 'server', 'tool', 'arguments', 'result', 'error', 'status'],
  web_search: ['id', 'type', 'query', 'status'],
};
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = value => typeof value === 'string';
const identity = value => text(value) && value.length > 0 && value.length <= 256;
const integer = value => Number.isSafeInteger(value) && value >= 0;
const keys = (value, allowed) => record(value) && Object.keys(value).every(key => allowed.includes(key));
const optional = (value, key, check) => !Object.hasOwn(value, key) || check(value[key]);

// JSON.parse alone silently accepts duplicate keys. Check decoded object keys and
// depth after syntax parsing, without retaining any source text in the result.
function unambiguousJson(source) {
  const stack = [];
  for (let index = 0; index < source.length; index++) {
    const character = source[index];
    if (character === '"') {
      const start = index++;
      while (index < source.length && source[index] !== '"') {
        if (source[index] === '\\') index++;
        index++;
      }
      const frame = stack.at(-1);
      if (frame?.object && frame.key) {
        const name = JSON.parse(source.slice(start, index + 1));
        if (frame.keys.has(name)) return false;
        frame.keys.add(name);
        frame.key = false;
      }
    } else if (character === '{' || character === '[') {
      stack.push({ object: character === '{', key: true, keys: new Set() });
      if (stack.length > 64) return false;
    } else if (character === '}' || character === ']') stack.pop();
    else if (character === ',' && stack.at(-1)?.object) stack.at(-1).key = true;
  }
  return true;
}

function validUsage(value) {
  return keys(value, ['input_tokens', 'cached_input_tokens', 'output_tokens',
    'reasoning_output_tokens', 'cache_write_input_tokens'])
    && ['input_tokens', 'cached_input_tokens', 'output_tokens'].every(key => integer(value[key]))
    && Object.values(value).every(integer);
}

function validItem(item, eventType) {
  if (!identity(item.id) || !keys(item, ITEM_KEYS[item.type])) return false;
  if (item.type === 'error') return eventType === 'item.completed' && text(item.message);
  if (item.type === 'agent_message' || item.type === 'reasoning') return text(item.text);
  if (item.type === 'todo_list') return Array.isArray(item.items) && item.items.every(entry =>
    keys(entry, ['text', 'completed']) && text(entry.text) && typeof entry.completed === 'boolean');

  const terminal = eventType === 'item.completed';
  if (item.type !== 'web_search' || Object.hasOwn(item, 'status')) {
    if (!(terminal ? ['completed', 'failed'] : ['in_progress']).includes(item.status)) return false;
  }
  switch (item.type) {
    case 'command_execution':
      return text(item.command) && optional(item, 'aggregated_output', text)
        && optional(item, 'exit_code', value => value === null || Number.isSafeInteger(value));
    case 'file_change':
      return Array.isArray(item.changes) && item.changes.length > 0 && item.changes.every(change =>
        keys(change, ['path', 'kind']) && text(change.path) && change.path.length > 0
        && ['add', 'delete', 'update'].includes(change.kind));
    case 'mcp_tool_call':
      return identity(item.server) && identity(item.tool) && record(item.arguments)
        && optional(item, 'error', value => value === null || (keys(value, ['message']) && text(value.message)))
        && optional(item, 'result', value => value === null || (keys(value, ['content', 'structured_content'])
          && optional(value, 'content', content => Array.isArray(content)
            && content.every(block => record(block) && identity(block.type)))
          && optional(value, 'structured_content', record)));
    case 'web_search': return text(item.query);
    default: return false;
  }
}

/** Counts describe this bounded, recognized log only. They never prove isolation. */
export function inspectTrialEvents(jsonl) {
  const result = {
    status: 'INCONCLUSIVE', eventCount: 0, toolEvents: 0, uniqueToolItems: 0,
    clientDiagnosticEvents: 0, agentMessageEvents: 0, reasoningEvents: 0, planEvents: 0,
    unknownEvents: 0, malformedEvents: 0, failureEvents: 0,
    threadStartedEvents: 0, turnStartedEvents: 0, turnCompletedEvents: 0,
    limitsExceeded: false, complete: false, securityAttested: false,
  };
  if (!text(jsonl)) { result.malformedEvents++; return result; }
  if (jsonl.length > MAX_TRIAL_EVENT_BYTES || Buffer.byteLength(jsonl, 'utf8') > MAX_TRIAL_EVENT_BYTES) {
    result.limitsExceeded = true;
    return result;
  }
  const lines = jsonl.split('\n');
  if (lines.length > MAX_TRIAL_EVENTS + 1) { result.limitsExceeded = true; return result; }
  const items = new Map();
  const toolIds = new Set();
  let thread = false;
  let active = false;
  let finished = false;
  let completedMessages = 0;
  for (const line of lines) {
    if (!line.trim()) continue;
    if (++result.eventCount > MAX_TRIAL_EVENTS) { result.limitsExceeded = true; break; }
    let event;
    try {
      event = JSON.parse(line);
      if (!unambiguousJson(line)) throw new Error();
    } catch { result.malformedEvents++; continue; }
    if (!record(event) || !text(event.type)) { result.malformedEvents++; continue; }
    if (event.type === 'thread.started') {
      result.threadStartedEvents++;
      if (!keys(event, ['type', 'thread_id']) || !identity(event.thread_id) || thread || active || finished) {
        result.malformedEvents++;
      } else thread = true;
    } else if (event.type === 'turn.started') {
      result.turnStartedEvents++;
      if (!keys(event, ['type']) || !thread || active || finished) result.malformedEvents++;
      else active = true;
    } else if (event.type === 'turn.completed') {
      result.turnCompletedEvents++;
      if (!keys(event, ['type', 'usage']) || !validUsage(event.usage) || !active || finished) {
        result.malformedEvents++;
      }
      active = false;
      finished = true;
    } else if (event.type === 'turn.failed' || event.type === 'error') {
      result.failureEvents++;
      const valid = event.type === 'error'
        ? keys(event, ['type', 'message']) && text(event.message)
        : keys(event, ['type', 'error']) && keys(event.error, ['message']) && text(event.error.message);
      if (!valid) result.malformedEvents++;
      if (event.type === 'turn.failed') { active = false; finished = true; }
    } else if (ITEM_EVENTS.has(event.type)) {
      if (!keys(event, ['type', 'item']) || !record(event.item) || !text(event.item.type)) {
        result.malformedEvents++; continue;
      }
      const item = event.item;
      if (!Object.hasOwn(ITEM_KEYS, item.type)) { result.unknownEvents++; continue; }
      if (TOOL_TYPES.has(item.type)) {
        result.toolEvents++;
        if (identity(item.id)) toolIds.add(item.id);
      }
      if (!validItem(item, event.type) || !thread || finished || (item.type !== 'error' && !active)) {
        result.malformedEvents++; continue;
      }
      if (item.status === 'failed' || (item.type === 'command_execution' && item.exit_code != null && item.exit_code !== 0)
          || (item.type === 'mcp_tool_call' && item.error != null)) result.failureEvents++;
      const prior = items.get(item.id);
      if ((prior && (prior.type !== item.type || prior.completed || event.type === 'item.started'))
          || (!prior && event.type === 'item.updated')) {
        result.malformedEvents++; continue;
      }
      items.set(item.id, { type: item.type, completed: event.type === 'item.completed' });
      if (item.type === 'error') result.clientDiagnosticEvents++;
      else if (item.type === 'agent_message') {
        result.agentMessageEvents++;
        if (event.type === 'item.completed') completedMessages++;
      } else if (item.type === 'reasoning') result.reasoningEvents++;
      else if (item.type === 'todo_list') result.planEvents++;
    } else result.unknownEvents++;
  }
  result.uniqueToolItems = toolIds.size;
  result.complete = result.threadStartedEvents === 1 && result.turnStartedEvents === 1
    && result.turnCompletedEvents === 1 && finished && !active && completedMessages > 0
    && [...items.values()].every(item => item.completed)
    && !result.limitsExceeded && !result.unknownEvents && !result.malformedEvents && !result.failureEvents;
  if (result.complete) result.status = result.toolEvents ? 'OBSERVED_TOOL_ITEMS' : 'OBSERVED_NO_TOOL_ITEMS';
  return result;
}
