# Crypto Notes

## Version 1

- AES-256-GCM via WebCrypto.
- One random root key per upload.
- One nonce per chunk.
- AAD contains upload ID, chunk index, and protocol version.

## Key Link

```text
/d/<uploadId>#key=<base64url-raw-key>
```

The fragment is not transmitted to the server.

## Post-Quantum

PQC should later be added as a key-wrapping / key-agreement layer. Large file contents remain symmetrically encrypted.
