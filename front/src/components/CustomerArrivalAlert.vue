<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { notificationCategory, useCustomerNotifications } from '../stores/customerNotifications'
import { displayDate } from '../utils/display-date'
import UiIcon from './UiIcon.vue'

const app = useApp(), route = useRoute(), router = useRouter()
const notifications = useCustomerNotifications()
const dialog = ref<HTMLDialogElement>(), heading = ref<HTMLElement>()
const busy = ref(false), actionError = ref('')
const eligible = computed(() => route.path.startsWith('/customer-app/') && app.account?.role === 'customer')
const current = computed(() => notifications.appArrivals[0])
const isSafety = computed(() => current.value?.category === 'RISK')
const tone = computed(() => {
  if (!isSafety.value) return 'info'
  const level = String(current.value?.severity || current.value?.currentRiskLevel || current.value?.riskLevel || '').toUpperCase()
  return ['DANGER', 'CRITICAL', 'EMERGENCY', '위험', '긴급'].includes(level) ? 'danger' : 'caution'
})
const label = computed(() => isSafety.value
  ? tone.value === 'danger' ? '위험 안내' : '안전 정보'
  : notificationCategory(current.value?.category || ''))
let interval: ReturnType<typeof setInterval> | undefined
let previousFocus: HTMLElement | null = null
let restoreFocus = true

