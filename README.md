# Private Chat (Spring Boot + PostgreSQL)

Real-time **direct messages** — only the sender and recipient see each conversation (not a public IRC-style channel).

- **PostgreSQL** — messages stored with `from_user` / `to_user`
- **WebSocket (STOMP)** — each user gets messages on `/user/queue/messages`
- **Presence** — online users broadcast on `/topic/presence`
- **REST** — conversation history and contacts for the signed-in display name
- **Media** — attach images/files (📎); bytes stored as PostgreSQL BLOBs (`media_assets`); `POST /api/media` uploads, `GET /api/media/{id}` fetches; messages store only the URL + metadata

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL (local or Docker)

## Database

Local Homebrew (default `application.properties` uses user `surya`, empty password):

```bash
createdb chatapp
# or: psql -d postgres -c 'CREATE DATABASE chatapp;'
```

If you previously ran the public chat version, reset the messages table so columns match:

```bash
psql -d chatapp -c 'DROP TABLE IF EXISTS chat_messages;'
```

Docker alternative — update `application.properties` to match:

```bash
docker compose up -d
# url=jdbc:postgresql://localhost:5432/chatapp
# username=chat
# password=chat
```

## Run the app

Override DB credentials with env vars if needed (CI uses `chat` / `chat` from Docker Compose):

```bash
# optional when using docker compose postgres:
# export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chatapp
# export SPRING_DATASOURCE_USERNAME=chat
# export SPRING_DATASOURCE_PASSWORD=chat

mvn spring-boot:run
```

Open http://localhost:8080 — **register** or **log in** with username + password in two browser profiles/tabs. Start a chat with the other person’s name; messages stay private to that pair.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs **unit tests** (`mvn test`) on pushes and PRs to `main`.

Optional local Playwright checks live under `e2e/` (not run in CI):

```bash
mvn -DskipTests package
# start app with DB available, then:
cd e2e && npm install && npx playwright install chromium && npm test
```

## How it works

1. **Register** (`POST /api/auth/register`) or **log in** (`POST /api/auth/login`) with username + password (BCrypt). Response includes a session `token`.
2. STOMP `CONNECT` must send header `token` (session from login); the server resolves the principal from `auth_sessions`. Username-only connect is rejected.
3. **Password reset:** `POST /api/auth/forgot-password` `{ username }` issues a one-time token (returned in JSON when `chat.auth.return-reset-token=true`, since there is no email). Then `POST /api/auth/reset-password` `{ token, newPassword }`. Logged-in users can also `POST /api/auth/change-password` with `X-Auth-Token` and `{ currentPassword, newPassword }`. Reset/change revoke all sessions.
3. Send DMs to `/app/chat` with `{ "to": "…", "text": "…" }`, optionally after uploading with `POST /api/media` and including `mediaUrl`, `mediaContentType`, `mediaFileName`, `messageType` (`IMAGE` / `FILE`).
4. Server persists the message and delivers it only via `convertAndSendToUser` to sender and recipient queues.
5. History: `GET /api/conversation?me=…&peer=…` returns a **cursor page** of that pair’s messages (default **50**, newest page first). Response: `{ messages, nextBeforeId, hasMore }`. Messages within a page are oldest → newest. Pass `beforeId=<nextBeforeId>` to load the next older page; the exclusive id keyset guarantees each message appears at most once when paging through the whole conversation. Optional `limit` (1–100).
6. Media is stored in table `media_assets` (bytea BLOB), not on the client or app `resources/`. Clients fetch with `GET /api/media/{id}` (max 10 MB; images, PDF, text, common audio/video). For large-scale production, prefer object storage (S3/MinIO) with the same API shape.
7. Sidebar: `GET /api/contacts?me=…` returns `[{ username, unreadCount }, …]` — `unreadCount` is inbound messages to `me` with status not `READ`. Opening a chat marks those messages READ (existing status flow) and clears the badge; live `SENT` frames while another chat is open increment the badge.
8. **Offline delivery:** messages are always written to PostgreSQL on send. If the recipient is offline, STOMP cannot push them live; when they reconnect and subscribe to `/user/queue/messages`, the server automatically replays all still-`SENT` inbound messages (also via `/app/catchup`). The client marks them DELIVERED/READ as usual — no page reload required.

## Layout

```
src/main/java/com/example/chat/
  config/       WebSocket, STOMP auth interceptor, password encoder
  controller/   Thin HTTP/STOMP adapters (auth, chat, media)
  domain/       MessageType enum
  dto/          Request/response records (auth, chat, media)
  exception/    AuthException + GlobalExceptionHandler
  model/        JPA entities
  repository/   Spring Data
  service/      AuthService, SessionService, PasswordResetService,
                ChatService, MediaStorageService, PresenceService
  util/         Usernames, Passwords, TokenGenerator (DRY)
src/main/resources/static/index.html
```
