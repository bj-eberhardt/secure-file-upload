# Secure Upload Skeleton

Micronaut + Kotlin Backend mit Vue/Vite/TypeScript Frontend für clientseitig verschlüsselte Datei-Uploads.

## Start lokal

```bash
./gradlew :backend:run
./gradlew :frontend:npm_run_dev
```

Frontend: http://localhost:5173  
Backend: http://localhost:8080

## Komplett bauen

```bash
./gradlew buildAll
```

Das Frontend wird gebaut und nach `backend/src/main/resources/public` kopiert, damit Micronaut es statisch ausliefert.

## Bauplan

Siehe `docs/IMPLEMENTATION_PLAN.md`.
