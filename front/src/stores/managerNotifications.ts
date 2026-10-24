import { defineStore } from 'pinia'
import { ref } from 'vue'
import { backend, asPage } from '../services/backend'
import type { CustomerNotification } from './customerNotifications'
import { useApp } from './app'
export const useManagerNotifications = defineStore('manager-notifications', () => {
  const unread = ref(0),
    recent = ref<CustomerNotification[]>([]),
    loading = ref(false),
    error = ref(''),
    app = useApp()
  let sequence = 0,
    previewSequence = 0,
    eventRevision = 0
  function reset() {
    sequence++
    previewSequence++
    recent.value = []
    loading.value = false
    error.value = ''
    unread.value = 0
  }
  async function refresh() {
    if (app.account?.role !== 'REGIONAL_ADMIN') {
      reset()
      return
    }
    const id = app.account.id,
      version = ++sequence
    try {
      const data = await backend('/api/managers/me/notifications/unread-count')
      if (version === sequence && app.account?.id === id && app.account.role === 'REGIONAL_ADMIN') {
        unread.value = Math.max(0, Number(data?.total) || 0)
        app.unread = unread.value
      }
    } catch {
      /* The notification list shows actionable API errors. */
    }
  }
  async function refreshPreview() {
    if (app.account?.role !== 'REGIONAL_ADMIN') return
    const user = app.account.id,
      version = ++previewSequence,
      revision = eventRevision
    loading.value = true
    error.value = ''
    try {
      const result = await backend('/api/managers/me/notifications?page=1&size=5')
      if (version !== previewSequence || app.account?.id !== user || revision !== eventRevision)
        return
      recent.value = asPage<CustomerNotification>(result).items.slice(0, 5)
    } catch (e) {
      if (version === previewSequence) error.value = (e as Error).message
    } finally {
      if (version === previewSequence) {
        loading.value = false
        if (revision !== eventRevision && app.account?.id === user) void refreshPreview()
      }
    }
    void refresh()
  }
  function ingest(data: Record<string, any>) {
    if (app.account?.role !== 'REGIONAL_ADMIN' || !Number.isSafeInteger(data.id)) return
    eventRevision++
    const previous = recent.value.find((item) => item.id === data.id)
    recent.value = [
      { ...data, read: previous?.read ?? false } as CustomerNotification,
      ...recent.value.filter((item) => item.id !== data.id),
    ].slice(0, 5)
  }
  return { unread, recent, loading, error, refresh, refreshPreview, ingest, reset }
})
