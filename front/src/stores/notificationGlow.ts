import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNotificationGlow = defineStore('notification-glow', () => {
  const fresh = ref<Record<string, boolean>>({}),
    bell = ref(false)
  const seen = new Set<string>(),
    timers = new Map<string, ReturnType<typeof setTimeout>>()
  let bellTimer: ReturnType<typeof setTimeout> | undefined
  function mark(kind: string, id: unknown) {
    if (id === undefined || id === null || id === '') return
    const key = `${kind}:${id}`
    if (seen.has(key)) return
    seen.add(key)
    if (seen.size > 2000) seen.delete(seen.values().next().value!)
    fresh.value[key] = true
    timers.set(
      key,
      setTimeout(() => {
        delete fresh.value[key]
        timers.delete(key)
      }, 6000),
    )
    if (kind === 'notification') {
      bell.value = true
      clearTimeout(bellTimer)
      bellTimer = setTimeout(() => {
        bell.value = false
      }, 6000)
    }
  }
  function has(kind: string, id: unknown) {
    return !!fresh.value[`${kind}:${id}`]
  }
  function reset() {
    for (const timer of timers.values()) clearTimeout(timer)
    timers.clear()
    clearTimeout(bellTimer)
    seen.clear()
    fresh.value = {}
    bell.value = false
  }
  return { bell, mark, has, reset }
})
