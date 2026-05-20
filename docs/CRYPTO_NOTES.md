# Crypto Notes

## Version 1

- AES-256-GCM über WebCrypto.
- Ein zufälliger Root-Key pro Upload.
- Ein Nonce pro Chunk.
- AAD enthält Upload-ID, Chunk-Index und Protokollversion.

## Key Link

```text
/d/<uploadId>#key=<base64url-raw-key>
```

Das Fragment wird nicht an den Server übertragen.

## Post-Quantum

PQC sollte später als Key-Wrapping-/Key-Agreement-Layer ergänzt werden. Große Datei-Inhalte werden weiterhin symmetrisch verschlüsselt.
