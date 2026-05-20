# Codex Context: `secure-upload-skeleton`

Stand: **2026-05-20** (lokaler Workspace-Snapshot)

## Kurzüberblick

Skeleton für **clientseitig verschlüsselte Datei-Uploads**:

- **Backend:** Micronaut + Kotlin (JVM), liefert zusätzlich statische Frontend-Assets aus.
- **Frontend:** Vue 3 + Vite + TypeScript, verschlüsselt Upload-Daten im Browser und lädt sie chunkweise hoch.
- **Wichtiges Sicherheitsprinzip:** Der **Schlüssel bleibt im URL-Fragment** (`#key=...`) und wird dadurch **nicht an den Server gesendet**.

## Architektur / Module

- `backend/`: Micronaut API + Static File Serving
- `frontend/`: Vue/Vite App (Upload + Download Views)
- `docs/`: Implementierungsplan, Threat Model, Crypto Notes

Gradle Multi-Project:

- Root `build.gradle.kts`: registriert Task `buildAll` (Frontend bauen/kopieren → Backend build)
- `settings.gradle.kts`: `include("backend", "frontend")`

## Tooling & Versionen (aus Build-Konfiguration)

- **Java:** 21 (`backend/build.gradle.kts`)
- **Kotlin:** 2.1.0 (Root `build.gradle.kts`)
- **Micronaut Gradle Plugin:** 4.5.0
- **Node via Gradle:** Node `22.11.0`, npm `10.9.0` (`frontend/build.gradle.kts`, `download=true`)
- **Frontend deps:** `vue`, `vite`, `@vitejs/plugin-vue` und Dev-Deps (`typescript`, `vue-tsc`) stehen aktuell auf `"latest"` (`frontend/package.json`) → Builds sind nicht reproduzierbar.

## Lokaler Start / wichtige Gradle-Tasks

Laut `README.md` und `docs/IMPLEMENTATION_PLAN.md`:

- Backend dev: `./gradlew :backend:run` (Windows: `./gradlew.bat :backend:run`)
- Frontend dev: `./gradlew :frontend:npm_run_dev`
- Komplett build: `./gradlew buildAll`
  - baut das Frontend und kopiert es nach `backend/src/main/resources/public`

Vite Proxy für API:

- `frontend/vite.config.ts` proxyt `/api` → `http://localhost:8080`

## Backend: aktueller Stand

**API-Endpunkte** (`backend/src/main/kotlin/app/api/UploadController.kt`):

- `POST /api/uploads/init` → gibt `uploadId`, `chunkSize` und URLs zurück
- `PUT /api/uploads/{id}/chunks/{index}` → speichert Chunk als `.part`
- `POST /api/uploads/{id}/complete` → concat’t Chunks zu `blob.enc`, schreibt `upload.json`, optional `manifest.enc.b64`
- `GET /api/uploads/{id}/status` → Upload-Status (Chunks + completed)
- `GET /api/downloads/{id}` → liefert `blob.enc` als ByteArray (kein Streaming)

**Storage** (`backend/src/main/kotlin/app/storage/UploadStorage.kt`):

- Upload-ID: URL-safe Base64 ohne Padding (24 random bytes)
- Layout: `storage/uploads/<id>/chunks/*.part`, final `blob.enc`, `upload.json`, optional `manifest.enc.b64`
- Chunk-Count wird beim Complete geprüft; Chunk-Inhalt/Integrität wird nicht validiert.

**Config**:

- `backend/src/main/resources/application.yml`:
  - `secure-upload.storage-dir` (default `storage/uploads`)
  - `secure-upload.chunk-size` (default `8388608` = 8 MiB)
  - `secure-upload.default-expiry-hours` (default 72) — aktuell nur konfiguriert, nicht enforced

## Frontend: aktueller Stand

**Routing/Views**:

- `frontend/src/App.vue`: simple Pfadprüfung (`/d/...` → DownloadView, sonst UploadView), kein Router.
- `frontend/src/views/UploadView.vue`: init → placeholder “zip” → AES-GCM per Chunk → Upload → complete → Link anzeigen.
- `frontend/src/views/DownloadView.vue`: key aus `location.hash` importieren → Blob laden → placeholder decrypt → Download triggern.

**Upload-Pipeline (heute placeholder-lastig)**:

- `frontend/src/upload/chunkUploader.ts`:
  - `createZipPlaceholder(files)` erzeugt Blob **ohne echtes ZIP-Streaming** (aktuell: `new Blob(files)`).
  - lädt den gesamten Blob in RAM (`await zip.arrayBuffer()`), chunked dann per `slice`.
  - verschlüsselt jeden Chunk via `encryptChunk()` und sendet das gepackte Format.
- `frontend/src/crypto/encryptStream.ts`:
  - per Chunk AES-GCM, Nonce = 12 bytes random
  - AAD wird im Upload gesetzt als `${uploadId}:${index}:v1`
  - Pack-Format: `[u32 nonceLen BE][nonce][ciphertext]`

**Download-Pipeline**:

- `frontend/src/download/downloadAndDecrypt.ts`:
  - lädt `/api/downloads/{uploadId}` komplett in RAM (`arrayBuffer()`)
  - `decryptPayloadPlaceholder()` gibt aktuell **nur den Ciphertext als Blob** zurück (keine Entschlüsselung)

**Worker**:

- `frontend/src/workers/cryptoWorker.ts` ist Stub; UI nutzt aktuell direkte placeholder Calls.

## “Was funktioniert” vs “TODO”

Funktional vorhanden:

- Upload-ID erzeugen, Chunks speichern, finalen verschlüsselten Blob zusammenbauen
- Frontend: Key-Generierung/Export/Import (AES-GCM raw key base64url)
- Frontend: Chunkweise AES-GCM Verschlüsselung (pro Chunk Nonce)
- Link-Pattern `/d/<id>#key=<...>` und Client liest Key aus Fragment

Wichtige TODOs (laut Code + `docs/IMPLEMENTATION_PLAN.md`):

- **Echtes ZIP-Streaming** statt `createZipPlaceholder()` (kein RAM-Alles-auf-einmal)
- **Streaming Decrypt** passend zu Pack-Format und AAD-Policy
- **Crypto Worker** verdrahten (UI nicht blockieren, Progress/Abort)
- Backend: **Streaming Download** statt `Files.readAllBytes`, Expiry/Cleanup/Quotas/RateLimits
- Frontend deps pinnen (statt `"latest"`) für reproduzierbare Builds

## Nützliche Einstiegspunkte (Datei-Map)

- Backend API: `backend/src/main/kotlin/app/api/UploadController.kt`
- Backend Storage/Files: `backend/src/main/kotlin/app/storage/UploadStorage.kt`
- Backend Konfig: `backend/src/main/resources/application.yml`
- Frontend Upload Flow: `frontend/src/views/UploadView.vue`, `frontend/src/upload/chunkUploader.ts`
- Frontend Crypto: `frontend/src/crypto/encryptStream.ts`, `frontend/src/crypto/decryptStream.ts`, `frontend/src/crypto/keyDerivation.ts`
- ZIP Platzhalter: `frontend/src/zip/zipStreamWriter.ts`
- Download Flow: `frontend/src/views/DownloadView.vue`, `frontend/src/download/downloadAndDecrypt.ts`
- Hintergrund/Planung: `docs/IMPLEMENTATION_PLAN.md`, `docs/THREAT_MODEL.md`, `docs/CRYPTO_NOTES.md`

