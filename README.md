# WireCraft

WireCraft is a Fabric 1.21.1 companion mod and relay service for sharing a single-player world with trusted friends over the internet. Everyone installs the mod. The host creates a password-protected room and shares its short code; guests join through a local proxy managed by the mod.

## Important security note

WireCraft is intended for trusted friends using offline-mode Minecraft. A room password controls entry, but an offline nickname is not a cryptographic identity: anyone with the password can claim an unused nickname. Do not expose a room code or password publicly.

## Development

The mod requires JDK 21 and Gradle 8.10+. The relay requires Node.js 20+.

```powershell
cd relay
npm ci
npm test
npm start
```

See [relay/README.md](relay/README.md) for Docker deployment and [docs/PROTOCOL.md](docs/PROTOCOL.md) for the relay protocol.

## Configuration

The release uses the project Relay at `ws://192.124.189.132:8080/ws`. You can override it in `config/wirecraft.json`. For production use, put the Relay behind TLS and use a `wss://` endpoint.
