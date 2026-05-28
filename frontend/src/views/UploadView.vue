<script setup lang="ts">
import { computed, ref } from 'vue'
import { initUpload, completeUpload } from '../upload/chunkUploader'
import { createShareKey, exportShareKey, importShareKey } from '../crypto/keyDerivation'
import { computeFileFingerprint, type FileFingerprint } from '../upload/fileFingerprint'
import NoticeBar from '../components/NoticeBar.vue'
import { formatBytes } from '../utils/formatBytes'
import { apiBase } from '../api/apiConfig'
import { useI18n } from 'vue-i18n'

type UiState = 'idle' | 'running' | 'success' | 'error' | 'paused'

const { t } = useI18n()

const fileInput = ref<HTMLInputElement | null>(null)
const files = ref<File[]>([])
const uiState = ref<UiState>('idle')
const uiMessage = ref(t('upload.ready'))
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
  uiMessage.value = t('upload.ready')
  progressPercent.value = null
  progressDetail.value = ''
  copied.value = false
  shareLink.value = ''
  clearResumeInfo()
  setTimeout(() => fileInput.value?.click(), 0)
}

function loadResumeInfo() {
  try {
    const raw = sessionStorage.getItem('secure-file-upload:resume')
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
  sessionStorage.setItem('secure-file-upload:resume', JSON.stringify(info))
  loadResumeInfo()
}

function clearResumeInfo() {
  sessionStorage.removeItem('secure-file-upload:resume')
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
            ? t('upload.resumeMismatchHint')
            : ''
      })
      .catch(() => {
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
  }
}

async function runWorkerUpload(init: { uploadId: string; chunkSize: number; protocolVersion: string }, keyRaw: ArrayBuffer) {
  return new Promise<any>((resolve, reject) => {
    if (!worker) worker = new Worker(new URL('../workers/cryptoWorker.ts', import.meta.url), { type: 'module' })
    const w = worker
    const onMessage = (event: MessageEvent<any>) => {
      const data = event.data
      if (data?.type === 'progress') {
        if (typeof data.messageKey === 'string') uiMessage.value = t(data.messageKey, data.messageParams ?? undefined)
        if (typeof data.percent === 'number') progressPercent.value = data.percent
        if (typeof data.detail === 'string') progressDetail.value = data.detail
      }
      if (data?.type === 'result') {
        w.removeEventListener('message', onMessage)
        resolve(data.result)
      }
      if (data?.type === 'error') {
        w.removeEventListener('message', onMessage)
        const err = new Error(typeof data.message === 'string' ? data.message : t('common.unknownError')) as any
        if (typeof data.errorKey === 'string') err.errorKey = data.errorKey
        reject(err)
      }
    }
    w.addEventListener('message', onMessage)
    w.postMessage({ type: 'upload', apiBase, init: { ...init }, files: Array.from(files.value), keyRaw }, [keyRaw])
  })
}

async function upload() {
  busy.value = true
  uiState.value = 'running'
  uiMessage.value = t('upload.initializing')
  progressPercent.value = null
  progressDetail.value = ''
  shareLink.value = ''
  try {
    const key = await createShareKey()
    const init = await initUpload()
    const exportedKey = await exportShareKey(key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)

    uiMessage.value = t('upload.checkingFiles')
    const fingerprint = await computeFileFingerprint(files.value)
    saveResumeInfo({
      uploadId: init.uploadId,
      key: exportedKey,
      chunkSize: init.chunkSize,
      protocolVersion: init.protocolVersion,
      fingerprint
    })

    uiMessage.value = t('upload.encryptingUploading')
    const result = await runWorkerUpload(init, keyRaw)

    uiMessage.value = t('upload.completing')
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${exportedKey}`
    uiState.value = 'success'
    uiMessage.value = t('upload.done')
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const errAny = error as any
    const errorKey = typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined
    const message = errorKey ? t(`errors.${errorKey}`) : error instanceof Error ? error.message : t('common.unknownError')
    uiState.value = 'error'
    uiMessage.value = message
  } finally {
    busy.value = false
  }
}

async function resumeUpload() {
  if (!resumeInfo.value) return
  if (files.value.length === 0) {
    uiState.value = 'error'
    uiMessage.value = t('upload.reselectFiles')
    return
  }

  busy.value = true
  uiState.value = 'running'
  shareLink.value = ''
  progressPercent.value = null
  progressDetail.value = ''
  try {
    const info = resumeInfo.value
    uiMessage.value = t('upload.checkingFilesResume')
    const currentFingerprint = await computeFileFingerprint(files.value)
    if (currentFingerprint.valueB64u !== info.fingerprint.valueB64u) {
      throw new Error(t('upload.resumeFingerprintMismatch'))
    }
    const key = await importShareKey(info.key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)
    const init = { uploadId: info.uploadId, chunkSize: info.chunkSize, protocolVersion: info.protocolVersion }

    uiMessage.value = t('upload.resuming')
    const result = await runWorkerUpload(init, keyRaw)

    uiMessage.value = t('upload.completing')
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${info.key}`
    uiState.value = 'success'
    uiMessage.value = t('upload.done')
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const errAny = error as any
    const errorKey = typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined
    const message = errorKey ? t(`errors.${errorKey}`) : error instanceof Error ? error.message : t('common.unknownError')
    uiState.value = 'error'
    uiMessage.value = message
  } finally {
    busy.value = false
  }
}

