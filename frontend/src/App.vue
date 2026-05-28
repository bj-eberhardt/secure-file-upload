<script setup lang="ts">
import { computed } from 'vue'
import UploadView from './views/UploadView.vue'
import DownloadView from './views/DownloadView.vue'
import { setLocale } from './i18n'
import { useI18n } from 'vue-i18n'

const path = window.location.pathname
const isDownload = path.startsWith('/d/')

const { t, locale } = useI18n()
const currentLocale = computed(() => locale.value as 'en' | 'de')
function switchLocale(next: 'en' | 'de') {
  setLocale(next)
}
</script>

<template>
  <main class="page">
    <header class="topbar">
      <div class="brand">{{ t('app.productName') }}</div>
      <div class="lang">
        <span class="muted small">{{ t('app.language') }}:</span>
        <button class="langbtn" :class="{ active: currentLocale === 'en' }" @click="switchLocale('en')">English</button>
        <button class="langbtn" :class="{ active: currentLocale === 'de' }" @click="switchLocale('de')">Deutsch</button>
      </div>
    </header>
    <DownloadView v-if="isDownload" />
    <UploadView v-else />
  </main>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.brand {
  font-weight: 700;
}
.lang {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.langbtn {
  border: 1px solid var(--border, #ddd);
  background: transparent;
  color: inherit;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.langbtn.active {
  border-color: var(--accent, #3b82f6);
}
</style>
