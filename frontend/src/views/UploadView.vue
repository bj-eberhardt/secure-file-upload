<script setup lang="ts">
import { computed, ref } from 'vue'
import { initUpload, completeUpload } from '../upload/chunkUploader'
import { createShareKey, exportShareKey, importShareKey } from '../crypto/keyDerivation'
import { computeFileFingerprint, type FileFingerprint } from '../upload/fileFingerprint'
import NoticeBar from '../components/NoticeBar.vue'
import InfoCallout from '../components/InfoCallout.vue'
import { formatBytes } from '../utils/formatBytes'
import { apiBase } from '../api/apiConfig'
import { useI18n } from 'vue-i18n'

type UiState = 'idle' | 'running' | 'success' | 'error' | 'paused'

const { t } = useI18n()

const fileInput = ref<HTMLInputElement | null>(null)
const files = ref<File[]>([])
const uiState = ref<UiState>('idle')
const uiMessageKey = ref('upload.ready')
const uiMessageParams = ref<Record<string, unknown> | null>(null)
const uiMessageText = ref<string | null>(null)
const uiMessage = computed(() => {
  if (uiMessageText.value) return uiMessageText.value
  return uiMessageParams.value ? t(uiMessageKey.value, uiMessageParams.value) : t(uiMessageKey.value)
})
const shareLink = ref('')
const busy = ref(false)
const copied = ref(false)
const progressPercent = ref<number | null>(null)
const progressDetailKey = ref<string | null>(null)
const progressDetailText = ref('')
const progressDetail = computed(() => (progressDetailKey.value ? t(progressDetailKey.value) : progressDetailText.value))
const isDragging = ref(false)

let worker: Worker | null = null
let cancelRequested = false

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
const uploadDescribedBy = computed(() => (showUploadForm.value ? 'upload-hint upload-promo' : undefined))
const progressDetailVisible = computed(() => progressDetail.value.trim().length > 0)

function setFiles(selected: File[]) {
  files.value = selected
  copied.value = false
  shareLink.value = ''
  progressDetailKey.value = null
  progressDetailText.value = ''
}

function removeFile(index: number) {
  files.value = files.value.filter((_, i) => i !== index)
  progressDetailKey.value = null
  progressDetailText.value = ''
}

