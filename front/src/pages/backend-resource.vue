<script setup lang="ts">
import TunnelCctvDialog from '../components/TunnelCctvDialog.vue'
import { useNotificationGlow } from '../stores/notificationGlow'
const notificationGlow = useNotificationGlow()
import RiskLevelBadge from '../components/RiskLevelBadge.vue'
import { formatPhone } from '../utils/phone'
import { dateParts } from '../utils/display-date'
import { riskGroup, matchesRisk, boardCategoryLabel } from '../services/backendCatalog'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import AdminFacilityList from '../components/AdminFacilityList.vue'
import AdminAppFacilityRows from '../components/AdminAppFacilityRows.vue'
import OcrRecordDetail from '../components/OcrRecordDetail.vue'
import AdminReviewCards from '../components/AdminReviewCards.vue'
import TrafficVideo from '../components/TrafficVideo.vue'
import DeadlineTimer from '../components/DeadlineTimer.vue'
import FaqAccordion from '../components/FaqAccordion.vue'
import BackendLayout from '../components/BackendLayout.vue'
import ServiceFields from '../components/ServiceFields.vue'
import {
  backendResources,
  resourceRoutes,
  valueLabels,
  type Field,
  type Action,
} from '../data/backendResources'
import { backend, asPage, queryString, downloadPdf, type PageData } from '../services/backend'
import { useApp } from '../stores/app'
import { ownsFacility } from '../services/facilityAccess'
import { patchFacility, facilityEvents, plateQueueRow } from '../services/streamUpdates'
import type { BackendEvent } from '../composables/useBackendEvents'
import { useManagerNotifications } from '../stores/managerNotifications'
import { useBackendEvents } from '../composables/useBackendEvents'
import { resourceTabs, resourceShortcuts } from '../data/resourceNavigation'
import { alertLogReturn } from '../services/resourceNavigation'
const props = defineProps<{ resourceKey?: string; embedded?: boolean; operationsOnly?: boolean }>()
const emit = defineEmits<{
  saved: [result?: { id: string; status: 'NORMAL' | 'BLOCKED' }]
}>()
const route = useRoute(),
  app = useApp(),
  resourceId = computed(() =>
    String(
      props.resourceKey || route.params.resource || resourceRoutes[route.path] || 'facilities',
    ),
  ),
  resource = computed(() => backendResources[resourceId.value]),
  customer = computed(
    () =>
      resource.value?.owner ||
      ['/c-notice', '/info/faq', '/customer-app/notices', '/customer-app/faq'].includes(route.path),
  )
const restored = computed(() => !customer.value && !route.path.startsWith('/admin-app/'))
const facilityList = computed(() => restored.value && resourceId.value === 'facilities')
const appFacilityList = computed(
  () => route.path === '/admin-app/screen-4' && resourceId.value === 'facilities',
)
const customerService = computed(
  () =>
    restored.value &&
    ['customers', 'inquiries', 'notices', 'faqs', 'alerts', 'manager-notifications'].includes(
      resourceId.value,
    ),
)
const managerNotifications = useManagerNotifications()
async function readAllManagerNotifications() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await backend(
      '/api/managers/me/notifications/read-all' + queryString({ category: filters.category }),
      { method: 'PUT' },
    )
    await Promise.all([load(), managerNotifications.refresh()])
    message.value = '알림을 모두 읽음 처리했습니다.'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
