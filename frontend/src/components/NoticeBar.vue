<script setup lang="ts">
type Variant = 'default' | 'error' | 'success'

defineProps<{
  title: string
  right?: string
  variant?: Variant
  testId?: string
}>()
</script>

<template>
  <div
    class="notice"
    :data-testid="testId"
    :data-variant="variant ?? 'default'"
    :class="{ error: variant === 'error', success: variant === 'success' }"
    :role="variant === 'error' ? 'alert' : 'status'"
    :aria-live="variant === 'error' ? 'assertive' : 'polite'"
  >
    <span class="notice-title">{{ title }}</span>
    <span v-if="right" class="notice-right">{{ right }}</span>
  </div>
</template>
