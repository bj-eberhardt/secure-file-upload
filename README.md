# Secure Upload Skeleton

Micronaut + Kotlin backend with a Vue/Vite/TypeScript frontend for client-side encrypted file uploads.

## Local development

```bash
./gradlew :backend:run
./gradlew :frontend:npm_run_dev
```

Frontend: http://localhost:5173  
Backend: http://localhost:8080

## API (Swagger / OpenAPI)

The backend generates an OpenAPI specification at build time (micronaut-openapi KSP) and serves an interactive Swagger UI via WebJar (no additional controller forwarding required).

- Start the backend:

```bash
./gradlew :backend:run
```

- Swagger UI (dev): http://localhost:8080/webjars/swagger-ui/<version>/index.html?url=/swagger/openapi.yml
- Alternative (short) link: http://localhost:8080/swagger-ui#/default

Note: The Swagger UI version is managed in the version catalog (`gradle/libs.versions.toml`, key `swaggerUi`). Replace `<version>` with that value, or use `http://localhost:8080/webjars/swagger-ui/index.html?url=/swagger/openapi.yml`, which is resolved to the installed WebJar version in many setups.

## Full build

```bash
./gradlew buildAll
```

The frontend is built and copied to `backend/src/main/resources/public` so Micronaut can serve it as static content.

Note (Windows): if `buildAll` fails during Node setup with "unable to delete node.exe", make sure no Gradle/Node/Vite process is still running (stop the dev server) and try again.

## E2E (Playwright)

Install browser (Chromium):

```bash
./gradlew :frontend:npmPlaywrightInstall
```

The Gradle tasks use Playwright Chromium by default (and ignore any globally set `PW_CHANNEL`) to keep E2E runs deterministic. If you want to use a locally installed browser instead, run Playwright directly in `frontend/`, e.g. `PW_CHANNEL=chrome npm run test:e2e` (or `msedge`).

Run E2E tests (prod-like: build + copy frontend, then run backend as web server):

```bash
./gradlew :frontend:npmE2e
```

Note: For deterministic E2E runs, Playwright starts its own backend with an E2E config (smaller chunk size, etc.) on port `18080` (so it does not collide with a dev backend on `8080`). To use a different port: `PW_PORT=18081 ./gradlew :frontend:npmE2e`. If you intentionally want to reuse an already running server (e.g. when developing individual tests), set `PW_REUSE_SERVER=true`.

Note (Windows/Sandbox): if Playwright fails with an EPERM error when writing to `%LOCALAPPDATA%\\ms-playwright`, this project sets `PLAYWRIGHT_BROWSERS_PATH=0` so browsers are stored locally in the project.

Report: `frontend/playwright-report/`

## Docs

- Threat model: `docs/THREAT_MODEL.md`
- Crypto notes: `docs/CRYPTO_NOTES.md`

## Configuration (Backend + E2E)

The backend is configured via Micronaut config. You can set values either as **system properties** (`-D...`) or as **environment variables**.

Important: Micronaut maps properties to env vars using `_` instead of `-` and in uppercase (e.g. `secure-file-upload.storage-dir` -> `SECURE_FILE_UPLOAD_STORAGE_DIR`).

**Backend (Micronaut)**
- `micronaut.environments` -> `MICRONAUT_ENVIRONMENTS` (e.g. `e2e`)
- `micronaut.server.port` -> `MICRONAUT_SERVER_PORT` (Default: `8080`)

**secure-file-upload settings**
- `secure-file-upload.storage-dir` -> `SECURE_FILE_UPLOAD_STORAGE_DIR` (Default: `storage/uploads`; Docker/Compose recommended: `/data/uploads` + volume mount)
- `secure-file-upload.protocol-version` -> `SECURE_FILE_UPLOAD_PROTOCOL_VERSION` (Default: `v1`)
- `secure-file-upload.chunk-size` -> `SECURE_FILE_UPLOAD_CHUNK_SIZE` (Plaintext chunk size, Default: `8388608`)
- `secure-file-upload.min-chunk-bytes` -> `SECURE_FILE_UPLOAD_MIN_CHUNK_BYTES` (Default: `1048576`)
- `secure-file-upload.max-chunk-bytes` -> `SECURE_FILE_UPLOAD_MAX_CHUNK_BYTES` (Max packed request size, Default: `67108864`)
- `secure-file-upload.default-expiry-hours` -> `SECURE_FILE_UPLOAD_DEFAULT_EXPIRY_HOURS` (Default: `72`)
- `secure-file-upload.max-expiry-hours` -> `SECURE_FILE_UPLOAD_MAX_EXPIRY_HOURS` (Default: `168`)
- `secure-file-upload.trust-proxy-headers` -> `SECURE_FILE_UPLOAD_TRUST_PROXY_HEADERS` (Default: `false`)
- `secure-file-upload.rate-limit-enabled` -> `SECURE_FILE_UPLOAD_RATE_LIMIT_ENABLED` (Default: `true`)
- `secure-file-upload.max-active-uploads-per-ip` -> `SECURE_FILE_UPLOAD_MAX_ACTIVE_UPLOADS_PER_IP` (Default: `5`)
- `secure-file-upload.init-requests-per-minute` -> `SECURE_FILE_UPLOAD_INIT_REQUESTS_PER_MINUTE` (Default: `30`)
- `secure-file-upload.download-requests-per-minute` -> `SECURE_FILE_UPLOAD_DOWNLOAD_REQUESTS_PER_MINUTE` (Default: `60`)
- `secure-file-upload.download-chunk-requests-per-minute` -> `SECURE_FILE_UPLOAD_DOWNLOAD_CHUNK_REQUESTS_PER_MINUTE` (Default: `600`)
- `secure-file-upload.upload-chunk-requests-per-minute` -> `SECURE_FILE_UPLOAD_UPLOAD_CHUNK_REQUESTS_PER_MINUTE` (Default: `600`)
- `secure-file-upload.cleanup-enabled` -> `SECURE_FILE_UPLOAD_CLEANUP_ENABLED` (Default: `true`)
- `secure-file-upload.cleanup-interval-minutes` -> `SECURE_FILE_UPLOAD_CLEANUP_INTERVAL_MINUTES` (Default: `15`)

**E2E (Playwright)**
- `PW_PORT` (Default: `18080`) - port used for the backend Playwright starts and tests against
- `PW_REUSE_SERVER=true` - reuse an already running server (otherwise Playwright starts its own backend with E2E config)
- `PW_CHANNEL=chrome|msedge` - optionally use a locally installed browser (Gradle tasks override this by default for deterministic runs)

## Docker build

Default (Compose)

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

App: http://localhost:11433

Stop / cleanup

```bash
docker compose down
docker compose down -v  # also deletes named volumes (uploads)
```
