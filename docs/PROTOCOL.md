# WireCraft Relay protocol

`POST /v1/rooms` receives a six-character room code, public per-room salt, SHA-256 password proof, host token, and requested capacity. `GET /v1/rooms/{code}` returns only `salt` and effective `capacity`.

Clients then open `GET /ws` and send `auth`. A host authenticates with its host token; a guest authenticates with its password proof. The server keeps no password and uses constant-time comparisons for stored secrets.

After `ready`, `open`, `data`, and `close` messages are routed between the host and guests. `data.payload` is opaque Base64 TCP bytes. The relay does not inspect, log, or persist frame contents. `candidate` is reserved for direct UDP candidate exchange; the current release always uses the reliable relay transport after negotiating candidates, ensuring that restrictive NATs still work.
