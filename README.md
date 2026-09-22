# Amarillo

Amarillo is an unofficial, browser-based implementation of the *Star Fleet Battles* tactical starship combat game. It combines a server-authoritative Java rules engine with a Spring Boot multiplayer server and a React/TypeScript client.

The project is under active development. Rules fidelity is the primary goal: implementations follow the Captain's Master Rulebook, and rule numbers are cited in the code where practical.

## Current state

Amarillo supports playable local and networked games rather than serving only as a collection of combat utilities. Players can create or join a game, assemble fleets, take control of ships, and share a synchronized tactical map through the browser.

Implemented areas include:

- 32-impulse turn sequencing and phase management
- Energy allocation and weapon arming
- Hex-map movement, facing, turn modes, and speed changes
- Direct-fire weapons and damage allocation
- Drones, plasma torpedoes, shuttles, and wild weasels
- Tractors, mines, cloaking, electronic warfare, and lock-on
- Boarding actions, hit-and-run raids, and disengagement
- Fleet construction and scenario validation
- Player-specific views that keep hidden information on the server
- REST actions with live game-state updates over STOMP/WebSocket
- Reconnection to an existing player seat from the same browser

Coverage varies by rule and system. Amarillo should be treated as a work in progress, not yet as a complete implementation of every SFB rule.

## Architecture

The repository has three layers:

| Module | Purpose |
| --- | --- |
| `amarillo-core` | Plain-Java rules engine and authoritative game model. It has no Spring dependency. |
| `amarillo-server` | Spring Boot REST and STOMP/WebSocket server. It owns game sessions, validates player actions, and publishes player-specific state snapshots. |
| `amarillo-web` | React/TypeScript/Vite client. It renders server state and submits player actions; authoritative rules remain in the Java engine. |

`com.sfb.Game` is the aggregate root for a game. Rules-heavy domains are divided among collaborators such as movement, damage, tractor, boarding, launch, mine, seeker-control, and lock-on resolvers.

## Prerequisites

- JDK 25
- Maven 3.9 or later
- Node.js with npm (a current LTS release is recommended)

## Build and test

From the repository root, build the Java modules and run their tests:

```bash
mvn clean install
```

The server resolves `amarillo-core` from the local Maven repository, so use `install` from the root after changing core or server code. Building only the server module can otherwise run against a stale core artifact.

Build, lint, and test the web client separately:

```bash
cd amarillo-web
npm install
npm run lint
npm test
npm run build
```

The Vite development server does not perform the full TypeScript build check. Use `npm run build` before considering a frontend change complete.

## Run locally

Start the backend from the repository root so the relative `data/` paths resolve correctly:

```bash
cd amarillo-server
mvn spring-boot:run
```

The API server listens on port `8080` by default.

In another terminal, start the frontend:

```bash
cd amarillo-web
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The Vite server proxies `/api` and `/ws` to the Spring Boot server. It binds to all local interfaces, so other devices on the same network can connect using the development machine's address.

## Data

Ship, faction, and scenario definitions are stored as JSON under `data/`. Core tests generally construct ships using sample builders rather than reading the filesystem. When changing a ship definition, update the corresponding sample builder so test fixtures remain aligned with live data.

## Current deployment limitations

The current application is best suited to development and trusted local-network play:

- Active game sessions are held in server memory and are lost when the server restarts.
- The React client and Spring server do not yet have a unified production package.
- Internet deployment still needs hardened origin rules, authenticated WebSocket subscriptions, and environment-specific configuration.
- Player access currently depends on possession of a generated player token stored by the browser.

These are the main areas to address before hosting long-running public internet games.

## Contributing

Keep authoritative rules in `amarillo-core`. The server should translate and validate requests, while the web client should render state and collect player intent rather than independently resolving game outcomes.

When adding or changing a rule:

1. Cite the applicable SFB rule number near the implementation.
2. Search all three layers for mirrored validations or previews that may also need updating.
3. Add or update focused tests.
4. Run `mvn clean install` and the relevant frontend checks.

## Legal notice

Amarillo is an unofficial fan project. *Star Fleet Battles* and related names and game materials belong to their respective rights holders. This repository is not affiliated with or endorsed by Amarillo Design Bureau.
