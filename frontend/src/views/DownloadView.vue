<script setup lang="ts">
import { ref } from 'vue'
import { importShareKey } from '../crypto/keyDerivation'
import { downloadAndDecrypt } from '../download/downloadAndDecrypt'

const status = ref('Bereit zum Download')
const busy = ref(false)

async function startDownload() {
  busy.value = true
  try {
    const uploadId = window.location.pathname.split('/').pop()
    const keyHash = new URLSearchParams(window.location.hash.slice(1)).get('key')
    if (!uploadId || !keyHash) throw new Error('Upload-ID oder Schlüssel fehlt')

    const key = await importShareKey(keyHash)
    status.value = 'Lade verschlüsselte Datei herunter...'
    await downloadAndDecrypt(uploadId, key)
    status.value = 'Download abgeschlossen'
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
    <h1>Secure Download</h1>
    <p>Der Schlüssel wird aus dem URL-Fragment gelesen und nicht an den Server gesendet.</p>
    <button :disabled="busy" @click="startDownload">Herunterladen und entschlüsseln</button>
    <p class="progress">{{ status }}</p>
  </section>
</template>
