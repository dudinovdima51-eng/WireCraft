import crypto from 'node:crypto';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';

const PORT = Number.parseInt(process.env.PORT ?? '8080', 10);
const MAX_PLAYERS = Number.parseInt(process.env.MAX_PLAYERS ?? '8', 10);
const TTL_MS = Number.parseInt(process.env.ROOM_TTL_MS ?? String(6 * 60 * 60 * 1000), 10);

export function createRelay({ maxPlayers = MAX_PLAYERS, ttlMs = TTL_MS } = {}) {
  const rooms = new Map();
  const equal = (left, right) => {
    const a = Buffer.from(left ?? ''), b = Buffer.from(right ?? '');
    return a.length === b.length && crypto.timingSafeEqual(a, b);
  };
  const json = (response, status, body) => response.writeHead(status, { 'content-type': 'application/json' }).end(JSON.stringify(body));
  const read = async request => {
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  };
  const clean = () => { const now = Date.now(); for (const [code, room] of rooms) if (room.expiresAt < now) rooms.delete(code); };
  const server = http.createServer(async (request, response) => {
    clean();
    if (request.method === 'GET' && request.url === '/healthz') return json(response, 200, { ok: true, rooms: rooms.size });
    if (request.method === 'POST' && request.url === '/v1/rooms') {
      try {
        const body = await read(request);
        if (!/^[A-Z0-9]{6}$/.test(body.code ?? '') || !body.salt || !body.proof || !body.hostToken) return json(response, 400, { error: 'invalid room request' });
        if (rooms.has(body.code)) return json(response, 409, { error: 'room code already exists' });
        const requested = Number(body.maxPlayers);
        const capacity = Number.isInteger(requested) ? Math.min(Math.max(requested, 1), maxPlayers) : maxPlayers;
        rooms.set(body.code, { salt: body.salt, proof: body.proof, hostToken: body.hostToken, capacity, host: null, guests: new Map(), streams: new Map(), expiresAt: Date.now() + ttlMs });
        return json(response, 201, { code: body.code, expiresAt: Date.now() + ttlMs });
      } catch { return json(response, 400, { error: 'invalid JSON' }); }
    }
    const match = request.url?.match(/^\/v1\/rooms\/([A-Z0-9]{6})$/);
    if (request.method === 'GET' && match) {
      const room = rooms.get(match[1]);
      return room ? json(response, 200, { salt: room.salt, capacity: room.capacity }) : json(response, 404, { error: 'room not found' });
    }
    return json(response, 404, { error: 'not found' });
  });
  const wss = new WebSocketServer({ noServer: true, maxPayload: 96 * 1024 });
  server.on('upgrade', (request, socket, head) => {
    if (request.url !== '/ws') return socket.destroy();
    wss.handleUpgrade(request, socket, head, ws => wss.emit('connection', ws));
  });
  const send = (ws, message) => { if (ws.readyState === 1) ws.send(JSON.stringify(message)); };
  const error = (ws, value) => { send(ws, { type: 'error', error: value }); ws.close(1008, value); };
  const closeStream = (room, stream, except) => {
    if (room.host && room.host !== except) send(room.host, { type: 'close', stream });
    for (const guest of room.guests.values()) if (guest !== except) send(guest, { type: 'close', stream });
  };
  wss.on('connection', ws => {
    let room, role;
    ws.once('message', raw => {
      let auth; try { auth = JSON.parse(raw); } catch { return error(ws, 'invalid auth'); }
      if (auth.type !== 'auth' || !/^[A-Z0-9]{6}$/.test(auth.code ?? '')) return error(ws, 'invalid auth');
      room = rooms.get(auth.code); if (!room || !equal(room.proof, auth.proof)) return error(ws, 'invalid room or password');
      role = auth.role;
      if (role === 'host') {
        if (!equal(room.hostToken, auth.hostToken)) return error(ws, 'invalid host token');
        if (room.host) room.host.close(4000, 'host replaced'); room.host = ws;
      } else if (role === 'guest') {
        if (room.guests.size >= room.capacity) return error(ws, 'room is full');
        const id = crypto.randomUUID(); room.guests.set(id, ws); ws.wirecraftGuestId = id;
      } else return error(ws, 'invalid role');
      room.expiresAt = Date.now() + ttlMs; send(ws, { type: 'ready', mode: 'relay' });
      ws.on('message', data => route(ws, room, role, data));
    });
    ws.on('close', () => {
      if (!room) return;
      if (role === 'host' && room.host === ws) { room.host = null; for (const guest of room.guests.values()) error(guest, 'host disconnected'); room.guests.clear(); room.streams.clear(); }
      if (role === 'guest') { room.guests.delete(ws.wirecraftGuestId); for (const [stream, owner] of room.streams) if (owner === ws) { room.streams.delete(stream); closeStream(room, stream, ws); } }
    });
  });
  function route(sender, room, role, raw) {
    let message; try { message = JSON.parse(raw); } catch { return error(sender, 'invalid message'); }
    if (!['open', 'data', 'close', 'candidate'].includes(message.type) || typeof message.stream !== 'string') return error(sender, 'invalid message');
    // The relay only routes opaque Base64 frame payloads. It does not decode, persist, or log them.
    if (role === 'guest') {
      if (!room.host) return error(sender, 'host unavailable');
      if (message.type === 'open') room.streams.set(message.stream, sender);
      if (!room.streams.has(message.stream)) return error(sender, 'unknown stream');
      if (message.type === 'close') room.streams.delete(message.stream);
      send(room.host, message);
    } else {
      const owner = room.streams.get(message.stream);
      if (!owner) return error(sender, 'unknown stream');
      if (message.type === 'close') room.streams.delete(message.stream);
      send(owner, message);
    }
  }
  return { server, rooms, listen: port => new Promise(resolve => server.listen(port, resolve)), close: () => new Promise(resolve => server.close(resolve)) };
}

if (process.argv[1] && fileURLToPath(import.meta.url).toLowerCase() === process.argv[1].toLowerCase()) {
  const relay = createRelay(); relay.listen(PORT).then(() => console.log(`WireCraft Relay listening on :${PORT}`));
}