function cancel() {
  if (!worker) return
  try {
    worker.postMessage({ type: 'abort' })
    uiState.value = 'paused'
    uiMessage.value = t('common.paused')
    progressDetail.value = ''
  } catch {
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
    <h1>{{ t('upload.title') }}</h1>
    <p v-if="showUploadForm" class="hint">
      {{ t('upload.hint') }}
    </p>

    <aside v-if="showUploadForm" class="promo" data-testid="upload:promo">
      <img class="promo-art" src="/privacy-browser-encryption.svg" alt="" width="120" height="96" />
      <div class="promo-text">
        <div class="promo-title">{{ t('upload.promoTitle') }}</div>
        <div class="promo-body">{{ t('upload.promoBody') }}</div>
      </div>
    </aside>

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
          <strong>{{ t('common.filesCount', { count: files.length }) }}</strong>
          <span class="muted right">{{ formatBytes(totalBytes) }}</span>
        </div>
        <hr class="sep tight" />
        <ul>
          <li v-for="(file, idx) in files" :key="file.name + ':' + file.size + ':' + file.lastModified">
            <span class="name" data-testid="upload:file-row">{{ file.name }}</span>
            <span class="muted right">{{ formatBytes(file.size) }}</span>
            <button class="secondary" data-testid="upload:file-remove" :disabled="isInProgress" @click="removeFile(idx)">
              {{ t('common.remove') }}
            </button>
          </li>
        </ul>
      </div>

      <hr class="sep" />

      <div class="actions">
        <button v-if="uiState === 'idle'" data-testid="upload:btn-upload" :disabled="files.length === 0" @click="upload">
          {{ t('upload.encryptedUpload') }}
        </button>
        <button
          v-if="resumeInfo"
          class="secondary"
          data-testid="upload:btn-resume"
          :disabled="isInProgress"
          @click="resumeUpload"
        >
          {{ t('common.resume') }}
        </button>
        <button v-if="isInProgress" data-testid="upload:btn-pause" @click="cancel">{{ t('common.pause') }}</button>
        <button
          v-if="isInProgress || isDone || isPaused || uiState === 'error'"
          class="secondary"
          data-testid="upload:btn-new"
          :disabled="isInProgress"
          @click="startNewUpload"
        >
          {{ t('common.startNewUpload') }}
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
      <h2>{{ t('upload.downloadLink') }}</h2>
      <p class="muted">{{ t('upload.downloadLinkIntro') }}</p>
      <aside class="keyinfo" data-testid="upload:keyinfo">
        <img class="keyinfo-art" src="/privacy-browser-encryption.svg" alt="" width="120" height="96" />
        <div class="keyinfo-text">
          <div class="keyinfo-title">{{ t('upload.keyInfoTitle') }}</div>
          <div class="keyinfo-body">
            <div>{{ t('upload.keyInFragment') }}</div>
            <div class="keyinfo-body-spacer">{{ t('upload.keyInfoBody') }}</div>
          </div>
        </div>
      </aside>
      <div class="sharebox">
        <input class="shareinput" data-testid="upload:share-link" readonly :value="shareLink" />
        <button class="secondary" data-testid="upload:btn-copy-link" @click="copyShareLink">
          {{ copied ? t('upload.copied') : t('upload.copyLink') }}
        </button>
      </div>
      <div class="actions">
        <button data-testid="upload:btn-more-files" @click="startNewUpload">{{ t('upload.uploadMore') }}</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.promo {
  margin: 14px 0 10px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border, #dde4f0);
  background: linear-gradient(135deg, rgba(51, 72, 255, 0.10), rgba(238, 242, 255, 0.85));
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: center;
}
.promo-art {
  display: block;
  filter: drop-shadow(0 14px 28px rgba(24, 33, 54, 0.12));
}
.promo-title {
  font-weight: 700;
  letter-spacing: 0.2px;
  margin-bottom: 3px;
}
.promo-body {
  color: var(--muted, #5c6b84);
  line-height: 1.35;
  font-size: 13px;
}
.keyinfo {
  margin: 10px 0 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--border, #dde4f0);
  background: rgba(238, 242, 255, 0.55);
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: center;
}
.keyinfo-art {
  display: block;
}
.keyinfo-title {
  font-weight: 700;
  letter-spacing: 0.2px;
  margin-bottom: 3px;
}
.keyinfo-body {
  color: var(--muted, #5c6b84);
  line-height: 1.35;
  font-size: 13px;
}
.keyinfo-body-spacer {
  margin-top: 6px;
}
@media (max-width: 540px) {
  .promo {
    grid-template-columns: 1fr;
  }
  .promo-art {
    width: 112px;
    height: auto;
  }
  .keyinfo {
    grid-template-columns: 1fr;
  }
  .keyinfo-art {
    width: 112px;
    height: auto;
  }
}
</style>