function startNewUpload() {
  setFiles([])
  cancelRequested = false
  uiState.value = 'idle'
  uiMessageKey.value = 'upload.ready'
  uiMessageParams.value = null
  uiMessageText.value = null
  progressPercent.value = null
  progressDetailKey.value = null
  progressDetailText.value = ''
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
        progressDetailKey.value = fp.valueB64u !== resumeInfo.value.fingerprint.valueB64u ? 'upload.resumeMismatchHint' : null
        progressDetailText.value = ''
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
        if (typeof data.messageKey === 'string') {
          uiMessageKey.value = data.messageKey
          uiMessageParams.value = (data.messageParams ?? null) as Record<string, unknown> | null
          uiMessageText.value = null
        }
        if (typeof data.percent === 'number') progressPercent.value = data.percent
        if (typeof data.detail === 'string') {
          progressDetailKey.value = null
          progressDetailText.value = data.detail
        }
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

function resetWorker() {
  try {
    worker?.terminate()
  } catch {
  }
  worker = null
}

async function upload() {
  busy.value = true
  uiState.value = 'running'
  cancelRequested = false
  uiMessageKey.value = 'upload.initializing'
  uiMessageParams.value = null
  uiMessageText.value = null
  progressPercent.value = null
  progressDetailKey.value = null
  progressDetailText.value = ''
  shareLink.value = ''
  try {
    const key = await createShareKey()
    const init = await initUpload()
    const exportedKey = await exportShareKey(key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)

    uiMessageKey.value = 'upload.checkingFiles'
    const fingerprint = await computeFileFingerprint(files.value)
    saveResumeInfo({
      uploadId: init.uploadId,
      key: exportedKey,
      chunkSize: init.chunkSize,
      protocolVersion: init.protocolVersion,
      fingerprint
    })

    uiMessageKey.value = 'upload.encryptingUploading'
    const result = await runWorkerUpload(init, keyRaw)

    if (cancelRequested) return
    uiMessageKey.value = 'upload.completing'
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${exportedKey}`
    uiState.value = 'success'
    uiMessageKey.value = 'upload.done'
    uiMessageParams.value = null
    uiMessageText.value = null
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const errAny = error as any
    const errorKey = typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined
    if (errorKey === 'ABORTED') {
      uiState.value = 'paused'
      uiMessageKey.value = 'common.paused'
      uiMessageText.value = null
      return
    }
    uiState.value = 'error'
    uiMessageKey.value = errorKey ? `errors.${errorKey}` : 'common.unknownError'
    uiMessageText.value = errorKey ? null : error instanceof Error ? error.message : null
  } finally {
    busy.value = false
  }
}

async function resumeUpload() {
  if (!resumeInfo.value) return
  if (files.value.length === 0) {
    uiState.value = 'error'
    uiMessageKey.value = 'upload.reselectFiles'
    uiMessageText.value = null
    return
  }

  busy.value = true
  uiState.value = 'running'
  cancelRequested = false
  shareLink.value = ''
  progressPercent.value = null
  progressDetailKey.value = null
  progressDetailText.value = ''
  try {
    const info = resumeInfo.value
    uiMessageKey.value = 'upload.checkingFilesResume'
    uiMessageText.value = null
    const currentFingerprint = await computeFileFingerprint(files.value)
    if (currentFingerprint.valueB64u !== info.fingerprint.valueB64u) {
      throw new Error(t('upload.resumeFingerprintMismatch'))
    }
    const key = await importShareKey(info.key)
    const keyRaw = await crypto.subtle.exportKey('raw', key)
    const init = { uploadId: info.uploadId, chunkSize: info.chunkSize, protocolVersion: info.protocolVersion }

    uiMessageKey.value = 'upload.resuming'
    const result = await runWorkerUpload(init, keyRaw)

    if (cancelRequested) return
    uiMessageKey.value = 'upload.completing'
    const completed = await completeUpload(init.uploadId, result)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${info.key}`
    uiState.value = 'success'
    uiMessageKey.value = 'upload.done'
    uiMessageParams.value = null
    uiMessageText.value = null
    clearResumeInfo()
  } catch (error) {
    console.error(error)
    const errAny = error as any
    const errorKey = typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined
    if (errorKey === 'ABORTED') {
      uiState.value = 'paused'
      uiMessageKey.value = 'common.paused'
      uiMessageText.value = null
      return
    }
    uiState.value = 'error'
    uiMessageKey.value = errorKey ? `errors.${errorKey}` : 'common.unknownError'
    uiMessageText.value = errorKey ? null : error instanceof Error ? error.message : null
  } finally {
    busy.value = false
  }
}

function cancel() {
  if (!worker) return
  try {
    cancelRequested = true
    worker.postMessage({ type: 'abort' })
    resetWorker()
    uiState.value = 'paused'
    uiMessageKey.value = 'common.paused'
    uiMessageText.value = null
    progressDetailKey.value = null
    progressDetailText.value = ''
  } catch {
  }
}
</script>

<template>
  <section
    class="card"
    data-testid="upload-page"
    :class="{ dragging: isDragging }"
    role="region"
    :aria-label="t('upload.title')"
    :aria-describedby="uploadDescribedBy"
    tabindex="0"
    @drop="onDrop"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
  >
    <h1>{{ t('upload.title') }}</h1>
    <p v-if="showUploadForm" id="upload-hint" class="hint">
      {{ t('upload.hint') }}
    </p>

    <InfoCallout
      v-if="showUploadForm"
      id="upload-promo"
      test-id="upload:promo"
      tone="brand"
      icon-src="/privacy-browser-encryption.svg"
      :title="t('upload.promoTitle')"
    >
      {{ t('upload.promoBody') }}
    </InfoCallout>

    <NoticeBar
      test-id="upload:notice"
      :title="uiMessage"
      :right="files.length > 0 && showUploadForm ? formatBytes(totalBytes) : undefined"
      :variant="noticeVariant"
    />

    <div v-if="showUploadForm">
      <hr class="sep" />

      <label class="sr-only" for="upload-file-input">{{ t('upload.fileLabel') }}</label>
      <input
        ref="fileInput"
        id="upload-file-input"
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
      <div v-if="progressDetailVisible" class="meta-row" data-testid="upload:progress-detail">
        <span class="muted small">{{ progressDetail }}</span>
        <span v-if="progressPercent !== null" class="muted small right">{{ progressLabel }}</span>
      </div>
    </div>

    <div v-if="shareLink">
      <hr class="sep" />
      <h2>{{ t('upload.downloadLink') }}</h2>
      <p class="muted">{{ t('upload.downloadLinkIntro') }}</p>
      <InfoCallout test-id="upload:keyinfo" tone="soft" icon-src="/privacy-browser-encryption.svg" :title="t('upload.keyInfoTitle')">
        <div>{{ t('upload.keyInFragment') }}</div>
        <div class="keyinfo-body-spacer">{{ t('upload.keyInfoBody') }}</div>
      </InfoCallout>
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
.keyinfo-body-spacer {
  margin-top: 6px;
}
</style>
