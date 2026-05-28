<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { importShareKey } from '../crypto/keyDerivation'
import { downloadAndDecryptWithManifest } from '../download/downloadAndDecrypt'
import type { PlainManifest } from '../crypto/manifest'
import { decryptManifestV1 } from '../crypto/manifest'
import { deleteDownloadResume, getDownloadResume } from '../download/resumeStore'
import NoticeBar from '../components/NoticeBar.vue'
import { formatBytes } from '../utils/formatBytes'
import { apiBase } from '../api/apiConfig'
import { API_PREFIX } from '../api/apiConfig'

type UiState = 'idle' | 'running' | 'success' | 'error'
const uiState = ref<UiState>('idle')
const { t } = useI18n()
const uiMessageKey = ref('download.ready')
const uiMessageText = ref<string | null>(null)
const uiMessage = computed(() => uiMessageText.value ?? t(uiMessageKey.value))
const busy = ref(false)
const manifest = ref<PlainManifest | null>(null)
const canResume = ref(false)
const resumeNextChunkIndex = ref<number | null>(null)
const downloadKey = ref<CryptoKey | null>(null)
const uploadIdRef = ref<string | null>(null)
const progressPercent = ref<number | null>(null)
const progressDetail = ref('')

const resumeDetail = computed(() =>
  resumeNextChunkIndex.value && resumeNextChunkIndex.value > 0 ? t('download.resumePossible', { chunk: resumeNextChunkIndex.value }) : ''
)

const noticeVariant = computed(() => (uiState.value === 'success' ? 'success' : uiState.value === 'error' ? 'error' : 'default'))

function goToUploadHome() {
  window.location.assign('/')
}

function displayName(file: PlainManifest['files'][number]): string {
  const rp = file.relativePath
  return rp && rp.length > 0 ? rp : file.name
}

