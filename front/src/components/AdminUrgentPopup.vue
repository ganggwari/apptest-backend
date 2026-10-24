<script setup lang="ts">
// 관리자 긴급 알림 팝업: SSE notification-new(urgent) 도착 시 + 접속(재연결) 시 미확인 긴급 알림을 띄운다.
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
import { useBackendEvents } from '../composables/useBackendEvents'
const app = useApp(),
  router = useRouter()
const queue = ref<any[]>([])
const isAdmin = () => app.account?.role === 'REGIONAL_ADMIN'
function push(n: any) {
  if (!n || !n.urgent || queue.value.some((q) => q.id === n.id)) return
  queue.value = [n, ...queue.value].slice(0, 5)
}
async function loadUnread() {
  if (!isAdmin()) return
  try {
    const list = await backend('/api/managers/me/notifications/urgent')
    ;(Array.isArray(list) ? list : []).slice().reverse().forEach(push)
  } catch {
    /* 팝업은 보조 수단: 실패해도 알림 목록에서 확인 가능 */
  }
}
useBackendEvents(({ name, data }) => {
  if (!isAdmin()) return
  if (name === 'connected') void loadUnread()
  if (name === 'notification-new') push(data)
})
watch(() => app.account?.id, () => (queue.value = []))
async function close(n: any) {
  queue.value = queue.value.filter((q) => q.id !== n.id)
  try {
    await backend('/api/managers/me/notifications/' + n.id + '/read', { method: 'PUT' })
  } catch {
    /* ignore */
  }
}
async function go(n: any) {
  await close(n)
  if (n.category === 'EQUIPMENT') router.push('/admin-app/screen-11')
  else router.push('/admin-app/notifications')
}
</script>
<template>
  <div v-if="queue.length" class="admin-urgent-stack" role="alert" aria-live="assertive">
    <section v-for="n in queue.slice(0, 2)" :key="n.id" class="admin-urgent-card">
      <b>🚨 {{ n.title }}</b>
      <p>{{ n.body }}</p>
      <div>
        <button type="button" @click="go(n)">확인하러 가기</button>
        <button type="button" class="ghost" @click="close(n)">닫기</button>
      </div>
    </section>
  </div>
</template>
<style>
.admin-urgent-stack{position:fixed;left:12px;right:12px;top:calc(env(safe-area-inset-top,0px) + 56px);z-index:9999;display:grid;gap:8px;max-width:520px;margin:0 auto}
.admin-urgent-card{background:#b3261e;color:#fff;border-radius:14px;padding:12px 14px;box-shadow:0 8px 24px rgba(0,0,0,.35)}
.admin-urgent-card p{margin:4px 0 8px;font-size:13px;line-height:1.4}
.admin-urgent-card button{background:#fff;color:#b3261e;border:0;border-radius:10px;padding:8px 12px;font-weight:700;margin-right:6px}
.admin-urgent-card button.ghost{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.6)}
</style>
