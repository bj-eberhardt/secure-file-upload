import { createI18n } from 'vue-i18n'

export type SupportedLocale = 'en' | 'de'

const LOCALE_STORAGE_KEY = 'secure-file-upload:locale'

function normalizeLocale(locale: string | null | undefined): SupportedLocale {
  const primary = (locale ?? '').trim().toLowerCase().split(/[-_]/)[0]
  return primary === 'de' ? 'de' : 'en'
}

function detectInitialLocale(): SupportedLocale {
  const persisted = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (persisted) return normalizeLocale(persisted)
  const langs = Array.isArray(navigator.languages) && navigator.languages.length > 0 ? navigator.languages : [navigator.language]
  for (const lang of langs) {
    const normalized = normalizeLocale(lang)
    if (normalized === 'de') return 'de'
  }
  return 'en'
}

export const messages = {
  en: {
    app: {
      productName: 'secure-file-upload',
      language: 'Language'
    },
    common: {
      filesCount: '{count} file(s)',
      remove: 'Remove',
      resume: 'Resume',
      pause: 'Pause',
      paused: 'Paused',
      restart: 'Restart',
      startNewUpload: 'Start new upload',
      unknownError: 'Unknown error'
    },
    upload: {
      title: 'secure-file-upload',
      ready: 'Ready',
      initializing: 'Initializing upload...',
      checkingFiles: 'Checking files...',
      encryptingUploading: 'Encrypting and uploading chunks...',
      completing: 'Completing upload...',
      done: 'Done',
      reselectFiles: 'Please re-select the files (must be identical)',
      checkingFilesResume: 'Checking files (resume)...',
      resumeFingerprintMismatch: 'Selected files do not match the in-progress upload (fingerprint mismatch)',
      resuming: 'Resuming upload...',
      creatingManifest: 'Creating manifest...',
      readingFileStream: 'Reading file stream...',
      creatingZipStream: 'Creating ZIP stream...',
      finalizing: 'Upload finished, finalizing...',
      chunkAlreadyPresent: 'Chunk {chunk} already present (resume)',
      hint:
        'Choose one or more files (drag & drop works too). Multiple files are packaged as a ZIP; a single file is uploaded raw.',
      encryptedUpload: 'Upload encrypted',
      uploadMore: 'Upload more files securely',
      downloadLink: 'Download link',
      keyInFragment: 'The key is in the URL fragment (#key=...) and is not sent to the server.',
      copyLink: 'Copy download link',
      copied: 'Copied',
      resumeMismatchHint: 'Note: selected files do not match the resume upload'
    },
    download: {
      title: 'secure-file-upload',
      keyInFragment: 'The key is read from the URL fragment and is not sent to the server.',
      ready: 'Ready to download',
      loadingMeta: 'Loading metadata...',
      startingFresh: 'Restarting download...',
      downloading: 'Downloading encrypted file...',
      completed: 'Download completed',
      saveAborted: 'Save canceled',
      missingIdOrKey: 'Upload id or key is missing',
      files: 'Files',
      resumePossible: 'Resumable (from chunk {chunk})',
      downloadAndDecrypt: 'Download and decrypt'
    },
    downloadErrors: {
      resumeMissingChunkCount: 'Cannot resume download: chunkCount is missing',
      resumeTargetMismatch: 'Resume not possible: target file no longer matches (please restart)',
      resumeTargetCannotCheck: 'Resume not possible: target file cannot be verified (please restart)',
      noWritePermission: 'No permission to write the target file'
    },
    rateLimit: {
      waiting: 'Rate limit - waiting {seconds}s... (Chunk {chunk})',
      waitingBrief: 'Rate limit - waiting briefly... (Chunk {chunk})',
      failed: 'Rate limit while downloading (Chunk {chunk})'
    },
    downloadProgress: {
      chunkOf: 'Chunk {chunk} / {total}'
    },
    progress: {
      chunkUploaded: 'Chunk {chunk} uploaded',
      chunkOf: 'Chunk {chunk} / {total}'
    },
    errors: {
      BAD_REQUEST: 'Bad request',
      RATE_LIMITED: 'Too many requests. Please try again later.',
      UNKNOWN_UPLOAD: 'Unknown upload id',
      UPLOAD_EXPIRED: 'Upload expired',
      UPLOAD_CONFLICT: 'Upload conflict',
      NOT_FOUND: 'Not found'
    }
  },
  de: {
    app: {
      productName: 'secure-file-upload',
      language: 'Sprache'
    },
    common: {
      filesCount: '{count} Datei(en)',
      remove: 'Entfernen',
      resume: 'Fortsetzen',
      pause: 'Pausieren',
      paused: 'Pausiert',
      restart: 'Neu starten',
      startNewUpload: 'Neuen Upload starten',
      unknownError: 'Unbekannter Fehler'
    },
    upload: {
      title: 'secure-file-upload',
      ready: 'Bereit',
      initializing: 'Initialisiere Upload...',
      checkingFiles: 'Prüfe Dateien...',
      encryptingUploading: 'Verschlüssele und lade Chunks hoch...',
      completing: 'Schließe Upload ab...',
      done: 'Fertig',
      reselectFiles: 'Bitte Dateien erneut auswählen (müssen identisch sein)',
      checkingFilesResume: 'Prüfe Dateien (Fortsetzen)...',
      resumeFingerprintMismatch: 'Die ausgewählten Dateien passen nicht zum angefangenen Upload (Fingerprint mismatch)',
      resuming: 'Setze Upload fort...',
      creatingManifest: 'Erzeuge Manifest...',
      readingFileStream: 'Lese Datei-Stream...',
      creatingZipStream: 'Erzeuge ZIP-Stream...',
      finalizing: 'Upload fertig, finalisiere...',
      chunkAlreadyPresent: 'Chunk {chunk} bereits vorhanden (resume)',
      hint:
        'Wähle eine oder mehrere Dateien (Drag & Drop geht auch). Mehrere Dateien werden als ZIP verpackt, eine Datei wird roh hochgeladen.',
      encryptedUpload: 'Verschlüsselt hochladen',
      uploadMore: 'Weitere Dateien sicher hochladen',
      downloadLink: 'Download-Link',
      keyInFragment: 'Der Schlüssel steht im URL-Fragment (#key=...) und wird nicht an den Server gesendet.',
      copyLink: 'Download-Link kopieren',
      copied: 'Kopiert',
      resumeMismatchHint: 'Hinweis: ausgewählte Dateien passen nicht zum Resume-Upload'
    },
    download: {
      title: 'secure-file-upload',
      keyInFragment: 'Der Schlüssel wird aus dem URL-Fragment gelesen und nicht an den Server gesendet.',
      ready: 'Bereit zum Download',
      loadingMeta: 'Lade Metadaten...',
      startingFresh: 'Starte Download neu...',
      downloading: 'Lade verschlüsselte Datei herunter...',
      completed: 'Download abgeschlossen',
      saveAborted: 'Speichern abgebrochen',
      missingIdOrKey: 'Upload-ID oder Schlüssel fehlt',
      files: 'Dateien',
      resumePossible: 'Fortsetzen möglich (ab Chunk {chunk})',
      downloadAndDecrypt: 'Herunterladen und entschlüsseln'
    },
    downloadErrors: {
      resumeMissingChunkCount: 'Download kann nicht resümieren: chunkCount fehlt',
      resumeTargetMismatch: 'Fortsetzen ist nicht möglich: Zieldatei passt nicht mehr (bitte "Neu starten")',
      resumeTargetCannotCheck: 'Fortsetzen ist nicht möglich: Zieldatei kann nicht geprüft werden (bitte "Neu starten")',
      noWritePermission: 'Keine Berechtigung zum Schreiben der Zieldatei'
    },
    rateLimit: {
      waiting: 'Rate limit - warte {seconds}s... (Chunk {chunk})',
      waitingBrief: 'Rate limit - warte kurz... (Chunk {chunk})',
      failed: 'Rate limit beim Download (Chunk {chunk})'
    },
    downloadProgress: {
      chunkOf: 'Chunk {chunk} / {total}'
    },
    progress: {
      chunkUploaded: 'Chunk {chunk} hochgeladen',
      chunkOf: 'Chunk {chunk} / {total}'
    },
    errors: {
      BAD_REQUEST: 'Ungültige Anfrage',
      RATE_LIMITED: 'Zu viele Anfragen. Bitte später erneut versuchen.',
      UNKNOWN_UPLOAD: 'Unbekannte Upload-ID',
      UPLOAD_EXPIRED: 'Upload ist abgelaufen',
      UPLOAD_CONFLICT: 'Upload-Konflikt',
      NOT_FOUND: 'Nicht gefunden'
    }
  }
} as const

export const i18n = createI18n({
  legacy: false,
  locale: detectInitialLocale(),
  fallbackLocale: 'en',
  messages
})

export function setLocale(locale: SupportedLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}

export function t(key: string, params?: Record<string, unknown>): string {
  if (params) return i18n.global.t(key, params) as unknown as string
  return i18n.global.t(key) as unknown as string
}
