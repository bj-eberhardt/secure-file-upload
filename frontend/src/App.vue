<script setup lang="ts">
import UploadView from './views/UploadView.vue'
import DownloadView from './views/DownloadView.vue'

const path = window.location.pathname
const isDownload = path.startsWith('/d/')
</script>

<template>
  <main class="page">
    <DownloadView v-if="isDownload" />
    <UploadView v-else />
  </main>
</template>
