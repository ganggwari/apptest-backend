<script setup lang="ts">
import { uiIconPaths, type UiIconName } from '../utils/ui-icons'
defineProps<{ name: UiIconName }>()
</script>
<template>
  <span v-if="name==='theme'" class="theme-switch-visual" aria-hidden="true">
    <span class="theme-switch-thumb"></span>
    <svg class="theme-switch-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
      <circle cx="12" cy="12" r="3.5" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" />
    </svg>
    <svg class="theme-switch-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z" />
    </svg>
  </span>
  <svg
    v-else
    class="ui-icon"
    viewBox="0 0 24 24"
    width="24"
    height="24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="uiIconPaths[name]" />
  </svg>
</template>
