<script setup lang="ts">
import { useRouter } from 'vue-router'
const props = withDefaults(defineProps<{ fallback?: string }>(), {
    fallback: '/admin-app/screen-3',
  }),
  router = useRouter()
function back() {
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && previous.startsWith('/admin-app/')) router.back()
  else void router.replace(props.fallback)
}
</script>
<template>
  <button type="button" class="admin-back-button" aria-label="이전 화면으로" @click="back">
    <UiIcon name="back" />
  </button>
</template>
<style scoped src="../styles/components/admin-back-button.css"></style>
