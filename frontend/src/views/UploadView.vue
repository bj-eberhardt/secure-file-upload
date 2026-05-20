<script setup lang="ts">
import { ref } from 'vue'
import { initUpload, uploadEncryptedChunks, completeUpload } from '../upload/chunkUploader'
import { createShareKey, exportShareKey } from '../crypto/keyDerivation'

const files = ref<File[]>([])
const status = ref('Bereit')
const shareLink = ref('')
const busy = ref(false)

function onFilesChanged(event: Event) {
  const input = event.target as HTMLInputElement
  files.value = Array.from(input.files ?? [])
}

async function upload() {
  busy.value = true
  status.value = 'Initialisiere Upload...'
  try {
    const key = await createShareKey()
    const init = await initUpload()

    status.value = 'Verschlüssele und lade Chunks hoch...'
    const result = await uploadEncryptedChunks(init, files.value, key, (message) => {
      status.value = message
    })

    status.value = 'Schließe Upload ab...'
    const completed = await completeUpload(init.uploadId, result)
    const exportedKey = await exportShareKey(key)
    shareLink.value = `${window.location.origin}${completed.downloadPath}#key=${exportedKey}`
    status.value = 'Fertig'
  } catch (error) {
    console.error(error)
    status.value = error instanceof Error ? error.message : 'Unbekannter Fehler'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card">
    <h1>Secure Upload</h1>
    <p>Wähle mehrere Dateien. Das Skeleton enthält bereits API, Worker- und Crypto-Stubs.</p>

    <input type="file" multiple @change="onFilesChanged" />

    <div class="actions">
      <button :disabled="busy || files.length === 0" @click="upload">Verschlüsselt hochladen</button>
    </div>

    <p class="progress">{{ status }}</p>

    <div v-if="shareLink">
      <h2>Download-Link</h2>
      <code>{{ shareLink }}</code>
    </div>
  </section>
</template>
