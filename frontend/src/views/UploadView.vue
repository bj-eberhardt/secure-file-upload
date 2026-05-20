<script setup lang="ts">
import { computed, ref } from 'vue'
import { initUpload, completeUpload } from '../upload/chunkUploader'
import { createShareKey, exportShareKey, importShareKey } from '../crypto/keyDerivation'
import { computeFileFingerprint, type FileFingerprint } from '../upload/fileFingerprint'
import NoticeBar from '../components/NoticeBar.vue'
import { formatBytes } from '../utils/formatBytes'
import { apiBase } from '../api/apiConfig'

type UiState = 'idle' | 'running' | 'success' | 'error' | 'paused'

const fileInput = ref<HTMLInputElement | null>(null)
const files = ref<File[]>([])
const uiState = ref<UiState>('idle')
const uiMessage = ref('Bereit')
const shareLink = ref('')
const busy = ref(false)
const copied = ref(false)
const progressPercent = ref<number | null>(null)
const progressDetail = ref('')
const isDragging = ref(false)

let worker: Worker | null = null

const resumeInfo = ref<{
  uploadId: string
  key: string
  chunkSize: number
  protocolVersion: string
  fingerprint: FileFingerprint
} | null>(null)

const totalBytes = computed(() => files.value.reduce((sum, f) => sum + f.size, 0))
const showUploadForm = computed(() => !shareLink.value)
const progressLabel = computed(() => (progressPercent.value === null ? '' : `${progressPercent.value.toFixed(0)}%`))
const isDone = computed(() => uiState.value === 'success')
const isInProgress = computed(() => busy.value)
const isPaused = computed(() => uiState.value === 'paused')
const noticeVariant = computed(() => (uiState.value === 'success' ? 'success' : uiState.value === 'error' ? 'error' : 'default'))

function setFiles(selected: File[]) {
  files.value = selected
  copied.value = false
  shareLink.value = ''
  progressDetail.value = ''
}

function removeFile(index: number) {
  files.value = files.value.filter((_, i) => i !== index)
  progressDetail.value = ''
}

function startNewUpload() {
  setFiles([])
  uiState.value = 'idle'
  uiMessage.value = 'Bereit'
  progressPercent.value = null
  progressDetail.value = ''
  copied.value = false
  shareLink.value = ''
  clearResumeInfo()
  setTimeout(() => fileInput.value?.click(), 0)
}

function loadResumeInfo() {
  try {
    const raw = sessionStorage.getItem('secure-upload:resume')
    resumeInfo.value = raw ? JSON.parse(raw) : null
  } catch {
    resumeInfo.value = null
  }
}

function saveResumeInfo(info: {
  uploadId: string
  key: string
  chunkSize: number
  protocolVersion: string
  fingerprint: FileFingerprint
}) {
  sessionStorage.setItem('secure-upload:resume', JSON.stringify(info))
  loadResumeInfo()
}

function clearResumeInfo() {
  sessionStorage.removeItem('secure-upload:resume')
  loadResumeInfo()
}

loadResumeInfo()

