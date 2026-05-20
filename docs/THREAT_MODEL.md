# Threat Model

## Schutzziele

- Server darf Dateiinhalte nicht lesen können.
- Server darf Dateinamen und Dateimetadaten nicht kennen.
- Netzwerkangreifer dürfen Inhalte nicht lesen oder verändern können.
- Falscher Schlüssel muss zu einem Authentifizierungsfehler führen.

## Nicht gelöst durch dieses Skeleton

- Kompromittierter Browser.
- Bösartige Browser-Erweiterungen.
- Manipuliertes Frontend JavaScript vom Server.
- Traffic-Analyse anhand Uploadgröße und Zeitpunkt.
- Schutz vor Linkweitergabe inklusive Fragment-Key.

## Wichtige Maßnahmen

- HTTPS/HSTS erzwingen.
- Subresource Integrity oder reproduzierbare Frontend-Builds prüfen.
- CSP restriktiv setzen.
- Keine externen Skripte laden.
- Secrets nie in Logs.
- Quotas und Rate Limits.
