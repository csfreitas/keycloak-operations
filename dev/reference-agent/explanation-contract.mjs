// Shared bounds for validation and published instructions. No provider or model runtime.
export const EXPLANATION_LIMITS = Object.freeze({
  maxItemsPerCategory: 20,
  maxTextUtf16Units: 1000,
  minReferencesPerItem: 1,
  maxReferencesPerItem: 10,
});

export function explanationContractText() {
  const limits = EXPLANATION_LIMITS;
  return `Return only one JSON object, without Markdown fences or surrounding prose, with
exactly these seven fields: \`profileVersion\`, \`targetId\`, \`reportId\`, \`facts\`,
\`observations\`, \`hypotheses\`, \`recommendations\`. Copy the three identity fields and
the entire \`facts\` array exactly from the host packet, preserving fact order, paths,
value types, nulls and empty containers. Do not add \`limitations\`, \`kind\`, tool calls
or other fields. JSON object key order is not significant; fact array order is.

Each of the three narrative fields is a required array containing 0–${limits.maxItemsPerCategory} items;
an empty array is permitted. Each item has exactly \`text\` and \`references\`.
\`text\` must be a nonblank string (not empty after JavaScript trim), with at most
${limits.maxTextUtf16Units} UTF-16 code units, including whitespace. Do not include U+0000–U+001F or
U+007F, including decoded newline or tab escapes. Unicode accents and emoji are
allowed; a supplementary character such as an emoji consumes two UTF-16 units.

\`references\` must be an array of ${limits.minReferencesPerItem}–${limits.maxReferencesPerItem} distinct strings per item, each exactly a
\`path\` present in \`facts\`. Do not invent paths, normalize escaped paths, or substitute
source URL values for paths. The same path may appear in different items. Select up
to ${limits.maxReferencesPerItem} relevant supporting paths; do not reproduce every related path automatically.
If more are needed, split the explanation into independently supported items within
the item limit. Never drop copied facts or invent support to satisfy these bounds.
An operator may impose stricter trial limits, never expand this contract. Invalid
output is rejected, not coerced, clipped, repaired or automatically retried.`;
}
