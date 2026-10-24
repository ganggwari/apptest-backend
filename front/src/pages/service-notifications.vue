<script setup lang="ts">
import { useNotificationGlow } from '../stores/notificationGlow'
const notificationGlow = useNotificationGlow()
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  useCustomerNotifications,
  notificationCategories,
  notificationCategory,
  type CustomerNotification,
} from '../stores/customerNotifications'
import { displayDate } from '../utils/display-date'
import { useBackendEvents } from '../composables/useBackendEvents'
import NotificationSettings from '../components/NotificationSettings.vue'
const route = useRoute(),
  notifications = useCustomerNotifications(),
  rows = ref<CustomerNotification[]>([]),
  page = ref(1),
  pages = ref(1),
  total = ref(0),
  category = ref(''),
  unreadOnly = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  expanded = ref<number | null>(null)
const pageSize = computed(() => route.path.startsWith('/customer-app/') ? 20 : 10)
useBackendEvents(({ name, data }) => {
  if (name === 'connected') {
    void refresh()
    return
  }
  if (name !== 'notification-new') return
  notifications.ingest(data)
  if (category.value && category.value !== data.category) return
  if (page.value !== 1) {
    void load()
    return
  }
  const previous = rows.value.find((item) => item.id === data.id)
  if (!previous) {
    total.value++
    pages.value = Math.max(1, Math.ceil(total.value / pageSize.value))
  }
  rows.value = [
    { ...data, read: previous?.read ?? false } as CustomerNotification,
    ...rows.value.filter((item) => item.id !== data.id),
  ].slice(0, pageSize.value)
})
let alive = true,
  sequence = 0
async function load() {
  const version = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await notifications.list({
      page: page.value,
      size: pageSize.value,
      category: category.value,
      unreadOnly: unreadOnly.value,
    })
    if (!alive || version !== sequence) return
    rows.value = result.items
    pages.value = Math.max(1, result.totalPages)
    total.value = result.totalItems
    if (page.value > pages.value) {
      page.value = pages.value
      void load()
    }
  } catch (e) {
    if (alive && version === sequence) error.value = (e as Error).message
  } finally {
    if (alive && version === sequence) loading.value = false
  }
}
async function open(item: CustomerNotification) {
  if (expanded.value === item.id) {
    expanded.value = null
    return
  }
  expanded.value = item.id
  if (!item.read) {
    try {
      await notifications.read(item.id)
      if (alive) item.read = true
    } catch (e) {
      if (alive) error.value = (e as Error).message
    }
  }
}
async function selectFromLink() {
  const id = Number(route.query.notification)
  if (!Number.isSafeInteger(id) || id <= 0) return
  const item = rows.value.find((row) => row.id === id)
  if (item) await open(item)
  else {
    try {
      await notifications.read(id)
    } catch (e) {
      if (alive) error.value = (e as Error).message
    }
  }
}
async function refresh() {
  await Promise.all([load(), notifications.refresh()])
}
async function filter(value: string) {
  category.value = value
  page.value = 1
  expanded.value = null
  await load()
}
async function markAll() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await notifications.readAll(category.value)
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
watch(
  () => route.query.notification,
  () => void selectFromLink(),
)
onMounted(async () => {
  await refresh()
  if (alive) await selectFromLink()
})
onBeforeUnmount(() => {
  alive = false
  sequence++
})
</script>
<template>
  <main class="service-workspace customer-notification-center"
    ><div class="service-heading"
      ><h1>알림 내역</h1
      ><button type="button" :disabled="loading" @click="refresh">새로고침</button></div
    ><details v-if="route.path.startsWith('/customer-app/')" class="notification-settings-details"
      ><summary>알림 수신 설정</summary><NotificationSettings /></details
    ><NotificationSettings v-else /><div class="notification-center-tools"
      ><nav class="notification-category-tabs" aria-label="알림 분류"
        ><button
          v-for="item in notificationCategories"
          :key="item.value"
          type="button"
          :aria-pressed="category===item.value"
          @click="filter(item.value)"
          >{{item.label
          }}<span
            v-if="item.value&&notifications.counts[item.value]"
            >{{notifications.counts[item.value]}}</span
          ></button
        ></nav
      ><div class="notification-center-actions"
        ><label
          ><input v-model="unreadOnly" type="checkbox" @change="page=1;load()" /> 안 읽은 알림만</label
        ><button type="button" :disabled="busy" @click="markAll">모두 읽음</button></div
      ></div
    ><p v-if="error" class="service-error" role="alert">{{error}}</p
    ><p v-if="loading" role="status">알림을 불러오는 중입니다.</p
    ><ul v-else class="notification-center-list"
      ><li
        v-for="item in rows"
        :key="item.id"
        :class="{unread:!item.read,expanded:expanded===item.id,'notification-arrival-glow':notificationGlow.has('notification',item.id)}"
        ><button
          type="button"
          class="notification-center-item"
          :aria-expanded="expanded===item.id"
          @click="open(item)"
          ><span class="notification-center-category">{{notificationCategory(item.category)}}</span
          ><span class="notification-center-title">{{item.title}}</span
          ><span v-if="!item.read" class="notification-unread-dot" aria-label="안 읽음"></span
          ><time>{{displayDate(item.createdAt)}}</time
          ><span aria-hidden="true">{{expanded===item.id?'⌃':'⌄'}}</span></button
        ><p v-if="expanded===item.id" class="notification-center-body">{{item.body}}</p></li
      ><li
        v-if="!rows.length"
        class="notification-center-empty"
        >{{unreadOnly?'안 읽은 알림이 없습니다.':'받은 알림이 없습니다.'}}</li
      ></ul
    ><nav class="service-pagination" aria-label="알림 페이지"
      ><button type="button" :disabled="page<=1||loading" @click="page--;load()">이전</button
      ><span>{{page}} / {{pages}} 페이지 · 전체 {{total}}개</span
      ><button type="button" :disabled="page>=pages||loading" @click="page++;load()"
        >다음</button
      ></nav
    ></main
  >
</template>
<style scoped src="../styles/pages/service-notifications.css"></style>