function onFilesChanged(event: Event) {
  const input = event.target as HTMLInputElement
  setFiles(Array.from(input.files ?? []))
  if (resumeInfo.value && files.value.length > 0) {
    computeFileFingerprint(files.value)
      .then((fp) => {
        if (!resumeInfo.value) return
        progressDetail.value =
          fp.valueB64u !== resumeInfo.value.fingerprint.valueB64u
            ? 'Hinweis: ausgewählte Dateien passen nicht zum Resume-Upload'
            : ''
      })
      .catch(() => {
        // ignore
      })
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false
  const dropped = Array.from(event.dataTransfer?.files ?? [])
  if (dropped.length > 0) setFiles(dropped)
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function onDragLeave() {
  isDragging.value = false
}

async function copyShareLink() {
  if (!shareLink.value) return
  try {
    await navigator.clipboard.writeText(shareLink.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1200)
  } catch {
    // ignore
  }
}

async function runWorkerUpload(init: { uploadId: string; chunkSize: number; protocolVersion: string }, keyRaw: ArrayBuffer) {
  return new Promise<any>((resolve, reject) => {
    if (!worker) worker = new Worker(new URL('../workers/cryptoWorker.ts', import.meta.url), { type: 'module' })
    const w = worker
    const onMessage = (event: MessageEvent<any>) => {
      const data = event.data
      if (data?.type === 'progress') {
        uiMessage.value = data.message
        if (typeof data.percent === 'number') progressPercent.value = data.percent
        if (typeof data.detail === 'string') progressDetail.value = data.detail
      }
      if (data?.type === 'result') {
        w.removeEventListener('message', onMessage)
        resolve(data.result)
      }
      if (data?.type === 'error') {
        w.removeEventListener('message', onMessage)
        reject(new Error(data.message))
      }
    }
    w.addEventListener('message', onMessage)
    w.postMessage({ type: 'upload', apiBase, init: { ...init }, files: Array.from(files.value), keyRaw }, [keyRaw])
  })
}

async function upload() {
  busy.value = true
  uiState.value = 'running'
  uiMessage.value = 'Initialisiere Upload...'
  progressPercent.value = null
  progressDetail.value = ''
  shareLink.value = ''
  try {
    const key = await createShareKey()
    const init = await initUpload()
    const exportedKey = await exportShareKey(key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)

    uiMessage.value = 'Prüfe Dateien...'
    const fingerprint = await computeFileFingerprint(files.value)
    saveResumeInfo({
      uploadId: init.uploadId,
      key: exportedKey,
      chunkSize: init.chunkSize,
      protocolVersion: init.protocolVersion,
      fingerprint
    })

    uiMessage.value = 'Verschlüssele und lade Chunks hoch...'
    const result = await runWorkerUpload(init, keyRaw)

    uiMessage.value = 'Schließe Upload ab...'
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${exportedKey}`
    uiState.value = 'success'
    uiMessage.value = 'Fertig'
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : 'Unbekannter Fehler'
    uiState.value = message === 'Abgebrochen' ? 'paused' : 'error'
    uiMessage.value = message === 'Abgebrochen' ? 'Abgebrochen' : message
  } finally {
    busy.value = false
  }
}

async function resumeUpload() {
  if (!resumeInfo.value) return
  if (files.value.length === 0) {
    uiState.value = 'error'
    uiMessage.value = 'Bitte Dateien erneut auswählen (müssen identisch sein)'
    return
  }

  busy.value = true
  uiState.value = 'running'
  shareLink.value = ''
  progressPercent.value = null
  progressDetail.value = ''
  try {
    const info = resumeInfo.value
    uiMessage.value = 'Prüfe Dateien (Fortsetzen)...'
    const currentFingerprint = await computeFileFingerprint(files.value)
    if (currentFingerprint.valueB64u !== info.fingerprint.valueB64u) {
      throw new Error('Die ausgewählten Dateien passen nicht zum angefangenen Upload (Fingerprint mismatch)')
    }
    const key = await importShareKey(info.key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)
    const init = { uploadId: info.uploadId, chunkSize: info.chunkSize, protocolVersion: info.protocolVersion }

    uiMessage.value = 'Setze Upload fort...'
    const result = await runWorkerUpload(init, keyRaw)

    uiMessage.value = 'Schließe Upload ab...'
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${info.key}`
    uiState.value = 'success'
    uiMessage.value = 'Fertig'
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : 'Unbekannter Fehler'
    uiState.value = message === 'Abgebrochen' ? 'paused' : 'error'
    uiMessage.value = message === 'Abgebrochen' ? 'Abgebrochen' : message
  } finally {
    busy.value = false
  }
}

function cancel() {
  if (!worker) return
  try {
    worker.postMessage({ type: 'abort' })
    uiState.value = 'paused'
    uiMessage.value = 'Pausiert'
    progressDetail.value = ''
  } catch {
    // ignore
  }
}
</script>

<template>
  <section
    class="card"
    data-testid="upload-page"
    :class="{ dragging: isDragging }"
    @drop="onDrop"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
  >
    <h1>Secure Upload</h1>
    <p v-if="showUploadForm" class="hint">
      Wähle eine oder mehrere Dateien (Drag &amp; Drop geht auch). Mehrere Dateien werden als ZIP verpackt, eine Datei wird roh
      hochgeladen.
    </p>

    <NoticeBar
      test-id="upload:notice"
      :title="uiMessage"
      :right="files.length > 0 && showUploadForm ? formatBytes(totalBytes) : undefined"
      :variant="noticeVariant"
    />

    <div v-if="showUploadForm">
      <hr class="sep" />

      <input
        ref="fileInput"
        data-testid="upload:file-input"
        type="file"
        multiple
        :disabled="isInProgress"
        @change="onFilesChanged"
      />

      <div v-if="files.length > 0" class="filelist" data-testid="upload:file-list">
        <div class="filelist-header">
          <strong>{{ files.length }} Datei(en)</strong>
          <span class="muted right">{{ formatBytes(totalBytes) }}</span>
        </div>
        <hr class="sep tight" />
        <ul>
          <li v-for="(file, idx) in files" :key="file.name + ':' + file.size + ':' + file.lastModified">
            <span class="name" data-testid="upload:file-row">{{ file.name }}</span>
            <span class="muted right">{{ formatBytes(file.size) }}</span>
            <button class="secondary" data-testid="upload:file-remove" :disabled="isInProgress" @click="removeFile(idx)">
              Entfernen
            </button>
          </li>
        </ul>
      </div>

      <hr class="sep" />

      <div class="actions">
        <button v-if="uiState === 'idle'" data-testid="upload:btn-upload" :disabled="files.length === 0" @click="upload">
          Verschlüsselt hochladen
        </button>
        <button
          v-if="resumeInfo"
          class="secondary"
          data-testid="upload:btn-resume"
          :disabled="isInProgress"
          @click="resumeUpload"
        >
          Fortsetzen
        </button>
        <button v-if="isInProgress" data-testid="upload:btn-pause" @click="cancel">Pausieren</button>
        <button
          v-if="isInProgress || isDone || isPaused || uiState === 'error'"
          class="secondary"
          data-testid="upload:btn-new"
          :disabled="isInProgress"
          @click="startNewUpload"
        >
          Neuen Upload starten
        </button>
      </div>

      <div v-if="progressPercent !== null" class="progress-wrap" data-testid="upload:progress">
        <progress class="bar" :value="progressPercent" max="100" />
      </div>
      <div v-if="progressDetail" class="meta-row" data-testid="upload:progress-detail">
        <span class="muted small">{{ progressDetail }}</span>
        <span v-if="progressPercent !== null" class="muted small right">{{ progressLabel }}</span>
      </div>
    </div>

    <div v-if="shareLink">
      <hr class="sep" />
      <h2>Download-Link</h2>
      <p class="muted">Der Schlüssel steht im URL-Fragment (#key=...) und wird nicht an den Server gesendet.</p>
      <div class="sharebox">
        <input class="shareinput" data-testid="upload:share-link" readonly :value="shareLink" />
        <button class="secondary" data-testid="upload:btn-copy-link" @click="copyShareLink">
          {{ copied ? 'Kopiert' : 'Download-Link kopieren' }}
        </button>
      </div>
      <div class="actions">
        <button data-testid="upload:btn-more-files" @click="startNewUpload">Weitere Dateien sicher hochladen</button>
      </div>
    </div>
  </section>
</template>
