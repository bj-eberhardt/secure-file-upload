<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
const uiMessage = ref('Bereit zum Download')
const busy = ref(false)
const manifest = ref<PlainManifest | null>(null)
const canResume = ref(false)
const resumeDetail = ref('')
const resumeNextChunkIndex = ref<number | null>(null)
const downloadKey = ref<CryptoKey | null>(null)
const uploadIdRef = ref<string | null>(null)
const progressPercent = ref<number | null>(null)
const progressDetail = ref('')

const noticeVariant = computed(() => (uiState.value === 'success' ? 'success' : uiState.value === 'error' ? 'error' : 'default'))

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
    resumeDetail.value = resume ? `Fortsetzen möglich (ab Chunk ${resume.nextChunkIndex})` : ''
  } catch {
    canResume.value = false
    resumeNextChunkIndex.value = null
    resumeDetail.value = ''
  }
}

async function preload() {
  const uploadId = window.location.pathname.split('/').pop() ?? null
  const keyHash = new URLSearchParams(window.location.hash.slice(1)).get('key')
  uploadIdRef.value = uploadId
  if (!uploadId || !keyHash) {
    uiState.value = 'error'
    uiMessage.value = 'Upload-ID oder Schlüssel fehlt'
    return
  }
  try {
    uiState.value = 'running'
    uiMessage.value = 'Lade Metadaten...'
    const key = await importShareKey(keyHash)
    downloadKey.value = key
    await loadManifest(uploadId, key)
    await refreshResumeState(uploadId)
    uiState.value = 'idle'
    uiMessage.value = 'Bereit zum Download'
  } catch (error) {
    console.error(error)
    uiState.value = 'error'
    uiMessage.value = error instanceof Error ? error.message : 'Unbekannter Fehler'
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
    if (!uploadId || !keyHash) throw new Error('Upload-ID oder Schlüssel fehlt')

    // Chrome/Edge: the file picker must be opened directly from the user gesture.
    // Don't await network/crypto work before calling `showSaveFilePicker`, otherwise it may be blocked.
    let preselectedHandle: FileSystemFileHandle | undefined
    try {
      const picker = (window as any).showSaveFilePicker as undefined | ((opts: any) => Promise<FileSystemFileHandle>)
      const shouldPick = typeof picker === 'function' && (mode === 'fresh' || !canResume.value)
      if (shouldPick) {
        const single = manifest.value?.files?.length === 1 ? manifest.value.files[0] : null
        const rawName = single ? (single.name) : `secure-upload-${uploadId}.zip`
        const suggestedName = rawName.split(/[\\/]/).pop() || rawName
        preselectedHandle = await picker({ suggestedName })
      }
    } catch (error) {
      const name = (error as any)?.name
      if (name === 'AbortError') throw new Error('Speichern abgebrochen')
      throw error
    }

    const key = downloadKey.value ?? (await importShareKey(keyHash))
    downloadKey.value = key
    uiState.value = 'running'
    uiMessage.value = 'Lade Metadaten...'
    await loadManifest(uploadId, key)
    await refreshResumeState(uploadId)
    if (mode === 'fresh') {
      await deleteDownloadResume(uploadId)
      await refreshResumeState(uploadId)
    }
    uiMessage.value = mode === 'fresh' ? 'Starte Download neu...' : 'Lade verschlüsselte Datei herunter...'
    progressPercent.value = 0
    progressDetail.value = ''
    await downloadAndDecryptWithManifest(uploadId, key, manifest.value, (percent, detail) => {
      progressPercent.value = percent
      progressDetail.value = detail
    }, { preselectedHandle })
    uiState.value = 'success'
    uiMessage.value = 'Download abgeschlossen'
    await refreshResumeState(uploadId)
  } catch (error) {
    console.error(error)
    uiState.value = 'error'
    uiMessage.value = error instanceof Error ? error.message : 'Unbekannter Fehler'
    try {
      const uploadId = uploadIdRef.value ?? window.location.pathname.split('/').pop()
      if (uploadId) await refreshResumeState(uploadId)
    } catch {
      // ignore
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card" data-testid="download-page">
    <h1>Secure Download</h1>
    <p>Der Schlüssel wird aus dem URL-Fragment gelesen und nicht an den Server gesendet.</p>
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
      <h2>Dateien</h2>
      <div class="filelist download" data-testid="download:file-list">
        <div class="filelist-header">
          <strong>{{ manifest.files.length }} Datei(en)</strong>
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
      <button v-if="canResume" data-testid="download:btn-resume" :disabled="busy" @click="startDownload('resume')">Fortsetzen</button>
      <button v-else data-testid="download:btn-start" :disabled="busy" @click="startDownload('fresh')">Herunterladen und entschlüsseln</button>
      <button v-if="canResume" data-testid="download:btn-restart" class="secondary" :disabled="busy" @click="startDownload('fresh')">Neu starten</button>
    </div>
  </section>
</template>
