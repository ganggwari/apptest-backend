import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
export const useApp = defineStore('app', () => {
  const authVersion = ref(0)
  const theme = ref(localStorage.getItem('routel-theme') === 'dark' ? 'dark' : 'light')
  const account = ref<{
    id: string
    name: string
    role: string
    regions?: { id: number; name: string }[]
  } | null>(null)
  const signupNationality = ref<'domestic' | 'foreign'>('domestic')
  const signupCarrier = ref('')
  sessionStorage.removeItem('routel-member-preview')
  const selectedFacility = ref(0)
  const filter = ref('')
  const unread = ref(0)
  const data = ref<Record<string, unknown>>({})
  const faqs = ref<{ question: string; answer: string; category: string }[]>([])
  const notices = ref<
    {
      id: string
      title: string
      body: string
      category: string
      start: string
      end: string
      pinned: boolean
    }[]
  >([])
  const events = ref<{ time: string; facility: string; category: string; content: string }[]>([])
  const bannerAt = ref(Number(sessionStorage.getItem('routel-alert-at')) || 0)
  const roleKey = computed(
    () => `${account.value?.id || 'guest'}:${account.value?.role || 'regional'}`,
  )
  function pageData(page: string, key: string, seed: unknown) {
    const id = `${roleKey.value}:${page}:${key}`
    if (!(id in data.value)) data.value[id] = structuredClone(seed)
    return data.value[id]
  }
  function themeToggle() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('routel-theme', theme.value)
  }
  function record(category: string, content: string, facility = '') {
    events.value.unshift({ time: new Date().toLocaleString('ko-KR'), facility, category, content })
  }
  function receiveUrgent(at: number) {
    if (Number.isFinite(at) && at > 0) {
      bannerAt.value = at
      sessionStorage.setItem('routel-alert-at', String(at))
    }
  }
  function clearAccount() {
    localStorage.removeItem('routel-session-kind')
    authVersion.value++
    account.value = null
    data.value = {}
    faqs.value = []
    notices.value = []
    events.value = []
    selectedFacility.value = 0
    unread.value = 0
    bannerAt.value = 0
    sessionStorage.removeItem('routel-alert-at')
  }
  return {
    authVersion,
    theme,
    account,
    signupNationality,
    signupCarrier,
    selectedFacility,
    filter,
    unread,
    data,
    faqs,
    notices,
    events,
    bannerAt,
    roleKey,
    pageData,
    themeToggle,
    record,
    clearAccount,
    receiveUrgent,
  }
})