function restorePreviousFocus() {
  if (restoreFocus && previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
  previousFocus = null
  restoreFocus = true
}
async function syncDialog() {
  await nextTick()
  if (eligible.value && current.value && document.visibilityState === 'visible') {
    if (!dialog.value?.open) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      dialog.value?.showModal()
    }
    heading.value?.focus({ preventScroll: true })
  } else if (dialog.value?.open) {
    dialog.value.close()
    restorePreviousFocus()
  }
}
function dismiss() {
  notifications.dismissAppArrivals()
}
async function acknowledge() {
  if (!current.value || busy.value) return
  const id = current.value.id, owner = app.account?.id
  busy.value = true
  actionError.value = ''
  try { await notifications.read(id) }
  catch (error) {
    if (app.account?.id === owner && current.value?.id === id)
      actionError.value = (error as Error).message || '확인 처리에 실패했습니다. 다시 시도해 주세요.'
  } finally {
    if (app.account?.id === owner) busy.value = false
  }
}
async function details() {
  if (!current.value) return
  const id = current.value.id
  restoreFocus = false
  notifications.dismissAppArrivals()
  await router.push({ path: '/customer-app/screen-12', query: { notification: id } })
}
function receive(event: Event) {
  if (!eligible.value) return
  const detail = (event as CustomEvent).detail
  if (detail?.name === 'notification-new' && detail.data) notifications.ingest(detail.data)
  if (detail?.name === 'connected') void notifications.refresh()
}
function visible() {
  if (document.visibilityState === 'visible') {
    void notifications.refresh()
    void syncDialog()
  }
}
function stop() {
  if (interval !== undefined) clearInterval(interval)
  interval = undefined
  window.removeEventListener('routel-backend-event', receive)
  document.removeEventListener('visibilitychange', visible)
  notifications.setAppAlertsEnabled(false)
}
watch([() => app.account?.id, () => app.account?.role, eligible], (values, previous) => {
  stop()
  busy.value = false
  actionError.value = ''
  if (values[1] === 'customer' || previous?.[1] === 'customer') notifications.reset()
  if (eligible.value) {
    notifications.setAppAlertsEnabled(true)
    // Reuse the app's shared SSE dispatch: never open an additional stream.
    window.addEventListener('routel-backend-event', receive)
    document.addEventListener('visibilitychange', visible)
    interval = setInterval(() => {
      if (document.visibilityState === 'visible') void notifications.refresh()
    }, 30_000)
    void notifications.refresh()
  }
  void syncDialog()
}, { immediate: true })
watch(() => current.value?.id, () => {
  busy.value = false
  actionError.value = ''
  void syncDialog()
})
onBeforeUnmount(() => {
  stop()
  dialog.value?.close()
  restorePreviousFocus()
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="customer-arrival-alert" :data-tone="tone" role="alertdialog"
      aria-modal="true" aria-labelledby="customer-arrival-title" aria-describedby="customer-arrival-body"
      @cancel.prevent="dismiss">
      <template v-if="current">
        <header class="arrival-heading">
          <span class="arrival-symbol" aria-hidden="true"><UiIcon name="bell" /></span>
          <div><span class="arrival-brand">ROUTEL</span><strong>{{ isSafety ? '안전 알림' : '새 알림' }}</strong></div>
          <button type="button" class="arrival-close" aria-label="알림 창 닫기" @click="dismiss">×</button>
        </header>
        <div class="arrival-content">
          <div class="arrival-meta"><span class="arrival-category">{{ label }}</span><time :datetime="current.createdAt">{{ displayDate(current.createdAt) }}</time></div>
          <h2 id="customer-arrival-title" ref="heading" tabindex="-1">{{ current.title }}</h2>
          <p id="customer-arrival-body" class="arrival-body">{{ current.body }}</p>
          <p class="arrival-origin">ROUTEL 서비스에서 보낸 알림입니다.</p>
          <p v-if="notifications.appArrivals.length > 1" class="arrival-queue">대기 중인 새 알림 {{ notifications.appArrivals.length - 1 }}개</p>
          <p v-if="actionError" class="arrival-error" role="alert">{{ actionError }}</p>
        </div>
        <footer class="arrival-actions">
          <button type="button" class="arrival-details" @click="details">상세 보기</button>
          <button type="button" class="arrival-confirm" :disabled="busy" @click="acknowledge">{{ busy ? '확인 중…' : '확인' }}</button>
        </footer>
      </template>
    </dialog>
  </Teleport>
</template>

<style scoped>
dialog.customer-arrival-alert.customer-arrival-alert {
  --arrival-accent: #225bb5;
  box-sizing: border-box;
  width: min(380px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
  max-height: calc(100dvh - 32px - env(safe-area-inset-top, 0px) - env(safe-area-inset-bottom, 0px));
  padding: 0;
  border: 1px solid var(--r-line, #dbe3ed);
  border-top: 5px solid var(--arrival-accent);
  border-radius: 20px;
  background: var(--r-surface, #fff);
  color: var(--r-ink, #172d48);
  box-shadow: 0 24px 80px #10243d4d;
  font-family: var(--r-font, Pretendard, sans-serif);
  font-size: 15px;
  line-height: 1.7;
  overflow: auto;
  overscroll-behavior: contain;
}
dialog.customer-arrival-alert[data-tone="caution"] { --arrival-accent: #946000; }
dialog.customer-arrival-alert[data-tone="danger"] { --arrival-accent: #b92e47; }
:global(:root[data-theme="dark"] dialog.customer-arrival-alert) { --arrival-accent: #9ac5ff; }
:global(:root[data-theme="dark"] dialog.customer-arrival-alert[data-tone="caution"]) { --arrival-accent: #f2c879; }
:global(:root[data-theme="dark"] dialog.customer-arrival-alert[data-tone="danger"]) { --arrival-accent: #ff9aae; }
dialog.customer-arrival-alert::backdrop { background: #10243d9c; backdrop-filter: blur(3px); }
.customer-arrival-alert .arrival-heading { display: flex; align-items: center; gap: 12px; margin: 0; padding: 19px 20px; background: color-mix(in srgb, var(--arrival-accent) 8%, var(--r-surface, white)); border-bottom: 1px solid var(--r-line, #dbe3ed); }
.customer-arrival-alert .arrival-symbol { display: grid; place-items: center; flex: 0 0 42px; width: 42px; height: 42px; border: 1px solid color-mix(in srgb, var(--arrival-accent) 25%, transparent); border-radius: 13px; color: var(--arrival-accent); }
.customer-arrival-alert .arrival-heading>div { display: grid; gap: 1px; }
.customer-arrival-alert .arrival-brand { font-size: 11px; font-weight: 800; letter-spacing: .1em; color: var(--arrival-accent); }
.customer-arrival-alert .arrival-heading strong { font-size: 17px; line-height: 1.4; }
.customer-arrival-alert button { box-sizing: border-box; font: inherit; cursor: pointer; }
.customer-arrival-alert .arrival-close { flex: 0 0 40px; width: 40px; height: 40px; margin: 0 -7px 0 auto; padding: 0; background: transparent; border: 0; border-radius: 9px; color: var(--r-muted, #63748a); font-size: 27px; line-height: 1; }
.customer-arrival-alert .arrival-content { padding: 22px 22px 20px; }
.customer-arrival-alert .arrival-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 15px; font-size: 11px; color: var(--r-muted, #63748a); }
.customer-arrival-alert .arrival-category { padding: 2px 8px; border: 1px solid color-mix(in srgb, var(--arrival-accent) 25%, transparent); border-radius: 5px; color: var(--arrival-accent); font-weight: 700; }
.customer-arrival-alert h2 { margin: 0 0 13px; color: var(--r-ink, #172d48); font-size: 22px; font-weight: 750; line-height: 1.45; letter-spacing: -.025em; overflow-wrap: anywhere; }
.customer-arrival-alert .arrival-body { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 16px; line-height: 1.75; color: var(--r-ink, #172d48); }
.customer-arrival-alert .arrival-origin { margin: 22px 0 0; font-size: 11px; line-height: 1.6; color: var(--r-muted, #63748a); }
.customer-arrival-alert .arrival-queue { margin: 8px 0 0; color: var(--arrival-accent); font-size: 12px; font-weight: 600; }
.customer-arrival-alert .arrival-error { margin: 12px 0 0; color: var(--r-danger, #b92e47); font-size: 13px; }
.customer-arrival-alert .arrival-actions { position: sticky; bottom: 0; display: flex; gap: 10px; padding: 16px 22px 20px; background: var(--r-surface, #fff); border-top: 1px solid var(--r-line, #dbe3ed); }
.customer-arrival-alert .arrival-actions>button { flex: 1; min-width: 0; min-height: 46px; padding: 10px 12px; border: 1px solid var(--r-line, #dbe3ed); border-radius: 10px; line-height: 1.5; font-size: 14px; font-weight: 700; }
.customer-arrival-alert .arrival-details { color: var(--r-ink, #172d48); background: var(--r-surface, #fff); }
.customer-arrival-alert .arrival-confirm { color: var(--r-surface, #fff); background: var(--arrival-accent); border-color: var(--arrival-accent); }
.customer-arrival-alert button:disabled { opacity: .65; cursor: wait; }
.customer-arrival-alert button:focus-visible { outline: 3px solid var(--arrival-accent); outline-offset: 3px; }
.customer-arrival-alert h2:focus { outline: none; }
@media (prefers-reduced-motion: reduce) {
  dialog.customer-arrival-alert, dialog.customer-arrival-alert::backdrop { animation: none; transition: none; }
}
</style>
