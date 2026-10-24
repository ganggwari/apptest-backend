<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import DeadlineTimer from './DeadlineTimer.vue'
defineProps<{ items: Record<string, any>[]; saving: boolean }>()
const emit = defineEmits<{ review: [row: Record<string, any>, number: string] }>()
const now = ref(Date.now()),
  timer = setInterval(() => {
    now.value = Date.now()
  }, 250)
onBeforeUnmount(() => clearInterval(timer))
const corrections = reactive<Record<string, string>>({})
function imageUrl(value: string) {
  if (!value || value.includes(':') || value.split(/[\\/]/).some((p) => p === '..' || p === '.'))
    return ''
  return (
    '/api/uploads/' +
    value
      .replace(/^\/api\/uploads\//, '')
      .split(/[\\/]/)
      .filter(Boolean)
      .map(encodeURIComponent)
      .join('/')
  )
}
function expired(row: Record<string, any>) {
  if (typeof row.reviewExpiresAt === 'number') return row.reviewExpiresAt <= now.value
  if (!row.reviewDeadline) return false
  const raw = String(row.reviewDeadline).replace(' ', 'T')
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/.test(raw) ? raw : raw + '+09:00') <= now.value
}
</script>
<template>
  <div class="pgrid restored-review-grid" id="grid">
    <form
      v-for="row in items"
      :key="row.id"
      class="pcard"
      @submit.prevent="emit('review',row,corrections[row.id]??row.detectedNum??'')"
    >
      <div class="pmeta">
        <strong>{{row.underpassName}}</strong>
        <span v-if="row.recognitionStatus==='LOW_CONFIDENCE'" class="low">인식 신뢰도 낮음</span>
      </div>
      <div class="pimg">
        <img
          v-if="imageUrl(row.imagePath)&&!row.imageUnavailable"
          :src="imageUrl(row.imagePath)"
          alt="인식된 번호판"
          @error="row.imageUnavailable=true"
        />
        <span v-else>번호판 이미지를 불러올 수 없습니다.</span>
      </div>
      <div class="timer">
        <DeadlineTimer
          v-if="row.reviewDeadline||row.reviewExpiresAt"
          :deadline="row.reviewExpiresAt??row.reviewDeadline"
        />
      </div>
      <input
        class="plate"
        :value="corrections[row.id]??row.detectedNum"
        aria-label="인식된 번호판 (수정 가능)"
        required
        @input="corrections[row.id]=($event.target as HTMLInputElement).value"
      />
      <div class="pbtns">
        <button
          class="btn sm"
          type="button"
          :disabled="saving||expired(row)||!row.detectedNum"
          @click="emit('review',row,row.detectedNum)"
        >맞음</button>
        <button
          class="btn sm primary"
          type="submit"
          :disabled="saving||expired(row)||!corrections[row.id]?.trim()"
        >수정 후 확정</button>
      </div>
    </form>
  </div>
  <p v-if="!items.length" class="empty">대기 중인 판별이 없어요. 새로고침하면 새 건을 확인할 수 있어요.</p>
</template>
