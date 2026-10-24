<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

withDefaults(defineProps<{ required?: boolean; disabled?: boolean; label?: string }>(), {
  label: '인증번호',
})
const value = defineModel<string>({ default: '' })
const digits = ref<string[]>(Array(6).fill(''))
const inputs = ref<HTMLInputElement[]>([])
let smsRequest: AbortController | undefined
let smsTimeout: ReturnType<typeof setTimeout> | undefined
function stopSms() {
  smsRequest?.abort()
  clearTimeout(smsTimeout)
}
onMounted(async () => {
  if (!window.isSecureContext || !('OTPCredential' in window) || !navigator.credentials?.get) return
  smsRequest = new AbortController()
  const request = smsRequest
  smsTimeout = setTimeout(stopSms, 180000)
  try {
    const credential = await navigator.credentials.get({
      otp: { transport: ['sms'] },
      signal: request.signal,
    } as CredentialRequestOptions) as (Credential & { code?: string }) | null
    if (!request.signal.aborted && credential?.code && /^\d{6}$/.test(credential.code)) enter(0, credential.code)
  } catch {
    // Manual entry and keyboard OTP suggestions remain available if consent is declined.
  } finally {
    clearTimeout(smsTimeout)
  }
})
onBeforeUnmount(stopSms)
let publishing = false
watch(value, (next) => {
  if (!publishing && (next || '') !== digits.value.join('')) digits.value = Array.from({ length: 6 }, (_, i) => (next || '').replace(/\D/g, '')[i] || '')
}, { immediate: true, flush: 'sync' })
function publish() {
  publishing = true
  value.value = digits.value.join('')
  publishing = false
}
function enter(index: number, raw: string) {
  const numbers = raw.replace(/\D/g, '').slice(0, 6)
  if (!numbers) digits.value[index] = ''
  else {
    const start = numbers.length === 6 ? 0 : index
    for (let i = 0; i < numbers.length && start + i < 6; i++) digits.value[start + i] = numbers[i]!
    inputs.value[Math.min(start + numbers.length, 5)]?.focus()
  }
  publish()
}
function input(index: number, event: Event) {
  const target = event.target as HTMLInputElement
  enter(index, target.value)
  target.value = digits.value[index] || ''
}
function paste(index: number, event: ClipboardEvent) {
  event.preventDefault()
  enter(index, event.clipboardData?.getData('text') || '')
}
function key(index: number, event: KeyboardEvent) {
  if (event.key === 'Backspace' && !digits.value[index] && index > 0) {
    event.preventDefault()
    digits.value[index - 1] = ''
    publish()
    inputs.value[index - 1]?.focus()
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    inputs.value[index + (event.key === 'ArrowLeft' ? -1 : 1)]?.focus()
  }
}
</script>

<template>
  <span class="verification-code-input" role="group" :aria-label="label">
    <input
      v-for="(_, index) in digits"
      :key="index"
      :ref="el => { if (el) inputs[index] = el as HTMLInputElement }"
      :value="digits[index]"
      :aria-label="`${label} ${index + 1}번째 자리`"
      type="text"
      inputmode="numeric"
      :autocomplete="index === 0 ? 'one-time-code' : 'off'"
      maxlength="6"
      pattern="[0-9]"
      :required="required"
      :disabled="disabled"
      @input="input(index, $event)"
      @paste="paste(index, $event)"
      @keydown="key(index, $event)"
      @focus="($event.target as HTMLInputElement).select()"
    />
  </span>
</template>

<style scoped src="../styles/components/verification-code-input.css"></style>
