# Implementierungsplan: Secure Upload

## Ziel

Eine Micronaut-Anwendung in Kotlin, die ein TypeScript-Frontend ausliefert. Nutzer können mehrere Dateien auswählen. Die Dateien werden lokal im Browser zu einem Archiv zusammengeführt, clientseitig verschlüsselt und dann als verschlüsselter Blob in Chunks hochgeladen. Der Server kennt weder Dateiinhalte noch Dateinamen noch Metadaten.

Der Nutzer erhält anschließend einen Link:

```text
https://host/d/<uploadId>#key=<base64url-key>
```

Das Fragment nach `#` wird nicht an den Server gesendet. Der Schlüssel bleibt damit clientseitig.

## Architektur

```text
secure-upload/
├─ backend/   Micronaut Kotlin API + statische Auslieferung
├─ frontend/  Vue 3 + Vite + TypeScript
└─ docs/      Bauplan, Threat Model, Crypto-Notizen
```

## Backend-Aufgaben

### Endpunkte

```text
POST /api/v1/uploads/init
PUT  /api/v1/uploads/{id}/chunks/{index}
POST /api/v1/uploads/{id}/complete
GET  /api/v1/uploads/{id}/status
 
```

### Server darf nur speichern

```text
storage/uploads/<uploadId>/
├─ chunks/
│  ├─ 00000000.part
│  └─ 00000001.part
├─ manifest.enc.b64
└─ upload.json
```

`upload.json` darf keine vertraulichen Metadaten enthalten. Erlaubt sind technische Angaben wie Upload-ID, Chunk-Anzahl, verschlüsselte Gesamtgröße, Erstellungszeit und Ablaufzeit.

## Frontend-Aufgaben

### Upload Flow

1. Dateien auswählen.
2. Zufälligen 256-bit Schlüssel erzeugen.
3. Manifest mit Dateinamen, Größen, MIME-Types, lastModified und relativen Pfaden erzeugen.
4. Manifest nur verschlüsselt speichern.
5. Dateien als ZIP/Archiv streamen.
6. ZIP-Stream chunkweise verschlüsseln.
7. Chunks hochladen.
8. Upload abschließen.
9. Link mit Schlüssel im URL-Fragment anzeigen.

### Download Flow

1. `/d/<uploadId>#key=<key>` öffnen.
2. Schlüssel aus Fragment lesen.
3. Verschlüsselten Blob herunterladen.
4. Streamend entschlüsseln.
5. ZIP auf Festplatte speichern.
6. Optional später: ZIP automatisch extrahieren, wenn File System Access API verfügbar ist.

## Kryptografie

### Basis für Version 1

```text
Content Encryption: AES-256-GCM
Key Generation: crypto.getRandomValues / WebCrypto generateKey
Nonce: 96 bit pro Chunk
AAD: uploadId + chunkIndex + protocolVersion
```

Jeder Chunk wird separat authentifiziert. Dadurch können große Dateien verarbeitet werden, ohne alles im RAM zu halten.

### Wichtig

- Niemals Schlüssel an den Server senden.
- Niemals Original-Dateinamen unverschlüsselt senden.
- Niemals eigene Krypto-Primitive implementieren.
- Nonces dürfen sich pro Schlüssel nie wiederholen.
- Download muss Authentifizierungsfehler sichtbar abbrechen.

## Post-Quantum-Plan

Browser-WebCrypto bietet aktuell keine universelle produktive ML-KEM/Kyber-Dateiverschlüsselungs-API. Deshalb:

```text
v1:
- sichere symmetrische Verschlüsselung mit zufälligem Schlüssel
- Schlüssel im URL Fragment

v2:
- PQC-Abstraktion in frontend/src/crypto/pqc.ts ausbauen
- geprüfte WASM-Library für ML-KEM/Kyber evaluieren
- hybrides Key-Wrapping: classical + ML-KEM
```

Der Dateiinhalt bleibt symmetrisch verschlüsselt. PQC wird für Schlüsselvereinbarung oder Key-Wrapping verwendet, nicht für die direkte Verschlüsselung großer Dateien.

## Große Dateien / kein künstliches Limit

Das Ziel ist nicht, unendlich große Dateien technisch zu garantieren, sondern keine unnötigen App-Limits einzubauen.

Erforderlich:

- Web Streams API verwenden.
- ZIP nicht komplett im Speicher bauen.
- Verschlüsselung in Web Worker auslagern.
- Upload in Chunks, z. B. 8–64 MB.
- Resume-Funktion über bereits hochgeladene Chunk-Indizes.
- Backpressure berücksichtigen.
- Serverseitige Quotas und Ablaufzeiten einführen.

## TODOs im Skeleton

### Backend

- Persistenz robuster machen.
- Ablaufzeiten implementieren.
- Cleanup-Job für alte Uploads.
- Quotas pro IP/User.
- Rate Limiting.
- Optional Authentifizierung.
- Streaming-Download statt `Files.readAllBytes`.
- Chunk-Integrität und Reihenfolge prüfen.

### Frontend

- `zipStreamWriter.ts` durch echten ZIP-Streaming-Writer ersetzen.
- `decryptStream.ts` passend zu `encryptStream.ts` implementieren.
- Crypto Worker verdrahten.
- Upload-Fortschritt in Prozent.
- AbortController für Abbruch.
- Resume Upload.
- File System Access API für große Downloads.
- Optional StreamSaver-Fallback.

### Security

- Threat Model ausarbeiten.
- CSP Header setzen.
- HSTS aktivieren.
- Keine sensiblen Daten loggen.
- Upload-ID nicht erratbar machen.
- Serverzugriffe auf Download-IDs optional begrenzen.
- Schlüssel nie in Query-Parametern verwenden, nur im Fragment.

## Gradle Tasks

```bash
./gradlew :backend:run
./gradlew :frontend:npm_run_dev
./gradlew :frontend:npmBuild
./gradlew :frontend:copyFrontendToBackend
./gradlew buildAll
```

## Lokale Entwicklung

Frontend läuft auf Port `5173` und proxyt `/api` an Micronaut auf Port `8080`.

```bash
./gradlew :backend:run
./gradlew :frontend:npm_run_dev
```

## Produktionsbuild

```bash
./gradlew buildAll
java -jar backend/build/libs/backend-0.1.0-all.jar
```

Micronaut liefert danach das Frontend aus `classpath:public` aus.

## Empfohlene nächste AI-Prompts in der IDE

```text
Implementiere in zipStreamWriter.ts einen echten streaming ZIP Writer mit Web Streams API, ohne alle Dateien vollständig in den RAM zu laden.
```

```text
Implementiere decryptStream.ts passend zu encryptStream.ts. Jeder Chunk enthält nonce length, nonce und AES-GCM ciphertext. Nutze AAD uploadId:index:v1.
```

```text
Verschiebe Upload-Verschlüsselung in cryptoWorker.ts, sodass die Vue UI nicht blockiert. Ergänze Progress Events und AbortController.
```

```text
Ersetze im Micronaut DownloadController Files.readAllBytes durch echten Streaming-Download mit StreamedFile oder Netty Streaming.
```

```text
Implementiere einen Cleanup-Service in Micronaut, der abgelaufene Uploads periodisch löscht.
```
