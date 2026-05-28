<script setup lang="ts">
type Tone = 'brand' | 'soft'

withDefaults(
  defineProps<{
    title: string
    iconSrc: string
    tone?: Tone
    testId?: string
  }>(),
  { tone: 'soft' }
)
</script>

<template>
  <aside class="callout" :class="tone" :data-testid="testId">
    <img class="callout-art" :src="iconSrc" alt="" aria-hidden="true" width="120" height="96" />
    <div class="callout-text">
      <div class="callout-title">{{ title }}</div>
      <div class="callout-body">
        <slot />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.callout {
  margin: 14px 0 10px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border, #dde4f0);
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: center;
}
.callout.brand {
  background: linear-gradient(135deg, rgba(51, 72, 255, 0.1), rgba(238, 242, 255, 0.85));
}
.callout.soft {
  background: rgba(238, 242, 255, 0.55);
}
.callout-art {
  display: block;
  filter: drop-shadow(0 14px 28px rgba(24, 33, 54, 0.12));
}
.callout-title {
  font-weight: 700;
  letter-spacing: 0.2px;
  margin-bottom: 3px;
}
.callout-body {
  color: var(--muted, #5c6b84);
  line-height: 1.35;
  font-size: 13px;
}

@media (max-width: 540px) {
  .callout {
    grid-template-columns: 1fr;
  }
  .callout-art {
    width: 112px;
    height: auto;
  }
}
</style>