async function loadManifest(uploadId: string, key: CryptoKey): Promise<void> {
  const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${uploadId}/manifest`)
  if (!response.ok) return
  const text = await response.text()
  try {
    const parsed = JSON.parse(text) as any
    if (parsed?.version === 'enc-manifest-v1') {
      manifest.value = await decryptManifestV1(uploadId, key, parsed)
    }
  } catch {
    // ignore malformed manifest
  }
}

async function refreshResumeState(uploadId: string) {
  try {
    const resume = await getDownloadResume(uploadId)
    canResume.value = !!resume && resume.nextChunkIndex > 0
    resumeNextChunkIndex.value = resume ? resume.nextChunkIndex : null
  } catch {
    canResume.value = false
    resumeNextChunkIndex.value = null
  }
}

async function preload() {
  const uploadId = window.location.pathname.split('/').pop() ?? null
  const keyHash = new URLSearchParams(window.location.hash.slice(1)).get('key')
  uploadIdRef.value = uploadId
  if (!uploadId || !keyHash) {
    uiState.value = 'error'
    uiMessageKey.value = 'download.missingIdOrKey'
    uiMessageText.value = null
    return
  }
  try {
    uiState.value = 'running'
    uiMessageKey.value = 'download.loadingMeta'
    uiMessageText.value = null
    const key = await importShareKey(keyHash)
    downloadKey.value = key
    await loadManifest(uploadId, key)
    await refreshResumeState(uploadId)
    uiState.value = 'idle'
    uiMessageKey.value = 'download.ready'
    uiMessageText.value = null
  } catch (error) {
    console.error(error)
    uiState.value = 'error'
    uiMessageText.value = error instanceof Error ? error.message : null
    uiMessageKey.value = uiMessageText.value ? uiMessageKey.value : 'common.unknownError'
  }
}

onMounted(() => {
  preload()
})

async function startDownload(mode: 'resume' | 'fresh' = 'resume') {
  busy.value = true
  try {
    const uploadId = uploadIdRef.value ?? window.location.pathname.split('/').pop()
    const keyHash = new URLSearchParams(window.location.hash.slice(1)).get('key')
    if (!uploadId || !keyHash) throw new Error(t('download.missingIdOrKey'))

    // Chrome/Edge: the file picker must be opened directly from the user gesture.
    // Don't await network/crypto work before calling `showSaveFilePicker`, otherwise it may be blocked.
    let preselectedHandle: FileSystemFileHandle | undefined
    try {
      const picker = (window as any).showSaveFilePicker as undefined | ((opts: any) => Promise<FileSystemFileHandle>)
      const shouldPick = typeof picker === 'function' && (mode === 'fresh' || !canResume.value)
      if (shouldPick) {
        const single = manifest.value?.files?.length === 1 ? manifest.value.files[0] : null
        const rawName = single ? single.name : `secure-file-upload-${uploadId}.zip`
        const suggestedName = rawName.split(/[\\/]/).pop() || rawName
        preselectedHandle = await picker({ suggestedName })
      }
    } catch (error) {
      const name = (error as any)?.name
      if (name === 'AbortError') throw new Error(t('download.saveAborted'))
      throw error
    }

    const key = downloadKey.value ?? (await importShareKey(keyHash))
    downloadKey.value = key
    uiState.value = 'running'
    uiMessageKey.value = 'download.loadingMeta'
    uiMessageText.value = null
    await loadManifest(uploadId, key)
    await refreshResumeState(uploadId)
    if (mode === 'fresh') {
      await deleteDownloadResume(uploadId)
      await refreshResumeState(uploadId)
    }
    uiMessageKey.value = mode === 'fresh' ? 'download.startingFresh' : 'download.downloading'
    progressPercent.value = 0
    progressDetail.value = ''
    await downloadAndDecryptWithManifest(uploadId, key, manifest.value, (percent, detail) => {
      progressPercent.value = percent
      progressDetail.value = detail
    }, { preselectedHandle })
    uiState.value = 'success'
    uiMessageKey.value = 'download.completed'
    uiMessageText.value = null
    await refreshResumeState(uploadId)
  } catch (error) {
    console.error(error)
    uiState.value = 'error'
    const errAny = error as any
    const errorKey = typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined
    uiMessageText.value = errorKey ? null : error instanceof Error ? error.message : null
    uiMessageKey.value = errorKey ? `errors.${errorKey}` : uiMessageText.value ? uiMessageKey.value : 'common.unknownError'
    try {
      const uploadId = uploadIdRef.value ?? window.location.pathname.split('/').pop()
      if (uploadId) await refreshResumeState(uploadId)
    } catch {
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section
    class="card"
    data-testid="download-page"
    role="region"
    :aria-label="t('download.title')"
    aria-describedby="download-hint"
    tabindex="0"
  >
    <h1>{{ t('download.title') }}</h1>
    <p id="download-hint" class="hint">{{ t('download.keyInFragment') }}</p>
    <NoticeBar
      test-id="download:notice"
      :title="uiMessage"
      :right="resumeDetail || undefined"
      :variant="noticeVariant"
      :data-resume-next-chunk="resumeNextChunkIndex ?? undefined"
    />
    <div v-if="progressPercent !== null && busy" class="progress-wrap" data-testid="download:progress">
      <progress class="bar" :value="progressPercent" max="100" />
    </div>
    <div v-if="progressDetail && busy" class="meta-row" data-testid="download:progress-detail">
      <span class="muted small">{{ progressDetail }}</span>
      <span v-if="progressPercent !== null" class="muted small right">{{ progressPercent.toFixed(0) }}%</span>
    </div>
    <div v-if="manifest">
      <h2>{{ t('download.files') }}</h2>
      <div class="filelist download" data-testid="download:file-list">
        <div class="filelist-header">
          <strong>{{ t('common.filesCount', { count: manifest.files.length }) }}</strong>
          <span class="muted small right">{{ formatBytes(manifest.files.reduce((s, f) => s + f.size, 0)) }}</span>
        </div>
        <hr class="sep tight" />
        <ul>
          <li v-for="file in manifest.files" :key="displayName(file) + ':' + file.size + ':' + file.lastModified">
            <span class="name" data-testid="download:file-row" :title="displayName(file)">{{ displayName(file) }}</span>
            <span class="muted small right">{{ formatBytes(file.size) }}</span>
          </li>
        </ul>
      </div>
    </div>
    <div class="actions">
      <button v-if="canResume" data-testid="download:btn-resume" :disabled="busy" @click="startDownload('resume')">{{ t('common.resume') }}</button>
      <button v-else data-testid="download:btn-start" :disabled="busy" @click="startDownload('fresh')">{{ t('download.downloadAndDecrypt') }}</button>
      <button v-if="canResume" data-testid="download:btn-restart" class="secondary" :disabled="busy" @click="startDownload('fresh')">{{ t('common.restart') }}</button>
      <button
        v-if="uiState === 'success'"
        data-testid="download:btn-back-to-upload"
        class="secondary"
        @click="goToUploadHome"
      >
        {{ t('download.backToUpload') }}
      </button>
    </div>
  </section>
</template>
