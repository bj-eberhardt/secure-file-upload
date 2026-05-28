# Threat Model

## Security goals

- The server must not be able to read file contents.
- The server must not learn filenames or file metadata.
- Network attackers must not be able to read or modify contents.
- A wrong key must result in an authentication failure.

## Not solved by this skeleton

- A compromised browser.
- Malicious browser extensions.
- Server-delivered frontend JavaScript that was tampered with.
- Traffic analysis based on upload size and timing.
- Protection against link sharing including the fragment key.

## Important measures

- Enforce HTTPS/HSTS.
- Consider Subresource Integrity or reproducible frontend builds.
- Set a restrictive CSP.
- Do not load external scripts.
- Never log secrets.
- Use quotas and rate limits.
