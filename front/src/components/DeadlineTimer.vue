<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { formatRemaining } from '../composables/useVerificationTimer'
const props = defineProps<{ deadline: string | number; expiredLabel?: string }>()
const now = ref(Date.now()),
  interval = setInterval(() => {
    now.value = Date.now()
  }, 250)
onBeforeUnmount(() => clearInterval(interval))
const end = computed(() => {
  if (typeof props.deadline === 'number') return props.deadline
  // Backend LocalDateTime values use the Korean database clock.
  const value = props.deadline.replace(' ', 'T').replace(/(\.\d{3})\d+/, '$1')
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : value + '+09:00')
})
const seconds = computed(() => Math.max(0, Math.ceil((end.value - now.value) / 1000)))
</script>
<template>
  <span
    v-if="Number.isFinite(end)"
    role="timer"
    aria-live="off"
    class="deadline-timer"
    :class="{'deadline-expired':seconds===0}"
  >
    {{seconds>0?formatRemaining(seconds):expiredLabel||'시간 만료'}}
  </span>
  <span v-else>—</span>
</template>
<style scoped src="../styles/components/deadline-timer.css"></style>
