# VzBot Discord Bot & Website

A Discord bot and companion website for the [VzBot](https://vzbot.org) 3D printer community. Manages serial number assignments, printer profiles, community statistics, and blog content.

## Features

- **Serial Number Management** - Apply for, review, and assign serial numbers through Discord with auto-generated STL badge files
- **Printer Profiles** - Catalog of VzBot printer variants with media and specifications
- **Community Map** - Interactive 3D globe showing VzBot builds worldwide
- **Blog System** - Community posts and announcements
- **Analytics** - Registration trends and printer distribution stats

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Kotlin, Ktor, Exposed ORM |
| Frontend | Nuxt 4, Vue 3, Tailwind CSS |
| Database | MariaDB 11.5 |
| Discord | JDA 5, KtBot |
| Deployment | Docker, GitHub Container Registry |

## Project Structure

```
backend/     Kotlin/Ktor Discord bot and REST API
frontend/    Nuxt 4 website
```

## Setup

### Prerequisites

- Docker & Docker Compose
- (For local dev) Java 21, Bun

### Quick Start

1. Copy the environment template and fill in your values:
   ```sh
   cp .env.template .env
   ```

2. Configure required environment variables in `.env`:
   - `VZ_TOKEN` - Discord bot token
   - `VZ_ADMIN_ROLE` / `VZ_TEAM_ROLE` - Discord role IDs
   - `VZ_SERIAL_CATEGORY` - Discord category for serial ticket channels
   - `VZ_SERIAL_ANNOUNCEMENT_CHANNEL` - Channel for serial announcements
   - `VZ_SERIAL_BASE_PLATE_HOST_PATH` / `VZ_SERIAL_NUMBER_PLATES_HOST_PATH` - Host paths to STL files
   - `BACKEND_TOKEN` - Shared token for frontend-to-backend auth

3. Start the stack:
   ```sh
   docker compose up -d
   ```

The frontend will be available on port **3000**. The backend API runs on port **8080** (internal only by default).

## Monitoring

The bot reports whether it is actually working, restarts itself when it is not, and tells you either way.

### Health endpoint

`GET /health` (no token required) returns the state of each component:

```json
{
  "status": "UP",
  "uptimeSeconds": 4210,
  "components": {
    "discord": { "status": "UP", "detail": "connected, gateway ping 38ms" },
    "database": { "status": "UP", "detail": "reachable" }
  }
}
```

`UP` and `DEGRADED` answer `200`, `DOWN` answers `503`. `DEGRADED` means a component is expected to
recover on its own — usually JDA reconnecting — and deliberately does not fail the container
healthcheck, so brief gateway churn doesn't take the frontend down with it.

`GET /health/ping` is a bare liveness probe that returns `pong`.

### Watchdog

A watchdog checks health every 30s (after a 90s startup grace period) and, on top of the two
components above, verifies that the web server itself still answers.

| Consecutive bad checks | What happens |
|---|---|
| 2 (~1 min) | Alert sent to the Discord webhook |
| 6 (~3 min) | Process exits, Docker restarts the container |
| 2, if the gateway is in a state it cannot recover from | Process exits immediately |

Docker's healthcheck alone would not do this — an unhealthy container is only labelled, never
restarted, and `restart: unless-stopped` only fires when the process actually exits. A dead JDA
gateway does not exit the JVM on its own, which is why the bot could quietly stop working.

### Alerting

Both channels are optional and configured by URL, so you can point them anywhere.

**`VZ_ALERT_WEBHOOK_URL`** — a Discord webhook, posted to over plain HTTPS. It does not go through
JDA, so it still reaches the channel when the gateway is the thing that broke. Fires on
unhealthy, recovered, self-restart, and startup. A stream of startup messages is a crash loop.

**`VZ_HEARTBEAT_URL`** — a dead man's switch, pinged *only while the bot is fully healthy*. If the
pings stop, the external service raises the alarm. This is the piece that catches what nothing
inside the process can report: a killed JVM, a wedged container, a dead host, a lost uplink.

Any service that treats an inbound `GET` as "still alive" works — [healthchecks.io](https://healthchecks.io),
an Uptime Kuma push monitor, BetterStack. **Set the monitor's grace period to at least 5 minutes**
so a short gateway reconnect doesn't page anyone at 3am.

### Both are optional

`VZ_HEARTBEAT_URL` and `VZ_ALERT_WEBHOOK_URL` are the only variables the bot will start without.
Leave either unset — or omit it from the environment entirely — and that channel is simply
disabled: no startup error, no failed health check. The watchdog still restarts the bot, you just
don't hear about it.

In production they come from the GitHub secrets of the same name; an unset secret renders as an
empty value, which is treated as "disabled".

## Local Development

**Backend:**
```sh
cd backend
./gradlew run
```

**Frontend:**
```sh
cd frontend
bun install
bun run dev
```
