import { computed, onBeforeUnmount, ref } from 'vue'

export interface PhoneCodeTiming {
  expiresInSeconds?: number
  resendAfterSeconds?: number
}
export function formatRemaining(seconds: number) {
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}
export function useVerificationTimer() {
  const now = ref(Date.now()),
    codeDeadline = ref(0),
    resendDeadline = ref(0),
    verifiedDeadline = ref(0)
  const tick = () => {
    now.value = Date.now()
  }
  const interval = setInterval(tick, 250)
  onBeforeUnmount(() => clearInterval(interval))
  const remaining = (deadline: number) => Math.max(0, Math.ceil((deadline - now.value) / 1000))
  const codeSeconds = computed(() => remaining(codeDeadline.value)),
    resendSeconds = computed(() => remaining(resendDeadline.value)),
    verifiedSeconds = computed(() => remaining(verifiedDeadline.value))
  const duration = (value: unknown, fallback: number) =>
    typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : fallback
  function issued(timing?: PhoneCodeTiming | null) {
    tick()
    codeDeadline.value = now.value + duration(timing?.expiresInSeconds, 180) * 1000
    resendDeadline.value = now.value + duration(timing?.resendAfterSeconds, 60) * 1000
    verifiedDeadline.value = 0
  }
  // The backend currently exposes only the code/resend durations; its configured
  // validity after verification is 30 minutes (verified-valid-minutes).
  function verified() {
    tick()
    verifiedDeadline.value = now.value + 30 * 60 * 1000
    codeDeadline.value = 0
  }
  function clear() {
    codeDeadline.value = 0
    resendDeadline.value = 0
    verifiedDeadline.value = 0
    tick()
  }
  return { codeSeconds, resendSeconds, verifiedSeconds, issued, verified, clear, refresh: tick }
}
