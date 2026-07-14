import assert from 'node:assert/strict';
import test from 'node:test';
import { WebSocket } from 'ws';
import { createRelay } from '../server.js';

let relay, base;
test.before(async () => { relay = createRelay({ maxPlayers: 2 }); await relay.listen(0); base = `http://127.0.0.1:${relay.server.address().port}`; });
test.after(async () => relay.close());

test('creates a room and only discloses its salt', async () => {
  const created = await fetch(`${base}/v1/rooms`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: 'ABC123', salt: 'salt', proof: 'proof', hostToken: 'token', maxPlayers: 99 }) });
  assert.equal(created.status, 201);
  const room = await (await fetch(`${base}/v1/rooms/ABC123`)).json();
  assert.deepEqual(room, { salt: 'salt', capacity: 2 });
  assert.equal((await fetch(`${base}/v1/rooms/ABC123`)).status, 200);
});

test('rejects invalid or duplicate rooms', async () => {
  const invalid = await fetch(`${base}/v1/rooms`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
  assert.equal(invalid.status, 400);
  const duplicate = await fetch(`${base}/v1/rooms`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: 'ABC123', salt: 's', proof: 'p', hostToken: 't' }) });
  assert.equal(duplicate.status, 409);
});

const connect = (message) => new Promise((resolve, reject) => {
  const ws = new WebSocket(base.replace('http', 'ws') + '/ws');
  ws.once('error', reject);
  ws.once('open', () => ws.send(JSON.stringify(message)));
  ws.once('message', data => resolve({ ws, message: JSON.parse(data) }));
});
const once = ws => new Promise(resolve => ws.once('message', data => resolve(JSON.parse(data))));

test('routes opaque stream data only to the stream owner', async () => {
  const created = await fetch(`${base}/v1/rooms`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: 'XYZ789', salt: 'salt', proof: 'proof', hostToken: 'token', maxPlayers: 2 }) });
  assert.equal(created.status, 201);
  const host = await connect({ type: 'auth', role: 'host', code: 'XYZ789', proof: 'proof', hostToken: 'token' });
  const guest = await connect({ type: 'auth', role: 'guest', code: 'XYZ789', proof: 'proof' });
  assert.equal(host.message.type, 'ready'); assert.equal(guest.message.type, 'ready');
  const fromGuest = once(host.ws); guest.ws.send(JSON.stringify({ type: 'open', stream: 'stream-1' }));
  assert.deepEqual(await fromGuest, { type: 'open', stream: 'stream-1' });
  const fromHost = once(guest.ws); host.ws.send(JSON.stringify({ type: 'data', stream: 'stream-1', payload: 'aGVsbG8=' }));
  assert.deepEqual(await fromHost, { type: 'data', stream: 'stream-1', payload: 'aGVsbG8=' });
  host.ws.close(); guest.ws.close();
});