const riskFilter = ref('')
const facilityRows = computed(() =>
  data.value.items.filter((row) => matchesRisk(row.currentRiskLevel, riskFilter.value)),
)
async function moveFaq(row: Record<string, any>, direction: number) {
  if (saving.value || !canWrite.value) return
  const ids = data.value.items.map((item) => Number(item.id)),
    index = ids.indexOf(Number(row.id)),
    next = index + direction
  if (index < 0 || next < 0 || next >= ids.length) return
  ;[ids[index], ids[next]] = [ids[next]!, ids[index]!]
  saving.value = true
  error.value = ''
  try {
    await backend('/api/faqs/order', { method: 'PUT', body: { ids } })
    await load()
    message.value = 'FAQ 표시 순서를 저장했습니다.'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
function reviewTab(status: string) {
  filters.reviewStatus = status
  search()
}
async function review(row: Record<string, any>, number: string) {
  await open('action', row, resource.value.actions?.[0])
  values.correctedNum = number
}
const publicNotices = computed(() => customer.value && resourceId.value === 'notices')
const managerNotificationList = computed(() => resourceId.value === 'manager-notifications')
const pageSize = computed(() => route.path.startsWith('/admin-app/') ? (managerNotificationList.value ? 5 : appFacilityList.value ? 8 : 20) : route.path.startsWith('/customer-app/') ? 20 : 10)
async function markManagerNotificationRead(row: Record<string, any>) {
  if (saving.value || row.read) return
  saving.value = true
  error.value = ''
  try {
    await backend(`/api/managers/me/notifications/${encodeURIComponent(row.id)}/read`, { method: 'PUT' })
    row.read = true
    await managerNotifications.refresh()
    if (filters.unreadOnly) await load()
  } catch (e) { error.value = (e as Error).message }
  finally { saving.value = false }
}
const readNotices = ref<Record<string, string>>({})
const noticeReadKey = computed(
  () => `routel-read-notices:${app.account?.role || 'guest'}:${app.account?.id || 'guest'}`,
)
watch(
  noticeReadKey,
  (key) => {
    try {
      const stored = JSON.parse(localStorage.getItem(key) || '{}')
      readNotices.value =
        stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {}
    } catch {
      readNotices.value = {}
    }
  },
  { immediate: true },
)
function noticeVersion(row: Record<string, any>) {
  return String(row.updatedAt || row.createdAt || 'read')
}
function noticeUnread(row: Record<string, any>) {
  return publicNotices.value && readNotices.value[String(row.id)] !== noticeVersion(row)
}
function markNoticeRead(row: Record<string, any>) {
  readNotices.value = { ...readNotices.value, [String(row.id)]: noticeVersion(row) }
  try {
    localStorage.setItem(noticeReadKey.value, JSON.stringify(readNotices.value))
  } catch {
    /* Reading still works when browser storage is unavailable. */
  }
}
async function loadCollection(signal: AbortSignal) {
  const fetchPage = async (current: number) =>
    asPage<Record<string, any>>(
      await backend(
        path(resource.value.path) + queryString({ ...filters, page: current, size: 100 }),
        { signal },
      ),
      current,
      100,
    )
  const first = await fetchPage(1),
    items = [...first.items]
  for (let current = 2; current <= first.totalPages; current++)
    items.push(...(await fetchPage(current)).items)
  if (['notices', 'faqs'].includes(resourceId.value)) {
    const views = (row: Record<string, any>) => {
      const count = Number(row.viewCount)
      return Number.isFinite(count) ? Math.max(0, count) : 0
    }
    const important = (row: Record<string, any>) =>
      row.important === true || row.important === 'true' || row.important === 1
    items.sort((a, b) =>
      (resourceId.value === 'notices' ? Number(important(b)) - Number(important(a)) : 0) ||
      views(b) - views(a),
    )
  }
  if ((facilityList.value || appFacilityList.value) && riskFilter.value) {
    const selected = items.filter((row) => matchesRisk(row.currentRiskLevel, riskFilter.value))
    items.splice(0, items.length, ...selected)
  }
  const size = pageSize.value
  const totalPages = Math.ceil(items.length / size)
  page.value = Math.min(page.value, Math.max(1, totalPages))
  return {
    items: items.slice((page.value - 1) * size, page.value * size),
    page: page.value,
    size,
    totalItems: items.length,
    totalPages,
  }
}
const visibleColumns = computed(() =>
  publicNotices.value
    ? resource.value.columns.filter(([key]) => key !== 'important')
    : resource.value.columns,
)
const pageTitle = computed(() =>
  customer.value && resourceId.value === 'notices'
    ? '공지사항'
    : customer.value && resourceId.value === 'faqs'
      ? '자주 묻는 질문 (FAQ)'
      : resource.value.title,
)
function displayField(key: string, value: any) {
  if (['phone', 'emergency'].includes(key)) return formatPhone(value) || '—'
  return resourceId.value === 'notices' && key === 'important'
    ? value
      ? '중요 공지'
      : '일반 공지'
    : display(value)
}
const relatedIds = computed(() => resourceTabs(resourceId.value)),
  shortcuts = computed(() => resourceShortcuts[resourceId.value] || [])
function resourceLink(id: string) {
  const appPaths: Record<string, string> = {
    ocr: '/admin-app/ocr',
    reviews: '/admin-app/reviews',
    transits: '/admin-app/transits',
    facilities: '/admin-app/screen-4',
    alerts: '/admin-app/screen-13',
    'manager-notifications': '/admin-app/notifications',
  }
  const paths: Record<string, string> = {
    facilities: '/fac-list',
    reviews: '/verify',
    events: '/logs',
    alerts: '/notifications',
  }
  const destination =
    route.path.startsWith('/admin-app/') && appPaths[id]
      ? appPaths[id]
      : paths[id] || '/admin-data/' + id
  const supported = new Set(
    [...(backendResources[id]?.filters || []), ...(backendResources[id]?.parents || [])].map(
      (field) => field.key,
    ),
  )
  const query: Record<string, string> = {}
  for (const key of ['underpassId', 'regionId', 'carNum']) {
    const value = parents[key] || filters[key]
    if (supported.has(key) && value !== undefined && value !== null && value !== '')
      query[key] = String(value)
  }
  return { path: destination, query }
}

const canWrite = computed(() =>
  resource.value?.owner
    ? app.account?.role === 'customer'
    : !customer.value && app.account?.role === 'REGIONAL_ADMIN',
)
const hasRowActions = computed(
  () =>
    !publicNotices.value &&
    !!(
      resource.value.detail ||
      (canWrite.value &&
        (resource.value.edit || resource.value.remove || resource.value.actions?.length)) ||
      ['facilities', 'cameras'].includes(resourceId.value)
    ),
)
const filters = reactive<Record<string, any>>({}),
  parents = reactive<Record<string, any>>({}),
  choices = reactive<Record<string, { value: string; label: string }[]>>({}),
  values = reactive<Record<string, any>>({})
const acknowledge = ref(false)
const data = ref<PageData<Record<string, any>>>({
    items: [],
    page: 1,
    size: pageSize.value,
    totalItems: 0,
    totalPages: 0,
  }),
  page = ref(1),
  loading = ref(false),
  error = ref(''),
  message = ref(''),
  saving = ref(false),
  modal = ref<HTMLDialogElement>(),
  selected = ref<Record<string, any> | null>(null),
  mode = ref(''),
  formFields = ref<Field[]>([]),
  action = ref<Action>(),
  detail = ref<Record<string, any>>({})
let abort: AbortController | undefined,
  request = 0,
  alive = true
function path(template: string, row = selected.value) {
  return template.replace(/\{(\w+)\}/g, (_, key) =>
    encodeURIComponent(
      String(
        key === 'id' ? row?.[resource.value.rowKey || 'id'] : (parents[key] ?? row?.[key] ?? ''),
      ),
    ),
  )
}
const missingParent = computed(() =>
  resource.value?.parents?.some((field) => field.required && !parents[field.key]),
)
async function lookup(fields: Field[], row?: Record<string, any>) {
  const scope = resourceId.value
  for (const field of fields) {
    if (!field.source) continue
    let url =
      field.source === 'underpasses'
        ? '/api/underpasses?size=500'
        : field.source === 'regions'
          ? '/api/regions'
          : field.source === 'shifts'
            ? '/api/shifts'
            : field.source === 'presets'
              ? '/api/display/presets'
              : field.source === 'reasons'
                ? '/api/control/reason-presets' +
                  (action.value?.path.endsWith('/release')
                    ? '?type=RELEASE'
                    : action.value?.path.endsWith('/block')
                      ? '?type=BLOCK'
                      : '')
                : `/api/underpasses/${row?.id || parents.underpassId}/cameras`
    if (field.source === 'cameras' && !row?.id && !parents.underpassId) continue
    try {
      const result = await backend(url)
      if (alive && scope === resourceId.value && (!row || selected.value?.id === row.id))
        choices[field.key] = asPage<any>(result).items.map((item) => ({
          value: String(item.id),
          label: item.name || item.content || `${item.cameraRole} · ${item.id}`,
        }))
    } catch {
      /* Main list retains its own request error. */
    }
  }
}
async function load() {
  if (props.operationsOnly) return
  if (!resource.value || missingParent.value) return
  abort?.abort()
  abort = new AbortController()
  const seq = ++request
  loading.value = true
  error.value = ''
  try {
    if (filters.startDate && filters.endDate && filters.startDate > filters.endDate)
      throw new Error('시작일은 종료일보다 늦을 수 없습니다.')
    const result =
      ['notices', 'faqs'].includes(resourceId.value) || managerNotificationList.value || facilityList.value || appFacilityList.value
        ? await loadCollection(abort.signal)
        : await backend(
            path(resource.value.path) + queryString({ ...filters, page: page.value, size: pageSize.value }),
            { signal: abort.signal },
          )
    if (seq === request) {
      if (Array.isArray(result)) {
        const size=pageSize.value, totalPages=Math.ceil(result.length/size)
        page.value=Math.min(page.value,Math.max(1,totalPages))
        data.value={items:result.slice((page.value-1)*size,page.value*size),page:page.value,size,totalItems:result.length,totalPages}
      } else data.value = asPage(result, page.value, pageSize.value)
    }
  } catch (e) {
    if (seq === request && !abort.signal.aborted) error.value = (e as Error).message
  } finally {
    if (seq === request) loading.value = false
  }
}
function imageUrl(value: string) {
  if (!value) return ''
  const parts = value.replace(/^\/api\/uploads\//, '').split(/[\\/]/)
  if (parts.some((part) => part === '..' || part === '.') || value.includes(':')) return ''
  return '/api/uploads/' + parts.filter(Boolean).map(encodeURIComponent).join('/')
}
function display(value: any) {
  if (value == null || value === '') return '—'
  if (typeof value === 'boolean') return value ? '예' : '아니요'
  if (Array.isArray(value))
    return value.map((item) => (typeof item === 'object' ? item.name || item.id : item)).join(', ')
  if (typeof value === 'object') return value.name || '—'
  const timestamp = dateParts(value)
  if (timestamp) return `${timestamp.date}${timestamp.time ? ' ' + timestamp.time : ''}`
  return valueLabels[String(value)] || String(value).replace('T', ' ')
}
function canEditRow(row: Record<string, any>) {
  return (
    resource.value.edit &&
    canWrite.value &&
    (resourceId.value !== 'my-inquiries' || row.status === 'PENDING') &&
    (resourceId.value !== 'facilities' || ownsFacility(row))
  )
}
function actionEnabled(row: Record<string, any>) {
  return resourceId.value === 'facilities'
    ? ownsFacility(row)
    : resourceId.value === 'manager-notifications'
      ? !row.read
      : resourceId.value === 'transits'
        ? row.status !== 'EXITED'
        : true
}
function openDialog() {
  if (modal.value && !modal.value.open) modal.value.showModal()
}
let summaryBackdropDown = false
function isSummaryBackdrop(event: MouseEvent | PointerEvent) {
  const dialog = modal.value
  if (!dialog || event.target !== dialog || mode.value !== 'detail' || saving.value) return false
  const rect = dialog.getBoundingClientRect()
  return event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom
}
function closeSummaryBackdrop(event: MouseEvent) {
  if (summaryBackdropDown && isSummaryBackdrop(event)) modal.value?.close()
  summaryBackdropDown = false
}
async function open(kind: string, row?: Record<string, any>, itemAction?: Action) {
  if (resource.value.detailFromRow && row)
    row = data.value.items.find((item) => String(item.id) === String(row?.id)) || row
  if (!['detail', 'video'].includes(kind) && !canWrite.value) {
    error.value = '이 작업을 수행할 권한이 없습니다.'
    return
  }
  acknowledge.value = false
  selected.value = row || null
  mode.value = kind
  action.value = itemAction
  error.value = ''
  message.value = ''
  Object.keys(values).forEach((key) => delete values[key])
  detail.value = row || {}
  formFields.value = (
    kind === 'remove' || kind === 'detail' || kind === 'video'
      ? []
      : kind === 'action'
        ? itemAction?.fields || []
        : resource.value.fields || []
  ).filter((field) => kind !== 'edit' || resourceId.value !== 'notices' || field.key !== 'sendPush')
  try {
    if (row && resource.value.detail && !resource.value.detailFromRow) {
      detail.value = await backend(`${path(resource.value.path)}/${encodeURIComponent(row.id)}`)
    }
    if (resourceId.value === 'my-inquiries' && kind === 'edit' && detail.value.status !== 'PENDING')
      throw new Error('답변이 등록된 문의는 수정할 수 없습니다.')
    if (
      resourceId.value === 'facilities' &&
      ['action', 'edit', 'remove'].includes(kind) &&
      !ownsFacility(detail.value)
    )
      throw new Error('담당 외 시설은 통제할 수 없습니다.')
    for (const field of formFields.value) {
      const value =
        field.key === 'category' && detail.value[field.key] == null
          ? resourceId.value === 'notices'
            ? 'INFO'
            : resourceId.value === 'faqs'
              ? 'ETC'
              : ''
          : field.key === 'eventId'
            ? detail.value.currentBlockEventId
            : field.key === 'content' && resourceId.value === 'inquiries' && kind === 'action'
              ? itemAction?.method === 'POST'
                ? ''
                : detail.value.replyContent
              : field.key === 'correctedNum'
                ? detail.value.correctedNum || detail.value.detectedNum
                : detail.value[field.key]
      values[field.key] =
        field.type === 'multi'
          ? Array.isArray(value)
            ? value.map((v) => String(v.id || v))
            : []
          : field.type === 'checkbox'
            ? Boolean(value)
            : Array.isArray(value)
              ? value.join('\n')
              : value == null
                ? ''
                : String(value)
    }
    if (kind === 'create' && !values.regionId && formFields.value.some(field => field.key === 'regionId') &&
        app.account?.role === 'REGIONAL_ADMIN' && app.account.regions?.length)
      values.regionId = String(app.account.regions[0]!.id)
    void lookup(formFields.value, row)
    openDialog()
    if (publicNotices.value && kind === 'detail' && row) {
      markNoticeRead(row)
      if (typeof detail.value.viewCount === 'number') row.viewCount = detail.value.viewCount
    }
  } catch (e) {
    error.value = (e as Error).message
  }
}
function body() {
  const result: Record<string, unknown> = {}
  for (const field of formFields.value) {
    const value = values[field.key]
    if (field.type === 'multi') result[field.key] = (value || []).map(Number)
    else if (field.type === 'lines')
      result[field.key] = String(value || '')
        .split(/\n/)
        .map((v) => v.trim())
        .filter(Boolean)
    else if (field.type === 'checkbox') result[field.key] = Boolean(value)
    else if (field.type === 'number' || field.source)
      result[field.key] = value === '' ? null : Number(value)
    else result[field.key] = value === '' ? null : value
  }
  return result
}
async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    if (
      resourceId.value === 'my-inquiries' &&
      mode.value === 'edit' &&
      detail.value.status !== 'PENDING'
    )
      throw new Error('답변이 등록된 문의는 수정할 수 없습니다.')
    if (!canWrite.value) throw new Error('이 작업을 수행할 권한이 없습니다.')
    if (
      resourceId.value === 'facilities' &&
      ['action', 'edit', 'remove'].includes(mode.value) &&
      !ownsFacility(detail.value)
    )
      throw new Error('담당 외 시설은 통제할 수 없습니다.')
    if (
      resourceId.value === 'display' &&
      mode.value !== 'remove' &&
      !values.presetId &&
      !String(values.content || '').trim()
    )
      throw new Error('문구를 선택하거나 입력해 주세요.')
    if (resourceId.value === 'customers' && mode.value === 'edit' && !acknowledge.value)
      throw new Error('주소·비상 연락처 교체 내용을 확인해 주세요.')
    let url = path(resource.value.path),
      method = 'POST',
      pdf = false,
      payload: unknown = body()
    if (mode.value === 'edit') {
      url += `/${encodeURIComponent(selected.value?.[resource.value.rowKey || 'id'])}`
      method = 'PUT'
    }
    if (mode.value === 'remove') {
      url += `/${encodeURIComponent(selected.value?.[resource.value.rowKey || 'id'])}`
      method = 'DELETE'
      payload = undefined
    }
    if (mode.value === 'action' && action.value) {
      url = path(action.value.path)
      method = action.value.method || 'POST'
      pdf = !!action.value.pdf
      if (!formFields.value.length) payload = undefined
    }
    if (resourceId.value === 'facilities' && mode.value === 'action' &&
        /\/(block|release)$/.test(url)) {
      const control = payload as Record<string, unknown>
      const reason = String(control.reason || '').trim()
      if (!reason && !control.presetId) throw new Error('사유를 선택하거나 직접 입력해 주세요.')
      // The server prioritizes presetId; clear it when a manual reason was supplied.
      payload = { ...control, reason: reason || null, presetId: reason ? null : control.presetId }
    }
    const result = await backend(url, { method, body: payload, pdf })
    const controlStatus =
      resourceId.value === 'facilities' && mode.value === 'action'
        ? action.value?.path.endsWith('/block')
          ? 'BLOCKED'
          : action.value?.path.endsWith('/release')
            ? 'NORMAL'
            : undefined
        : undefined
    if (controlStatus) {
      const confirmed: { id: string; status: 'NORMAL' | 'BLOCKED' } = {
        id: String(selected.value?.id),
        status: controlStatus,
      }
      // A successful control response is authoritative; do not wait for another SSE or GET.
      window.dispatchEvent(
        new CustomEvent<BackendEvent>('routel-backend-event', {
          detail: {
            name: 'control-change',
            data: {
              underpassId: confirmed.id,
              status: controlStatus,
              changedAt: new Date().toISOString(),
            },
          },
        }),
      )
      emit('saved', confirmed)
    }
    if (pdf) downloadPdf(result, `이관문서-${selected.value?.id || '시설'}.pdf`)
    if (resourceId.value === 'my-inquiries' && mode.value === 'create')
      sessionStorage.removeItem('routel-inquiry-draft')
    modal.value?.close()
    message.value = '요청이 처리되었습니다.'
    await load()
    if (resourceId.value === 'manager-notifications') await managerNotifications.refresh()
    if (
      resourceId.value === 'managers' &&
      String(selected.value?.id) === app.account?.id &&
      action.value?.path.endsWith('/regions') &&
      Array.isArray(result?.regions)
    )
      app.account = { ...app.account, regions: result.regions }
    if (!controlStatus) emit('saved')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
function search() {
  page.value = 1
  void load()
}
function navigatePage(next: number) {
  page.value = next
  void load()
}
const labels = computed(() =>
  Object.fromEntries([
    ...resource.value.columns,
    ...(resource.value.fields || []).map((field) => [field.key, field.label]),
    ['content', '내용'],
    ['answer', '답변'],
    ['replyContent', '답변 내용'],
    ['replyAuthorName', '답변자'],
    ['repliedAt', '답변일'],
    ['roadAddress', '주소'],
    ['category', '분류'],
    ['viewCount', '조회수'],
    ['currentWaterCm', '현재 수위(cm)'],
    ['currentRiskScore', '현재 위험 점수'],
    ['manageable', '담당 시설'],
    ['sortOrder', '표시 순서'],
  ]),
)
watch(
  () => route.fullPath,
  () => {
    abort?.abort()
    request++
    modal.value?.close()
    for (const obj of [filters, parents, choices])
      Object.keys(obj).forEach((key) => delete obj[key])
    page.value = 1
    data.value = { items: [], page: 1, size: pageSize.value, totalItems: 0, totalPages: 0 }
    error.value = ''
    if (!resource.value) {
      error.value = '페이지를 찾을 수 없습니다.'
      return
    }
    for (const field of resource.value.parents || [])
      parents[field.key] = route.query[field.key] || ''
    for (const field of resource.value.filters || [])
      filters[field.key] =
        field.type === 'checkbox' ? route.query[field.key] === 'true' : route.query[field.key] || ''
    if (app.account?.role === 'REGIONAL_ADMIN' && app.account.regions?.length === 1 &&
        resource.value.filters?.some(field => field.key === 'regionId') &&
        route.query.regionId == null && route.query.underpassId == null)
      filters.regionId = String(app.account.regions[0]!.id)
    if (resourceId.value === 'alerts' && alertLogReturn.path) {
      Object.assign(filters, alertLogReturn.filters)
      page.value = alertLogReturn.page
    }
    if (props.operationsOnly) return
    if (resourceId.value === 'reviews' && !filters.reviewStatus) filters.reviewStatus = 'PENDING'
    void lookup([...(resource.value.parents || []), ...(resource.value.filters || [])])
    void load().then(() => {
      if (!props.embedded && route.query.id && resource.value.detail && resourceId.value !== 'faqs')
        void open('detail', { id: Number(route.query.id) })
      else if (route.path === '/my-inquiry-new')
        void open('create').then(() => {
          try {
            const draft = JSON.parse(sessionStorage.getItem('routel-inquiry-draft') || 'null')
            if (draft) {
              values.title = draft.title || ''
              values.content = draft.content || ''
            }
          } catch {}
        })
    })
  },
  { immediate: true },
)
let liveRefresh: ReturnType<typeof setTimeout> | undefined,
  liveRefreshPending = false
function queueLiveRefresh() {
  if (props.operationsOnly) return
  if (modal.value?.open && mode.value !== 'detail') {
    liveRefreshPending = true
    return
  }
  clearTimeout(liveRefresh)
  liveRefresh = setTimeout(async () => {
    if (!alive) return
    await load()
    if (resourceId.value === 'ocr' && mode.value === 'detail' && selected.value) {
      const row = data.value.items.find((item) => item.id === selected.value?.id)
      if (row) detail.value = row
    }
    if (
      alive &&
      modal.value?.open &&
      mode.value === 'detail' &&
      resource.value.detail &&
      !resource.value.detailFromRow &&
      selected.value
    ) {
      const id = selected.value.id
      try {
        const result = await backend(`${path(resource.value.path)}/${encodeURIComponent(id)}`)
        if (alive && mode.value === 'detail' && selected.value?.id === id) detail.value = result
      } catch (e) {
        if (alive) error.value = (e as Error).message
      }
    }
  }, 200)
}
watch(mode, (value) => {
  if (!value && liveRefreshPending) {
    liveRefreshPending = false
    queueLiveRefresh()
  }
})
useBackendEvents((event) => {
  const { name, data: payload } = event
  const keys: Record<string, string> = {
    facilities: 'underpass',
    cameras: 'camera',
    thresholds: 'threshold',
    transfers: 'fire-transfer',
    notices: 'notice',
    faqs: 'faq',
    inquiries: 'inquiry',
    'my-inquiries': 'inquiry',
    reviews: 'plate-log',
    ocr: 'plate-log',
  }
  if (props.operationsOnly) return
  if (name === 'connected') {
    queueLiveRefresh()
    return
  }
  if (resourceId.value === 'facilities' && facilityEvents.includes(name)) {
    data.value.items = data.value.items.map((row) => patchFacility(row, event))
    if (mode.value === 'detail') detail.value = patchFacility(detail.value, event)
    return
  }
  if (resourceId.value === 'ocr' && name.startsWith('plate-queue-')) {
    queueLiveRefresh()
    return
  }
  if (resourceId.value === 'reviews' && name.startsWith('plate-queue-')) {
    const id = payload.plateLogId,
      index = data.value.items.findIndex((row) => row.id === id)
    if (name === 'plate-queue-done') {
      if (filters.reviewStatus === 'PENDING') {
        if (index >= 0) {
          data.value.items.splice(index, 1)
          data.value.totalItems = Math.max(0, data.value.totalItems - 1)
        }
      } else if (index >= 0)
        data.value.items[index] = plateQueueRow(payload, data.value.items[index])
    } else if (
      (!filters.underpassId || String(filters.underpassId) === String(payload.underpassId)) &&
      (!filters.reviewStatus || filters.reviewStatus === 'PENDING')
    ) {
      if (index >= 0) data.value.items[index] = plateQueueRow(payload, data.value.items[index])
      else if (page.value === 1 && !filters.regionId && !filters.recognitionStatus) {
        data.value.items.unshift(plateQueueRow(payload))
        data.value.items = data.value.items.slice(0, pageSize.value)
        data.value.totalItems++
      }
      queueLiveRefresh()
    }
    return
  }
  if (resourceId.value === 'manager-notifications' && name === 'notification-new') {
    if (filters.category && filters.category !== payload.category) return
    if (page.value !== 1) {
      queueLiveRefresh()
      return
    }
    const previous = data.value.items.find((row) => row.id === payload.id)
    if (!previous) data.value.totalItems++
    data.value.items = [
      { ...payload, read: previous?.read ?? false },
      ...data.value.items.filter((row) => row.id !== payload.id),
    ].slice(0, pageSize.value)
    data.value.totalPages = Math.ceil(data.value.totalItems / pageSize.value)
    return
  }
  if (name === 'data-changed' && keys[resourceId.value] === payload.resource) {
    queueLiveRefresh()
    return
  }
  if (
    (resourceId.value === 'notices' && name === 'notice-new') ||
    (resourceId.value === 'inquiries' && name === 'inquiry-new') ||
    (resourceId.value === 'transits' && name === 'transit-update') ||
    (resourceId.value === 'events' && name === 'control-change') ||
    (resourceId.value === 'display' &&
      name === 'display-change' &&
      String(parents.equipId) === String(payload.equipId)) ||
    (resourceId.value === 'alerts' &&
      ['risk-level-change', 'control-change', 'notification-new'].includes(name))
  )
    queueLiveRefresh()
})
onBeforeRouteLeave(() => {
  if (resourceId.value === 'alerts') {
    alertLogReturn.path = route.fullPath
    alertLogReturn.filters = { ...filters }
    alertLogReturn.page = page.value
  }
})
onBeforeUnmount(() => {
  clearTimeout(liveRefresh)
  alive = false
  abort?.abort()
  modal.value?.close()
})
defineExpose({ open, load })
</script>
<template>
  <component :is="customer||props.embedded?'div':BackendLayout">
    <section
      v-if="resource"
      class="service-workspace"
      :class="{'restored-admin-workspace':restored,'resource-operations-only':props.operationsOnly}"
    >
      <RouterLink
        v-if="!customer&&resourceId==='faqs'&&alertLogReturn.path"
        :to="alertLogReturn.path"
        class="alert-log-return"
      >← 알림 발송 기록으로 돌아가기</RouterLink>
      <div v-if="restored" class="crumb">{{customerService?'고객 서비스':pageTitle}}</div>
      <div class="service-heading page-head">
        <component :is="props.embedded?'h2':'h1'">{{pageTitle}}</component>
        <div class="service-actions">
          <button
            v-if="resourceId==='manager-notifications'"
            type="button"
            :disabled="saving"
            @click="readAllManagerNotifications"
          >모두 읽음</button>
          <button type="button" :disabled="loading||missingParent" @click="load">새로고침</button>
          <button v-if="resource.create&&canWrite" type="button" @click="open('create')">
            {{resourceId==='cars'?'차량 등록':resourceId==='my-inquiries'?'문의 등록':facilityList?'+ 지하차도 등록':'새로 등록'}}
          </button>
        </div>
      </div>
      <nav v-if="publicNotices" class="notice-category-tabs" aria-label="공지사항 분류">
        <button
          v-for="[label,value] in [['전체',''],['중요','true'],['일반','false']]"
          :key="label"
          type="button"
          :aria-pressed="String(filters.important||'')===value"
          @click="filters.important=value;search()"
        >
          {{label}}
        </button>
      </nav>
      <nav
        v-if="!customer&&!props.embedded&&relatedIds.length>1"
        class="service-tabs tabs"
        aria-label="업무 구분"
      >
        <RouterLink
          v-for="id in relatedIds"
          :key="id"
          :to="resourceLink(id)"
          :aria-current="resourceId===id?'page':undefined"
        >
          {{backendResources[id]?.title}}
        </RouterLink>
      </nav>
      <nav v-if="restored&&resourceId==='reviews'" class="tabs review-tabs" aria-label="판별 구분">
        <button
          type="button"
          :aria-pressed="filters.reviewStatus==='PENDING'"
          @click="reviewTab('PENDING')"
        >대기</button>
        <button
          type="button"
          :aria-pressed="filters.reviewStatus!=='PENDING'"
          @click="reviewTab('')"
        >처리 이력</button>
      </nav>
      <form
        v-if="facilityList||appFacilityList"
        class="filters restored-facility-filters"
        role="search"
        @submit.prevent="search"
      >
        <div class="chips" role="group" aria-label="위험 단계">
          <button
            v-for="[value,label] in [['','전체'],['DANGER','위험'],['WARNING','주의'],['SAFE','안전']]"
            :key="value"
            class="chip"
            type="button"
            :aria-pressed="riskFilter===value"
            @click="riskFilter=value;search()"
          >
            {{label}}
          </button>
        </div>
        <select v-model="filters.regionId" aria-label="담당 지역" @change="search">
          <option value="">담당 지역 전체</option>
          <option v-for="option in choices.regionId" :key="option.value" :value="option.value">
            {{option.label}}
          </option>
        </select>
        <select v-model="filters.status" aria-label="통제 상태" @change="search">
          <option value="">통제 상태 전체</option>
          <option value="BLOCKED">통제 중</option>
          <option value="NORMAL">정상 운영</option>
        </select>
        <input
          v-model="filters.keyword"
          type="search"
          placeholder="지하차도 이름 검색"
          aria-label="지하차도 이름 검색"
        />
        <button class="btn sm" type="submit">조회</button>
      </form>
      <form v-else-if="managerNotificationList" class="manager-notification-filters" @submit.prevent="search">
        <label>분류<select v-model="filters.category" @change="search"><option value="">전체 알림</option><option v-for="[value,label] in resource.filters?.[0]?.options||[]" :key="value" :value="value">{{label}}</option></select></label>
        <label class="manager-unread-filter"><input v-model="filters.unreadOnly" type="checkbox" @change="search" />안 읽은 알림만</label>
      </form>
      <form
        v-else-if="!customer||resource.parents?.length||resource.filters?.length"
        class="service-filters filters"
        @submit.prevent="search"
        @change="customer&&search()"
      >
        <ServiceFields
          v-if="resource.parents?.length"
          :fields="resource.parents"
          :values="parents"
          :choices="choices"
        />
        <ServiceFields
          v-if="resource.filters?.length"
          :fields="resource.filters"
          :values="filters"
          :choices="choices"
        />
        <button v-if="!customer" type="submit" :disabled="loading">
          {{resourceId==='my-inquiries'?'검색':'조회'}}
        </button>
      </form>
      <nav
        v-if="!customer&&!props.embedded&&shortcuts.length"
        class="resource-shortcuts"
        aria-label="관련 업무 바로가기"
      >
        <RouterLink v-for="link in shortcuts" :key="link.id" :to="resourceLink(link.id)">
          {{link.label}} →</RouterLink>
      </nav>
      <p v-if="error" class="service-error" role="alert">
        {{error}} <RouterLink v-if="!app.account" :to="customer?'/login':'/admin-login'">로그인</RouterLink>
      </p>
      <p v-if="message" class="service-message" role="status">{{message}}</p>
      <p v-if="missingParent">위에서 {{resource.parents?.map(field=>field.label).join(' · ')}}를 선택하고 조회하세요.</p>
      <p v-else-if="loading" role="status">불러오는 중입니다.</p>
      <template v-else>
        <p class="service-result-count" role="status">총 {{data.totalItems}}건</p>
        <AdminAppFacilityRows v-if="appFacilityList" :items="facilityRows" />
        <ul v-else-if="managerNotificationList" class="manager-notification-cards" aria-label="관리자 알림 목록">
          <li v-for="row in data.items" :key="row.id" :class="{'is-unread':!row.read}">
            <div class="manager-notification-meta"><span class="manager-notification-category">{{valueLabels[row.category]||'알림'}}</span><span v-if="!row.read" class="manager-notification-unread">● 안 읽음</span><time>{{display(row.createdAt)}}</time></div>
            <h2>{{row.title}}</h2><p>{{row.body}}</p>
            <button v-if="!row.read" type="button" :disabled="saving" @click="markManagerNotificationRead(row)">읽음 처리</button><small v-else class="manager-notification-read">✓ 읽음</small>
          </li>
          <li v-if="!data.items.length" class="manager-notification-empty">표시할 알림이 없습니다.</li>
        </ul>
        <AdminFacilityList
          v-else-if="facilityList"
          :items="facilityRows"
          :total="data.totalItems"
          @detail="row=>open('detail',row)"
          @control="row=>open('action',row,resource.actions?.[row.currentStatus==='BLOCKED'?1:0])"
        />
        <AdminReviewCards
          v-else-if="restored&&resourceId==='reviews'&&filters.reviewStatus==='PENDING'"
          :items="data.items.filter(row=>row.reviewStatus==='PENDING')"
          :saving="saving"
          @review="review"
        />
        <FaqAccordion
          v-else-if="resourceId==='faqs'"
          :items="data.items"
          :manage="canWrite"
          :allow-reorder="false"
          :initial-open-id="String(route.query.id||'')"
          :saving="saving"
          @move="moveFaq"
          @edit="row=>open('edit',row)"
          @remove="row=>open('remove',row)"
        />
        <div v-else class="service-table-wrap">
          <table class="service-table">
            <thead>
              <tr>
                <th v-for="[key,label] in visibleColumns" :key="key">{{label}}</th>
                <th v-if="hasRowActions">상세·처리</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row,index) in data.items"
                :key="row.id||index"
                :class="{'notification-arrival-glow':notificationGlow.has(resourceId==='manager-notifications'?'notification':resourceId==='notices'?'notice':resourceId==='inquiries'?'inquiry':'',row.id)}"
              >
                <td v-for="[key,label] in visibleColumns" :key="key" :data-label="label">
                  <template v-if="resourceId==='notices'&&key==='title'">
                    <button
                      type="button"
                      class="notice-title-button"
                      :class="{'notice-unread':noticeUnread(row)}"
                      @click="open('detail',row)"
                    >
                      <span :class="row.important?'notice-important-badge':'notice-regular-label'">
                        {{row.important?'중요':'일반'}}
                      </span>
                      <span>{{row.title}}</span>
                    </button>
                  </template>
                  <template v-else-if="resourceId==='notices'&&key==='important'">
                    <span :class="row.important?'notice-important-badge':'notice-regular-label'">
                      {{row.important?'중요':'일반'}}
                    </span>
                  </template>
                  <img
                    v-else-if="key==='imagePath'&amp;&amp;imageUrl(row[key])&amp;&amp;!row.imageUnavailable"
                    :src="imageUrl(row[key])"
                    alt="번호판 인식 이미지"
                    loading="lazy"
                    @error="row.imageUnavailable=true"
                  />
                  <template
                    v-else-if="key==='reviewDeadline'&&row.reviewStatus==='PENDING'&&(row[key]||row.reviewExpiresAt)"
                  >
                    <DeadlineTimer :deadline="row.reviewExpiresAt??row[key]" />
                    <br />
                    <small>{{display(row[key])}}</small>
                  </template>
                  <template v-else-if="restored&&resourceId==='customers'&&key==='name'">
                    <button type="button" class="notice-title-button" @click="open('detail',row)">
                      {{row.name}}
                    </button>
                  </template>
                  <template v-else>
                    {{key==='imagePath'?'이미지 없음':displayField(key,row[key])}}
                  </template>
                </td>
                <td v-if="hasRowActions">
                  <div class="service-actions">
                    <button
                      v-if="resourceId==='cameras'&amp;&amp;row.streamUrl"
                      @click="open('video',row)"
                    >영상 보기</button>
                    <button v-if="resource.detail" @click="open('detail',row)">상세 보기</button>
                    <button v-if="canEditRow(row)" @click="open('edit',row)">수정</button>
                    <button v-if="resource.remove&&canWrite" @click="open('remove',row)">삭제</button>
                    <button
                      v-for="act in canWrite?resource.actions||[]:[]"
                      :key="act.label"
                      :disabled="!actionEnabled(row)"
                      @click="open('action',row,act)"
                    >
                      {{act.label}}
                    </button>
                    <RouterLink
                      v-if="resourceId==='facilities'"
                      :to="'/admin-data/cameras?underpassId='+row.id"
                    >카메라·입출구</RouterLink>
                  </div>
                </td>
              </tr>
              <tr v-if="!data.items.length">
                <td :colspan="visibleColumns.length+(hasRowActions?1:0)">조회된 항목이 없습니다.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="service-pagination">
          <button :disabled="page<=1" @click="navigatePage(page-1)">이전</button>
          <span>{{page}} / {{Math.max(1,data.totalPages)}} 페이지</span>
          <button :disabled="page>=data.totalPages" @click="navigatePage(page+1)">다음</button>
        </div>
      </template>
      <dialog
        ref="modal"
        class="service-modal"
        :class="{'service-app-modal':route.path.startsWith('/customer-app/')||route.path.startsWith('/admin-app/'),'restored-summary-drawer':restored&&mode==='detail'&&['facilities','customers'].includes(resourceId)}"
        @close="mode=''"
        @pointerdown="summaryBackdropDown = isSummaryBackdrop($event)"
        @click="closeSummaryBackdrop"
      >
        <form @submit.prevent="submit">
          <header>
            <h2>
              {{mode==='video'?'카메라 영상':mode==='detail'?(restored&&detail.name?detail.name:'상세 보기'):mode==='create'?(resourceId==='my-inquiries'?'문의 등록':'새로 등록'):mode==='edit'?'정보 수정':mode==='remove'?'삭제 확인':action?.label}}
            </h2>
            <button type="button" :disabled="saving" @click="modal?.close()">닫기</button>
          </header>
          <TrafficVideo v-if="mode==='video'" :src="selected?.streamUrl" :hls="true" />
          <img
            v-if="resourceId==='reviews'&amp;&amp;imageUrl(selected?.imagePath)"
            :src="imageUrl(selected?.imagePath)"
            alt="확인할 번호판"
            style="max-width:100%;max-height:300px;object-fit:contain"
          />
          <div
            v-if="restored&&mode==='detail'&&resourceId==='facilities'"
            class="restored-summary-overview"
          >
            <RiskLevelBadge :value="detail.currentRiskLevel" />
            <strong>{{detail.riskScore??detail.currentRiskScore??'—'}}점</strong>
            <span>{{detail.currentStatus==='BLOCKED'?'통제 중':'정상 운영'}}</span>
            <div class="restored-risk-gauge">
              <b
                :style="{width:Math.max(0,Math.min(100,Number(detail.riskScore??detail.currentRiskScore)||0))+'%'}"
              ></b>
            </div>
          </div>
          <article v-if="mode==='detail'&&resourceId==='notices'" class="notice-detail">
            <div class="notice-detail-title">
              <span v-if="detail.important" class="notice-important-badge">중요</span>
              <h3>{{detail.title}}</h3>
            </div>
            <dl class="notice-detail-meta">
              <div>
                <dt>분류</dt>
                <dd>{{detail.categoryLabel||boardCategoryLabel(detail.category)||'안내'}}</dd>
              </div>
              <div>
                <dt>작성자</dt>
                <dd>{{detail.authorName||'—'}}</dd>
              </div>
              <div>
                <dt>작성일</dt>
                <dd>{{display(detail.createdAt)}}</dd>
              </div>
              <div>
                <dt>조회수</dt>
                <dd>{{detail.viewCount??0}}</dd>
              </div>
            </dl>
            <div class="notice-detail-content">{{detail.content}}</div>
          </article>
          <OcrRecordDetail
            v-if="mode==='detail'&&resourceId==='ocr'"
            :record="detail"
            :image="imageUrl(detail.imagePath)"
          />
          <dl
            v-if="mode==='detail'&&resourceId!=='notices'&&resourceId!=='ocr'"
            class="service-detail"
          >
            <template v-for="(label,key) in labels" :key="key">
              <template v-if="detail[key]!=null">
                <dt>{{label}}</dt>
                <dd>{{displayField(String(key),detail[key])}}</dd>
              </template>
            </template>
          </dl>
          <div v-if="mode==='detail'&&facilityList" class="service-actions">
            <TunnelCctvDialog :facility-name="detail.name" />
            <RouterLink :to="{path:route.path.startsWith('/admin-app/')?'/admin-app/screen-5':'/fac-detail',query:{id:String(detail.id),underpassId:String(detail.id),tab:'cctv'}}">시설 CCTV 보기</RouterLink>
          </div>
          <div v-if="mode==='detail'&&canWrite" class="service-actions">
            <button v-if="canEditRow(detail)" type="button" @click="open('edit',detail)">수정</button>
            <button v-if="resource.remove" type="button" @click="open('remove',detail)">삭제</button>
            <RouterLink v-if="facilityList" :to="'/admin-data/cameras?underpassId='+detail.id">카메라·입출구</RouterLink>
            <button
              v-for="act in resource.actions||[]"
              :key="act.label"
              type="button"
              :disabled="!actionEnabled(detail)"
              @click="open('action',detail,act)"
            >
              {{act.label}}
            </button>
          </div>
          <template v-if="mode!=='detail'&amp;&amp;mode!=='video'">
            <p v-if="mode==='remove'">선택한 항목을 삭제하시겠습니까?</p>
            <p v-else-if="mode==='action'&&!formFields.length">선택한 항목에 대해 {{action?.label}} 작업을 진행하시겠습니까?</p>
            <div v-if="resourceId==='customers'&amp;&amp;mode==='edit'" class="service-message">
              <p>목록에는 기존 주소·비상 연락처가 제공되지 않습니다. 저장하면 해당 항목도 입력한 값으로 교체됩니다.</p>
              <label>
                <input v-model="acknowledge" type="checkbox" required /> 주소·비상 연락처 교체를 확인했습니다.</label>
            </div>
            <ServiceFields
              :fields="formFields"
              :values="values"
              :choices="choices"
              :disabled="saving"
            />
          </template>
          <p v-if="error" class="service-error" role="alert">{{error}}</p>
          <footer v-if="facilityList&&mode==='detail'&&detail.id" class="facility-drawer-footer">
            <RouterLink :to="{path:'/fac-detail',query:{id:String(detail.id)}}" @click="modal?.close()">상세보기</RouterLink>
          </footer>
          <footer v-if="mode!=='detail'&amp;&amp;mode!=='video'">
            <button type="button" :disabled="saving" @click="modal?.close()">취소</button>
            <button type="submit" :disabled="saving">{{saving?'처리 중…':'확인'}}</button>
          </footer>
        </form>
      </dialog>
    </section>
    <p v-else role="alert">페이지를 찾을 수 없습니다.</p>
  </component>
</template>

<style scoped src="../styles/pages/backend-resource.css"></style>

<style scoped src="../styles/pages/backend-resource-2.css"></style>

<style scoped src="../styles/pages/backend-resource-3.css"></style>

<style scoped src="../styles/pages/backend-resource-4.css"></style>

<style scoped src="../styles/pages/backend-resource-5.css"></style>

<style scoped src="../styles/pages/backend-resource-6.css"></style>
