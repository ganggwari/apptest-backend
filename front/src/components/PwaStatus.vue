<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
withDefaults(defineProps<{ hideInstall?: boolean }>(), { hideInstall: false })
const online = ref(navigator.onLine),
  prompt = ref<any>(null),
  message = ref('')
function network() {
  online.value = navigator.onLine
}
function installable(event: Event) {
  event.preventDefault()
  prompt.value = event
}
async function install() {
  if (!prompt.value) return
  await prompt.value.prompt()
  const result = await prompt.value.userChoice
  if (result.outcome === 'accepted') message.value = '홈 화면에 앱을 추가했습니다.'
  prompt.value = null
}
function installed() {
  prompt.value = null
}
onMounted(() => {
  window.addEventListener('online', network)
  window.addEventListener('offline', network)
  window.addEventListener('beforeinstallprompt', installable)
  window.addEventListener('appinstalled', installed)
})
onBeforeUnmount(() => {
  window.removeEventListener('online', network)
  window.removeEventListener('offline', network)
  window.removeEventListener('beforeinstallprompt', installable)
  window.removeEventListener('appinstalled', installed)
})
</script>
<template>
  <div v-if="!online||(!hideInstall&&(prompt||message))" class="pwa-status" role="status">
    <span v-if="!online">오프라인 · 실시간 상태를 확인할 수 없습니다.</span>
    <button v-if="prompt&&!hideInstall" type="button" @click="install">홈 화면에 앱 설치</button>
    <span v-if="message">{{message}}</span>
  </div>
</template>
<style scoped src="../styles/components/pwa-status.css"></style>
