# WireCraft Relay

The relay has no database. Rooms, password proofs, active sockets, and their expiry live only in memory. Restarting it closes rooms.

## Run locally

```powershell
npm ci
npm test
npm start
```

The development endpoint is `ws://127.0.0.1:8080/ws`; its health check is `http://127.0.0.1:8080/healthz`.

## Deploy

Use `docker compose up -d --build` on a host reachable from the internet. Put TLS termination (for example, Caddy or nginx) in front of port 8080 and configure the release mod with `wss://your-domain.example/ws`. Do not publish an unsecured `ws://` endpoint.

No player creates an account or configures their router. The relay is still an internet service and must be operated by the project owner or an approved host.
