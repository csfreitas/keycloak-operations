// Local context/output budget, independent of the server's per-finding contract.
export const MAX_PACKET_BYTES = 262_144;

export function requirePacketBudget(packet) {
  if (Buffer.byteLength(JSON.stringify(packet), 'utf8') > MAX_PACKET_BYTES) {
    const error = new Error('INVALID_REPORT');
    error.code = 'INVALID_REPORT';
    throw error;
  }
  return packet;
}
