# Secure Upload Skeleton

Micronaut + Kotlin Backend mit Vue/Vite/TypeScript Frontend für clientseitig verschlüsselte Datei-Uploads.

## Start lokal

```bash
./gradlew :backend:run
./gradlew :frontend:npm_run_dev
```

Frontend: http://localhost:5173  
Backend: http://localhost:8080

## API (Swagger / OpenAPI)

Das Backend generiert zur Build-Zeit eine OpenAPI-Spezifikation (micronaut-openapi KSP) und stellt eine interaktive Swagger UI bereit via Webjar (keine zusätzliche Controller-Weiterleitung nötig).

- Backend starten:

```bash
./gradlew :backend:run
```

- Swagger UI (Dev): http://localhost:8080/webjars/swagger-ui/<version>/index.html?url=/swagger/openapi.yml
- Alternativer (eingängiger) Link zur UI: http://localhost:8080/swagger-ui#/default

Hinweis: Die verwendete Swagger UI-Version wird im Version Catalog (`gradle/libs.versions.toml`) verwaltet (Schlüssel `swaggerUi`). Ersetze `<version>` durch die dort definierte Version oder nutze die kurze URL `http://localhost:8080/webjars/swagger-ui/index.html?url=/swagger/openapi.yml`, die in vielen Setups automatisch zur installierten Webjar-Version aufgelöst wird.

## Komplett bauen

```bash
./gradlew buildAll
```

Das Frontend wird gebaut und nach `backend/src/main/resources/public` kopiert, damit Micronaut es statisch ausliefert.

Hinweis (Windows): falls `buildAll` beim Node-Setup mit "unable to delete node.exe" fehlschlägt, stelle sicher, dass kein
Gradle/Node/Vite-Prozess mehr läuft (Dev-Server stoppen) und versuche erneut.

## E2E (Playwright)

Browser (Chromium) installieren:

```bash
./gradlew :frontend:npmPlaywrightInstall
```

Die Gradle-Tasks nutzen standardmäßig Playwright-Chromium (und ignorieren ein ggf. global gesetztes `PW_CHANNEL`), damit E2E-Läufe deterministisch sind.
Wenn du stattdessen einen lokal installierten Browser nutzen willst, starte Playwright direkt im `frontend/` Ordner, z.B. `PW_CHANNEL=chrome npm run test:e2e` (oder `msedge`).

E2E-Tests laufen lassen (prod-like: Frontend build + copy, dann Backend als Webserver):

```bash
./gradlew :frontend:npmE2e
```

Hinweis: Für deterministische E2E-Läufe startet Playwright standardmäßig ein eigenes Backend mit E2E-Config (kleine Chunk-Size etc.) auf Port `18080`
(damit es nicht mit einem Dev-Backend auf `8080` kollidiert). Wenn du einen anderen Port willst: `PW_PORT=18081 ./gradlew :frontend:npmE2e`.
Wenn du bewusst einen bereits laufenden Server wiederverwenden willst (z.B. beim Entwickeln einzelner Tests), setze `PW_REUSE_SERVER=true`.

Hinweis (Windows/Sandbox): Falls Playwright mit einem EPERM-Fehler beim Schreiben nach `%LOCALAPPDATA%\\ms-playwright` abbricht,
stellt das Projekt `PLAYWRIGHT_BROWSERS_PATH=0` ein, damit Browser lokal im Projekt abgelegt werden.

Report: `frontend/playwright-report/`

## Bauplan

Siehe `docs/IMPLEMENTATION_PLAN.md`.

## Konfiguration (Backend + E2E)

Das Backend wird über Micronaut-Config gesteuert. Du kannst Werte entweder als **System Properties** (`-D...`) oder als **Environment Variables** setzen.

**Wichtig:** Micronaut mappt Properties auf Env Vars mit `_` statt `-` und in UPPERCASE (z.B. `secure-upload.storage-dir` → `SECURE_UPLOAD_STORAGE_DIR`).

**Backend (Micronaut)**
- `micronaut.environments` → `MICRONAUT_ENVIRONMENTS` (z.B. `e2e`)
- `micronaut.server.port` → `MICRONAUT_SERVER_PORT` (Default: `8080`)

**secure-upload Settings**
- `secure-upload.storage-dir` → `SECURE_UPLOAD_STORAGE_DIR` (Default: `storage/uploads`; Docker/Compose empfohlen: `/data/uploads` + Volume-Mount)
- `secure-upload.protocol-version` → `SECURE_UPLOAD_PROTOCOL_VERSION` (Default: `v1`)
- `secure-upload.chunk-size` → `SECURE_UPLOAD_CHUNK_SIZE` (Plaintext Chunk Size, Default: `8388608`)
- `secure-upload.min-chunk-bytes` → `SECURE_UPLOAD_MIN_CHUNK_BYTES` (Default: `1048576`)
- `secure-upload.max-chunk-bytes` → `SECURE_UPLOAD_MAX_CHUNK_BYTES` (Max Packed Request Size, Default: `67108864`)
- `secure-upload.default-expiry-hours` → `SECURE_UPLOAD_DEFAULT_EXPIRY_HOURS` (Default: `72`)
- `secure-upload.max-expiry-hours` → `SECURE_UPLOAD_MAX_EXPIRY_HOURS` (Default: `168`)
- `secure-upload.trust-proxy-headers` → `SECURE_UPLOAD_TRUST_PROXY_HEADERS` (Default: `false`)
- `secure-upload.rate-limit-enabled` → `SECURE_UPLOAD_RATE_LIMIT_ENABLED` (Default: `true`)
- `secure-upload.max-active-uploads-per-ip` → `SECURE_UPLOAD_MAX_ACTIVE_UPLOADS_PER_IP` (Default: `5`)
- `secure-upload.init-requests-per-minute` → `SECURE_UPLOAD_INIT_REQUESTS_PER_MINUTE` (Default: `30`)
- `secure-upload.download-requests-per-minute` → `SECURE_UPLOAD_DOWNLOAD_REQUESTS_PER_MINUTE` (Default: `60`)
- `secure-upload.download-chunk-requests-per-minute` → `SECURE_UPLOAD_DOWNLOAD_CHUNK_REQUESTS_PER_MINUTE` (Default: `600`)
- `secure-upload.upload-chunk-requests-per-minute` → `SECURE_UPLOAD_UPLOAD_CHUNK_REQUESTS_PER_MINUTE` (Default: `600`)
- `secure-upload.cleanup-enabled` → `SECURE_UPLOAD_CLEANUP_ENABLED` (Default: `true`)
- `secure-upload.cleanup-interval-minutes` → `SECURE_UPLOAD_CLEANUP_INTERVAL_MINUTES` (Default: `15`)

**E2E (Playwright)**
- `PW_PORT` (Default: `18080`) – Port, auf dem Playwright das Backend startet und gegen das getestet wird
- `PW_REUSE_SERVER=true` – bereits laufenden Server wiederverwenden (sonst startet Playwright ein eigenes Backend mit E2E-Config)
- `PW_CHANNEL=chrome|msedge` – optional lokal installierten Browser verwenden (Gradle-Tasks überschreiben das standardmäßig, für deterministische Runs)



## Docker build

Standard (Compose)

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

App: http://localhost:11433

Stoppen / Aufräumen
```bash
docker compose down
docker compose down -v  # löscht auch named volumes (uploads + pgdata)
```
