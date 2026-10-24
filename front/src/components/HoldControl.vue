<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { workflows } from '../services/workflows'
const props = defineProps<{
  action: 'block' | 'release'
  facilityId: string
  facilityName?: string
}>()
const reason = ref(''),
  progress = ref(0),
  message = ref(''),
  busy = ref(false)
let timer: number | undefined,
  started = 0
function cancel() {
  clearInterval(timer)
  timer = undefined
  progress.value = 0
}
async function run() {
  cancel()
  if (!reason.value.trim()) {
    message.value = '사유를 입력해 주세요.'
    return
  }
  if (busy.value) return
  if (!workflows.control) {
    message.value = '통제 서비스가 연결되지 않았습니다. 실제 시설 상태는 변경되지 않았습니다.'
    return
  }
  busy.value = true
  try {
    await workflows.control({
      facilityId: props.facilityId,
      action: props.action,
      reason: reason.value,
    })
    message.value = '시설 상태 변경을 확인했습니다.'
  } catch (e) {
    message.value = e instanceof Error ? e.message : '요청 실패'
  } finally {
    busy.value = false
  }
}
function start() {
  if (busy.value) return
  cancel()
  started = Date.now()
  timer = window.setInterval(() => {
    progress.value = Math.min(100, (Date.now() - started) / 15)
    if (progress.value === 100) void run()
  }, 50)
}
function keyboard() {
  if (
    confirm(
      `${props.facilityName || props.facilityId} ${props.action === 'block' ? '통제 시작' : '통제 해제'}를 요청할까요?`,
    )
  )
    void run()
}
onBeforeUnmount(cancel)
</script>
<template>
  <section>
    <label>사유<input v-model="reason" required />
    </label>
    <p>{{facilityName||facilityId}} · {{action==='block'?'통제 시작':'통제 해제'}}</p>
    <button
      class="held-control"
      type="button"
      :disabled="busy"
      @pointerdown.prevent="start"
      @pointerup="cancel"
      @pointerleave="cancel"
      @pointercancel="cancel"
      @keydown.enter.prevent="keyboard"
      @keydown.space.prevent="keyboard"
    >
      {{busy?'처리 중…':'1.5초 길게 누르기'}}
    </button>
    <progress class="control-progress" :value="progress" max="100" aria-label="누르기 진행" />
    <button type="button" :disabled="busy" @click="keyboard">확인 후 요청</button>
    <p role="status">{{message}}</p>
  </section>
</template>
