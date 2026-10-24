<script setup lang="ts">
import RiskLevelBadge from '../components/RiskLevelBadge.vue'
import { useRiskChartClock } from '../composables/useRiskChartClock'
import { mapMarkerSvg } from '../utils/map-marker'
import { pageFriendlyMapScroll } from '../runtime/mapScroll'
import { useApp } from '../stores/app'
import type { BackendEvent } from '../composables/useBackendEvents'
import { patchFacility, facilityEvents, appendRiskHistory } from '../services/streamUpdates'
import { useBackendEvents } from '../composables/useBackendEvents'
import { riskChartSeries } from '../utils/risk-chart'
import { riskGroup, riskSummaryLabel } from '../services/backendCatalog'
import { ownsFacility } from '../services/facilityAccess'
import '../styles/map-weather.css'
import '../styles/live-map.css'
import '../styles/c-map.css'
import '../styles/customer-map-live.css'
import MapWeatherWidget from '../components/MapWeatherWidget.vue'
import ManagerMapPanel from '../components/ManagerMapPanel.vue'
import BackendResource from './backend-resource.vue'
import { backendResources } from '../data/backendResources'
import { busanDistricts, busanDistrict } from '../utils/busan-districts'
import { getWeather } from '../services/weather'
import { loadKakao } from '../services/kakao'
let disposeMapScroll: (() => void) | undefined

import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { displayDate, dateParts } from '../utils/display-date'
import { RouterLink, useRoute } from 'vue-router'
import TrafficMapLayers from '../components/TrafficMapLayers.vue'
const trafficMap = shallowRef<any>(), trafficMaps = shallowRef<any>()
const mapControlsExpanded = ref(true)
const currentRoute = useRoute()

const props = withDefaults(defineProps<{ manager?: boolean; mapOnly?: boolean; dashboardVisual?: boolean }>(), {
  manager: false,
})
const emit = defineEmits<{ controlChanged: [] }>()
const appMap = computed(() => !props.manager && currentRoute.path.startsWith('/customer-app/'))
const appDetail = computed(() => appMap.value && currentRoute.path.endsWith('/screen-6'))
const adminAppMap = computed(() => props.mapOnly && currentRoute.path.startsWith('/admin-app/'))
const sheetOpen = ref(false),
  sheetHeight = ref<number | null>(null)
let sheetDrag: { id: number; y: number; height: number; limit: number } | undefined,
  sheetMoved = false
function startSheet(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0) return
  const handle = event.currentTarget as HTMLElement,
    rect = handle.parentElement!.getBoundingClientRect(),
    limit = customerRoot.value!.clientHeight * 0.88
  sheetMoved = false
  sheetDrag = { id: event.pointerId, y: event.clientY, height: rect.height, limit }
  handle.setPointerCapture(event.pointerId)
}
function moveSheet(event: PointerEvent) {
  if (!sheetDrag || sheetDrag.id !== event.pointerId) return
  const delta = sheetDrag.y - event.clientY
  if (Math.abs(delta) > 6) sheetMoved = true
  if (sheetMoved)
    sheetHeight.value = Math.max(68, Math.min(sheetDrag.limit, sheetDrag.height + delta))
}
function endSheet() {
  if (sheetHeight.value !== null) sheetOpen.value = sheetHeight.value > 130
  sheetHeight.value = null
  sheetDrag = undefined
}
function toggleSheet() {
  if (sheetMoved) {
    sheetMoved = false
    return
  }
  sheetOpen.value = !sheetOpen.value
}
function collapseSheetOutside(event: PointerEvent) {
  if ((!appMap.value && !adminAppMap.value) || !sheetOpen.value || sheetDrag) return
  const target = event.target
  if (!(target instanceof Element)) return
  if (target.closest('.app-map-sheet,.admin-facility-sheet,dialog[open]')) return
  sheetOpen.value = false
  sheetHeight.value = null
}
const sheetStyle = computed(() =>
  appMap.value || props.mapOnly
    ? {
        height:
          sheetHeight.value !== null ? sheetHeight.value + 'px' : sheetOpen.value ? '72%' : '68px',
      }
    : undefined,
)
// 고객과 관리자에서 조회하는 공개 시설 목록입니다.
const API_BASE_URL = ''
const app = useApp()
const STALE_AFTER_MS = 120000
// 백엔드(back/final) UnderpassController 기준. 목록 항목에 현재 상태(currentStatus/currentRiskLevel)가 함께 옵니다.
const API = {
  underpasses: '/api/underpasses',
}
// 조회 구역의 대표 위치이며 지하차도 좌표/관측 데이터가 아닙니다.
// 2026-07 광주·전남 통합 반영. 이전 주소/지역 코드는 aliases로 함께 인식합니다.
const regions = [
  {
    id: 'seoul',
    name: '서울특별시',
    aliases: ['서울특별시', '서울시', '서울'],
    codes: ['11'],
    latitude: 37.5665,
    longitude: 126.978,
  },
  {
    id: 'busan',
    name: '부산광역시',
    aliases: ['부산광역시', '부산시', '부산'],
    codes: ['26'],
    latitude: 35.1796,
    longitude: 129.0756,
  },
  {
    id: 'daegu',
    name: '대구광역시',
    aliases: ['대구광역시', '대구시', '대구'],
    codes: ['27'],
    latitude: 35.8714,
    longitude: 128.6014,
  },
  {
    id: 'incheon',
    name: '인천광역시',
    aliases: ['인천광역시', '인천시', '인천'],
    codes: ['28'],
    latitude: 37.4563,
    longitude: 126.7052,
  },
  {
    id: 'daejeon',
    name: '대전광역시',
    aliases: ['대전광역시', '대전시', '대전'],
    codes: ['30'],
    latitude: 36.3504,
    longitude: 127.3845,
  },
  {
    id: 'ulsan',
    name: '울산광역시',
    aliases: ['울산광역시', '울산시', '울산'],
    codes: ['31'],
    latitude: 35.5384,
    longitude: 129.3114,
  },
  {
    id: 'sejong',
    name: '세종특별자치시',
    aliases: ['세종특별자치시', '세종시', '세종'],
    codes: ['36'],
    latitude: 36.48,
    longitude: 127.289,
  },
  {
    id: 'gyeonggi',
    name: '경기도',
    aliases: ['경기도', '경기'],
    codes: ['41'],
    latitude: 37.4138,
    longitude: 127.5183,
  },
  {
    id: 'gangwon',
    name: '강원특별자치도',
    aliases: ['강원특별자치도', '강원도', '강원'],
    codes: ['42', '51'],
    latitude: 37.8228,
    longitude: 128.1555,
  },
  {
    id: 'chungbuk',
    name: '충청북도',
    aliases: ['충청북도', '충북'],
    codes: ['43'],
    latitude: 36.8,
    longitude: 127.7,
  },
  {
    id: 'chungnam',
    name: '충청남도',
    aliases: ['충청남도', '충남'],
    codes: ['44'],
    latitude: 36.5184,
    longitude: 126.8,
  },
  {
    id: 'jeonbuk',
    name: '전북특별자치도',
    aliases: ['전북특별자치도', '전라북도', '전북'],
    codes: ['45', '52'],
    latitude: 35.82,
    longitude: 127.1088,
  },
  {
    id: 'jeonnam-gwangju',
    name: '전남광주통합특별시',
    aliases: [
      '전남광주통합특별시',
      '전남광주특별시',
      '광주광역시',
      '광주시',
      '전라남도',
      '전남',
      '광주',
    ],
    codes: ['29', '46'],
    latitude: 35.1595,
    longitude: 126.8526,
  },
  {
    id: 'gyeongbuk',
    name: '경상북도',
    aliases: ['경상북도', '경북'],
    codes: ['47'],
    latitude: 36.4919,
    longitude: 128.8889,
  },
  {
    id: 'gyeongnam',
    name: '경상남도',
    aliases: ['경상남도', '경남'],
    codes: ['48'],
    latitude: 35.4606,
    longitude: 128.2132,
  },
  {
    id: 'jeju',
    name: '제주특별자치도',
    aliases: ['제주특별자치도', '제주도', '제주'],
    codes: ['50'],
    latitude: 33.3846,
    longitude: 126.5535,
  },
]
type Json = Record<string, unknown>
type Facility = {
  id: string
  name: string
  address: string
  regionId: string
  assignedRegionId?: string
  manageable?: boolean
  riskLevel?: string
  controlled?: boolean
  currentWaterCm?: number | null
  assignedRegionName?: string
  latitude: number | null
  longitude: number | null
  status: string
  rainfallMm: number | null
  riskScore: number | null
  updatedAt: string
  pumpStatus: string
  remainingCount: number | null
  pendingReviewCount: number | null
}
const customerRoot = ref<HTMLElement>(),
  expanded = ref(true),
  riskHistory = ref<Json[]>([]),
  riskForecast = ref<Json[]>([]),
  forecastError = ref('')
function defaultDistrict() {
  const districts = [...new Set(
    (app.account?.regions || []).map((region) => busanDistrict(region.name)).filter(Boolean),
  )]
  // 여러 담당 구 중 첫 구에 시설이 없어도 다른 담당 구의 시설을 가리지 않습니다.
  return districts.length === 1 ? districts[0]! : ''
}
const district = ref(props.manager ? defaultDistrict() : ''),
  clusterFacilityIds = ref<string[]>([])
watch(() => app.account?.regions?.map(region => `${region.id}:${region.name}`).join('|'), () => {
  if (props.manager) district.value = defaultDistrict()
})
const managerPanelExpanded = ref(true),
  managerOperations = ref<InstanceType<typeof BackendResource>>()
function selectManagerRow(id: string) {
  const row = filtered.value.find((f) => f.id === id)
  if (row) void selectFacility(row)
}
function managerControl(index: number) {
  if (selected.value && ownsFacility(selected.value))
    void managerOperations.value?.open(
      'action',
      { id: Number(selected.value.id) },
      backendResources.facilities!.actions?.[index],
    )
}
async function focusAssignedRegion() {
  query.value = ''
  statusFilter.value = ''
  district.value = defaultDistrict()
  clearRegionSelection('busan')
  await nextTick()
  if (map) fitNation()
}
async function afterManagerControl(result?: { id: string; status: 'NORMAL' | 'BLOCKED' }) {
  // Control success is already applied through control-change; a full detail reload would lock
  // the opposite button while unrelated risk history and forecast requests complete.
  if (!result?.status) await refresh()
  emit('controlChanged')
}
const facilities = ref<Facility[]>([])
function displayState(f: Facility) {
  const label = stateLabel(f)
  return riskSummaryLabel(label, label === '\uD1B5\uC81C')
}
function badgeClass(f: Facility) {
  return (
    (
      {
        '안전': 'safe',
        '주의': 'warn',
        '관심': 'warn',
        '경계': 'danger',
        '심각': 'danger',
        '위험': 'danger',
        '통제': 'danger',
      } as Record<string, string>
    )[displayState(f)] || 'unknown'
  )
}
function controlLabel(f: Facility) {
  // Risk observation age does not determine whether an underpass is blocked.
  return f.controlled === true ? '통제 중' : f.controlled === false ? '정상 운영' : '상태 확인 필요'
}
function closeFacility() {
  detailAbort?.abort()
  selectedId.value = ''
  detail.value = null
  detailError.value = ''
  riskHistory.value = []
  riskForecast.value = []
  forecastError.value = ''
  infoWindow?.close()
}
const chartNow = useRiskChartClock()
const riskSeries = computed(() =>
  riskChartSeries(riskHistory.value, riskForecast.value, 20, 260, 110, 100, {
    now: chartNow.value,
  }),
)
const riskPoints = computed(() => riskSeries.value.observed)
async function fetchRiskForecast(id: string, signal: AbortSignal) {
  try {
    const rows = await request(`${API.underpasses}/${encodeURIComponent(id)}/risk-forecast`, signal)
    if (!signal.aborted && selectedId.value === id)
      riskForecast.value = Array.isArray(rows) ? rows.map(object) : []
  } catch {
    if (!signal.aborted && selectedId.value === id)
      forecastError.value = '예측 정보를 불러오지 못했습니다.'
  }
}

const detail = ref<Facility | null>(null)
type CompactWeather = {
  condition?: string
  temperature?: number | null
  humidity?: number | null
  rainfall?: number | null
  observedAt?: string
}
const compactWeather = ref<CompactWeather | null>(null),
  weatherError = ref(''),
  weatherLoading = ref(false)
let weatherAbort: AbortController | undefined

const detailRegion = ref<HTMLElement | null>(null)
const selectedId = ref(''),
  regionId = ref('busan'),
  query = ref(''),
  statusFilter = ref('')
const legendFilter = ref('')
const loading = ref(false),
  detailLoading = ref(false),
  locating = ref(false)
const listError = ref(''),
  statusError = ref(''),
  detailError = ref(''),
  mapError = ref('')
const receivedAt = ref(''),
  loaded = ref(false),
  now = ref(Date.now())
const mapElement = ref<HTMLDivElement | null>(null)
const findingPosition = ref(false)
let userLocation: any
function zoomMap(change: number) {
  if (map) map.setLevel(Math.max(1, Math.min(14, map.getLevel() + change)))
}
function locateUser() {
  if (!map || findingPosition.value) return
  if (!navigator.geolocation) {
    mapError.value = '이 브라우저에서는 위치 찾기를 지원하지 않습니다.'
    return
  }
  findingPosition.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      if (!alive) return
      findingPosition.value = false
      mapError.value = ''
      const center = new maps.LatLng(position.coords.latitude, position.coords.longitude)
      if (!userLocation) {
        const dot = document.createElement('div')
        dot.className = 'map-user-location'
        dot.setAttribute('aria-label', '내 현재 위치')
        userLocation = new maps.CustomOverlay({ map, position: center, content: dot, zIndex: 4 })
      } else userLocation.setPosition(center)
      map.setLevel(4)
      map.panTo(center)
    },
    (error) => {
      if (!alive) return
      findingPosition.value = false
      mapError.value =
        error.code === 1
          ? '위치 권한을 허용한 뒤 다시 눌러 주세요.'
          : '현재 위치를 확인하지 못했습니다.'
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
  )
}
let viewportObserver: ResizeObserver | undefined
let toolbarObserver: ResizeObserver | undefined
let adminSheetObserver: ResizeObserver | undefined
function sizeCustomerMap() {
  const root = customerRoot.value
  if (!root) return
  root.style.setProperty(
    '--customer-map-top',
    `${Math.max(0, root.getBoundingClientRect().top + window.scrollY)}px`,
  )
  nextTick(measureList)
}
let alive = true,
  requestNumber = 0,
  regionLookupNumber = 0
let listAbort: AbortController | undefined, detailAbort: AbortController | undefined

let sdkWatchdog: number | undefined
let sdkPoll: number | undefined
let observer: ResizeObserver | undefined
// 카카오 SDK에서 제공하는 객체입니다. 팀 프로젝트에 SDK 타입이 있으면 교체할 수 있습니다.
let maps: any, map: any, infoWindow: any, clusterInfoWindow: any, geocoder: any
let overlays: any[] = []
let markers: { id: string; marker: any; click: () => void }[] = []
watch(
  () => selectedId.value,
  async (id) => {
    weatherAbort?.abort()
    compactWeather.value = null
    weatherError.value = ''
    weatherLoading.value = false
    const facility = facilities.value.find((f) => f.id === id)
    if (!facility || props.mapOnly) return
    if (facility.latitude === null || facility.longitude === null) {
      weatherError.value = '위치 정보가 없어 날씨를 확인할 수 없습니다.'
      return
    }
    const controller = new AbortController()
    weatherAbort = controller
    weatherLoading.value = true
    try {
      const data = await getWeather(facility.latitude, facility.longitude, controller.signal)
      if (!controller.signal.aborted) compactWeather.value = data
    } catch {
      if (!controller.signal.aborted) weatherError.value = '현재 날씨를 불러오지 못했습니다.'
    } finally {
      if (!controller.signal.aborted) weatherLoading.value = false
    }
  },
)
const regionCache = new Map<string, string>()

function object(value: unknown): Json {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Json) : {}
}
function text(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}
function number(value: unknown): number | null {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean')
    return null
  const result = Number(value)
  return Number.isFinite(result) ? result : null
}
function findRegion(row: Json): string {
  const code = text(row.sidoCode || row.regionCode).slice(0, 2)
  const byCode = regions.find((r) => r.codes.includes(code))
  if (byCode) return byCode.id
  // Check all province/address aliases when backend regions contain district names.
  for (const candidate of [
    row.roadAddress,
    row.address,
    row.regionName,
    row.sidoName,
    row.region,
  ]) {
    const name = text(candidate).trim()
    const match = regions.find((r) =>
      r.aliases.some((alias) => name === alias || name.startsWith(alias + ' ')),
    )
    if (match) return match.id
  }
  return busanDistrict(row.regionName, row.roadAddress, row.address) ? 'busan' : ''
}
function statusLabel(value: unknown): string {
  const labels: Record<string, string> = {
    NORMAL: '정상',
    SAFE: '정상',
    OPEN: '정상',
    NORMAL_TRAFFIC: '정상',
    CAUTION: '경고',
    WATCH: '관심',
    ALERT: '경계',
    SEVERE: '심각',
    관심: '관심',
    경계: '경계',
    심각: '심각',
    WARNING: '경고',
    DANGER: '위험',
    BLOCKED: '통제',
    CLOSED: '통제',
    CONTROLLED: '통제',
    정상: '정상',
    안전: '정상',
    주의: '경고',
    경고: '경고',
    위험: '위험',
    통제: '통제',
    해제: '정상',
  }
  return labels[text(value).toUpperCase()] || '미확인'
}
// PDF는 URL과 공통 응답 형식만 정의합니다. 실제 조회 DTO 확정 시 이 매핑만 맞춥니다.
function normalize(row: Json, current?: Json): Facility | null {
  const id = text(row.underpassId ?? row.id)
  if (!id) return null
  const state = current || {}
  const latitude = number(row.latitude),
    longitude = number(row.longitude)
  const coordinatesValid =
    latitude !== null &&
    longitude !== null &&
    latitude >= 32 &&
    latitude <= 39.5 &&
    longitude >= 124 &&
    longitude <= 132
  const risk = number(state.riskScore ?? state.currentRiskScore)
  const hasBlocked = typeof state.isBlocked === 'boolean'
  return {
    id,
    name: text(row.name) || '이름 미제공',
    address: text(row.roadAddress || row.address),
    regionId: findRegion(row),
    assignedRegionId: String(row.regionId ?? ''),
    assignedRegionName: String(row.regionName ?? ''),
    manageable: typeof row.manageable === 'boolean' ? row.manageable : undefined,
    riskLevel: text(state.currentRiskLevel ?? row.currentRiskLevel),
    controlled:
      typeof state.isBlocked === 'boolean'
        ? state.isBlocked
        : ['NORMAL', 'BLOCKED'].includes(text(state.currentStatus).toUpperCase())
          ? text(state.currentStatus).toUpperCase() === 'BLOCKED'
          : undefined,
    currentWaterCm: number(state.currentWaterCm ?? row.currentWaterCm),
    latitude: coordinatesValid ? latitude : null,
    longitude: coordinatesValid ? longitude : null,
    // 백엔드: currentStatus(NORMAL/BLOCKED) + currentRiskLevel(SAFE/WARNING/DANGER)
    status:
      (hasBlocked && state.isBlocked === true) ||
      text(state.currentStatus).toUpperCase() === 'BLOCKED'
        ? '통제'
        : statusLabel(
            state.controlStatus ?? state.status ?? state.currentRiskLevel ?? state.currentStatus,
          ),
    rainfallMm: number(state.rainfallMm),
    riskScore: risk !== null && risk >= 0 && risk <= 100 ? risk : null,
    // 백엔드 목록에는 상태 기준 시각이 없어 조회 시각(receivedAt)을 기준으로 사용합니다.
    updatedAt: text(state.updatedAt || state.observedAt || state.predictedAt || state.receivedAt),
    pumpStatus: text(state.pumpStatus),
    remainingCount: number(state.remainingCount ?? row.remainingCount),
    pendingReviewCount: number(state.pendingReviewCount ?? row.pendingReviewCount),
  }
}
function clearState(row: Facility): Facility {
  return {
    ...row,
    status: '미확인',
    rainfallMm: null,
    riskScore: null,
    updatedAt: '',
    pumpStatus: '',
  }
}
function stateLabel(row: Facility): string {
  if (listError.value || statusError.value || (selectedId.value === row.id && detailError.value))
    return '확인 불가'
  if (!row.updatedAt || !Number.isFinite(Date.parse(row.updatedAt)))
    return '미확인 (기준 시각 없음)'
  const age = now.value - Date.parse(row.updatedAt)
  if (age < -60000 || age > STALE_AFTER_MS) return '갱신 지연'
  return row.status
}
function formatTime(value: string): string {
  if (!props.manager) return displayDate(value)
  const time = Date.parse(value)
  return Number.isFinite(time) ? new Date(time).toLocaleString('ko-KR') : '미제공'
}
async function request(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await fetch(API_BASE_URL + path, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) {
    if (response.status === 401) throw new Error('로그인이 필요한 정보입니다.')
    if (response.status === 403) throw new Error('조회 권한이 없습니다.')
    if (response.status === 404) throw new Error('조회 서비스를 찾을 수 없습니다.')
    if (response.status >= 500)
      throw new Error('지하차도 조회 서버에 오류가 발생했습니다. 잠시 후 새로고침해 주세요.')
    throw new Error('정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new Error('조회 서비스에 연결할 수 없습니다.')
  const body = object(await response.json())
  if (body.success !== true) throw new Error(text(body.message) || '조회에 실패했습니다.')
  return body.data
}
async function collection(path: string, signal: AbortSignal): Promise<Json[]> {
  const all: Json[] = []
  let page = 1
  // 단순 배열 또는 백엔드 PageResponse(items, totalPages, page는 1부터, size 최대 100) 형태를 허용합니다.
  while (page <= 100) {
    const result = await request(`${path}?page=${page}&size=100`, signal)
    if (Array.isArray(result)) return [...all, ...result.map(object)]
    const body = object(result),
      rows = body.content ?? body.items
    if (!Array.isArray(rows)) throw new Error('목록 응답 형식을 확인할 수 없습니다.')
    all.push(...rows.map(object))
    const totalPages = number(body.totalPages)
    if (body.last === true || (totalPages !== null && page >= totalPages)) return all
    if (body.last !== false && totalPages === null) {
      if (number(body.totalElements) !== null && Number(body.totalElements) > all.length)
        throw new Error('전체 목록을 확인할 수 없습니다.')
      return all
    }
    if (rows.length === 0) throw new Error('목록 일부를 불러오지 못했습니다.')
    page++
  }
  throw new Error('전체 목록을 불러오지 못했습니다. 조회 범위를 확인해 주세요.')
}
// 담당 지역은 등록 시설이 아직 없어도 기본 선택을 유지합니다.
const availableDistricts = computed(() => {
  const populated = new Set(
    facilities.value.map((facility) => busanDistrict(facility.assignedRegionName, facility.address)),
  )
  if (props.manager)
    for (const region of app.account?.regions || []) populated.add(busanDistrict(region.name))
  return busanDistricts.filter((name) => populated.has(name))
})
watch(availableDistricts, (names) => {
  if (district.value && !names.some((name) => name === district.value)) district.value = ''
})
const filtered = computed(() =>
  facilities.value.filter(
    (f) =>
      (!regionId.value || f.regionId === regionId.value) &&
      (!district.value || busanDistrict(f.assignedRegionName, f.address) === district.value) &&
      (!query.value || (f.name + ' ' + f.address).includes(query.value)) &&
      (!legendFilter.value || legendState(stateLabel(f)) === legendFilter.value) &&
      (!statusFilter.value ||
        (statusFilter.value === '미확인'
          ? stateLabel(f).startsWith('미확인')
          : statusFilter.value === '위험'
            ? riskGroup(stateLabel(f)) === 'DANGER'
            : statusFilter.value === '경고'
              ? riskGroup(stateLabel(f)) === 'WARNING'
              : stateLabel(f) === statusFilter.value)),
  ),
)
const selected = computed(() =>
  detail.value?.id === selectedId.value
    ? detail.value
    : facilities.value.find((f) => f.id === selectedId.value) || null,
)
const missingCoordinates = computed(
  () => filtered.value.filter((f) => f.latitude === null || f.longitude === null).length,
)
const unclassified = computed(() => facilities.value.filter((f) => !f.regionId).length)
const page = ref(1),
  adaptivePageSize = ref(5)
const pageSize = computed(() => (props.manager ? 10 : appMap.value ? 5 : adaptivePageSize.value))
const listRows = computed(() =>
  !props.manager && clusterFacilityIds.value.length
    ? filtered.value.filter((f) => clusterFacilityIds.value.includes(f.id))
    : filtered.value,
)
const pages = computed(() => Math.max(1, Math.ceil(listRows.value.length / pageSize.value)))
const shown = computed(() =>
  appMap.value
    ? listRows.value
    : listRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
let listObserver: ResizeObserver | undefined,
  listElement: HTMLElement | null = null
function measureList() {
  if (props.manager || appMap.value) return
  const list = customerRoot.value?.querySelector<HTMLElement>('.facility-list-panel .lst')
  if (!list) return
  if (list !== listElement) {
    listObserver?.disconnect()
    listElement = list
    listObserver = new ResizeObserver(measureList)
    listObserver.observe(list)
  }
  if (matchMedia('(max-width:900px)').matches) {
    if (adaptivePageSize.value !== 5) adaptivePageSize.value = 5
    return
  }
  const heights = Array.from(list.querySelectorAll<HTMLElement>('.it>span:last-child')).map(
    (text) => text.getBoundingClientRect().height + 26,
  )
  const rowHeight = Math.max(64, ...heights)
  const capacity = Math.max(1, Math.min(10, Math.floor(list.clientHeight / rowHeight)))
  if (capacity !== adaptivePageSize.value) {
    const first = (page.value - 1) * adaptivePageSize.value
    adaptivePageSize.value = capacity
    page.value = Math.floor(first / capacity) + 1
  }
}
watch(
  () => [shown.value.length, selectedId.value, loading.value],
  () => nextTick(measureList),
)
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(page.value - 2, pages.value - 4))
  return Array.from({ length: Math.min(5, pages.value) }, (_, i) => start + i)
})
function changePage(next: number) {
  page.value = Math.max(1, Math.min(pages.value, next))
  nextTick(() =>
    customerRoot.value?.querySelector('.lst')?.scrollTo({ top: 0, behavior: 'instant' }),
  )
}
async function chooseDistrict(value: string) {
  district.value = value
  page.value = 1
  await chooseRegion('busan')
}
watch([regionId, district, query, statusFilter, clusterFacilityIds], () => {
  page.value = 1
})
watch(pages, () => {
  page.value = Math.min(page.value, pages.value)
})

let initialFacilityBoundsApplied = false
async function searchFacilities() {
  await refresh()
  await nextTick()
  if (!map || selectedId.value) return
  const rows = filtered.value.filter((f) => f.latitude !== null && f.longitude !== null)
  if (!rows.length) return
  map.relayout()
  const bounds = new maps.LatLngBounds()
  rows.forEach((f) => bounds.extend(new maps.LatLng(f.latitude, f.longitude)))
  map.setBounds(bounds, 48, 32, 32, 32)
  if (rows.length === 1) map.setLevel(5)
}
async function refresh() {
  if (loading.value || !alive) return
  const version = ++requestNumber
  listAbort?.abort()
  listAbort = new AbortController()
  const signal = listAbort.signal
  const timeout = window.setTimeout(() => listAbort?.abort(), 15000)
  loading.value = true
  listError.value = ''
  statusError.value = ''
  try {
    const master = await collection(API.underpasses, signal)
    if (!alive || version !== requestNumber) return
    const fetchedAt = new Date().toISOString()
    const unique = new Map<string, Facility>()
    for (const row of master) {
      // 목록 항목 자체에 현재 상태가 들어 있으므로 상태 정보로도 사용합니다.
      const f = normalize(row, { ...row, receivedAt: fetchedAt })
      if (!f) throw new Error('지하차도 목록의 식별자 형식을 확인할 수 없습니다.')
      if (f) {
        const cached = regionCache.get(`${f.latitude},${f.longitude}`)
        if (!f.regionId && cached) f.regionId = cached
        unique.set(f.id, f)
      }
    }
    facilities.value = [...unique.values()]
    if (map && !initialFacilityBoundsApplied && facilities.value.length && !selectedId.value) {
      fitNation()
      initialFacilityBoundsApplied = true
    }
    loaded.value = true
    receivedAt.value = new Date().toISOString()
    now.value = Date.now()
    if (selectedId.value && !unique.has(selectedId.value)) {
      selectedId.value = ''
      detail.value = null
      infoWindow?.close()
    }
    if (selectedId.value) await loadDetail(selectedId.value)
    void resolveRegions()
  } catch (error) {
    if (!alive || version !== requestNumber) return
    facilities.value = facilities.value.map(clearState)
    detail.value = null
    listError.value = signal.aborted
      ? '응답 시간이 초과되었습니다. 다시 조회해 주세요.'
      : error instanceof Error
        ? error.message
        : '목록을 불러오지 못했습니다.'
  } finally {
    clearTimeout(timeout)
    if (alive && version === requestNumber) {
      loading.value = false
      replayStreamBuffer()
    }
  }
}
// 위험도(riskScore)/강우량(rainfallMm)은 상세 응답에 없고 MP-05 추이 엔드포인트에만 있어
// 최근 1건을 가져와 최신 값으로 사용합니다. 실패해도 상세 조회 자체는 계속 진행합니다.
async function fetchLatestRiskScore(id: string, signal: AbortSignal): Promise<Json | null> {
  try {
    const rows = await request(
      `${API.underpasses}/${encodeURIComponent(id)}/risk-scores?hours=6&limit=500`,
      signal,
    )
    if (!Array.isArray(rows) || !rows.length) return null
    if (!signal.aborted && selectedId.value === id) riskHistory.value = rows.map(object)
    // 서비스가 오름차순으로 반환하므로 마지막 원소가 최신입니다.
    return object(rows[rows.length - 1])
  } catch {
    return null
  }
}
async function loadDetail(id: string) {
  riskHistory.value = []
  riskForecast.value = []
  forecastError.value = ''
  detailAbort?.abort()
  detailAbort = new AbortController()
  const signal = detailAbort.signal
  const timeout = window.setTimeout(() => {
    if (!signal.aborted) controller.abort()
  }, 10000)
  const controller = detailAbort
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const [rawBody, latestRisk] = await Promise.all([
      request(`${API.underpasses}/${encodeURIComponent(id)}`, signal),
      fetchLatestRiskScore(id, signal),
      fetchRiskForecast(id, signal),
    ])
    const body = object(rawBody)
    if (!alive || selectedId.value !== id || signal.aborted) return
    const base = facilities.value.find((f) => f.id === id)
    const receivedId = text(body.underpassId ?? body.id)
    if (receivedId && receivedId !== id) throw new Error('선택한 지하차도와 응답 정보가 다릅니다.')
    const riskState = latestRisk
      ? {
          riskScore: latestRisk.riskScore,
          rainfallMm: latestRisk.rainfallMm,
          predictedAt: latestRisk.predictedAt,
        }
      : {}
    const normalized = normalize(
      {
        ...body,
        underpassId: id,
        name: body.name || base?.name,
        regionName: body.regionName,
        latitude: body.latitude ?? base?.latitude,
        longitude: body.longitude ?? base?.longitude,
        remainingCount: body.remainingCount ?? base?.remainingCount,
        pendingReviewCount: body.pendingReviewCount ?? base?.pendingReviewCount,
      },
      { ...body, ...riskState },
    )
    if (normalized) {
      // 상세 응답에 상태 정보가 없는 경우 최신 요약값만 사용합니다.
      const hasCurrent =
        body.currentStatus !== undefined ||
        body.status !== undefined ||
        body.controlStatus !== undefined ||
        body.isBlocked !== undefined
      detail.value = hasCurrent
        ? {
            ...normalized,
            regionId: normalized.regionId || base?.regionId || '',
            updatedAt: normalized.updatedAt || base?.updatedAt || '',
          }
        : base || normalized
    }
  } catch (error) {
    if (!alive || selectedId.value !== id || (signal.aborted && controller !== detailAbort)) return
    detailError.value = signal.aborted
      ? '상세 응답 시간이 초과되었습니다.'
      : error instanceof Error
        ? error.message
        : '최신 상세 정보를 확인할 수 없습니다.'
  } finally {
    clearTimeout(timeout)
    if (alive && selectedId.value === id && controller === detailAbort) {
      detailLoading.value = false
      replayStreamBuffer()
      await nextTick()
      openSelectedPopup()
    }
  }
}
async function selectFacility(f: Facility) {
  dismissedPopupId = ''
  closeClusterPopup()
  if (props.manager) managerPanelExpanded.value = true
  if (appMap.value) sheetOpen.value = true
  if (appMap.value) {
    sessionStorage.setItem('routel-app-facility', f.id)
    if (appDetail.value) expanded.value = true
  }
  selectedId.value = f.id
  detail.value = null
  if (!regionId.value && f.regionId) regionId.value = f.regionId
  await nextTick()
  if (selectedId.value !== f.id) return
  if (map && f.latitude !== null && f.longitude !== null) {
    map.relayout()
    clusterer?.redraw()
    map.setLevel(5, { anchor: new maps.LatLng(f.latitude, f.longitude) })
    map.setCenter(new maps.LatLng(f.latitude, f.longitude))
    drawMap()
  }
  void loadDetail(f.id)
  detailRegion.value?.focus({ preventScroll: true })
  mapElement.value?.closest('.map-columns')?.scrollIntoView({
    block: 'start',
    behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
}
watch(
  () => [facilities.value, currentRoute.query.id],
  () => {
    if (!appDetail.value || selectedId.value) return
    const id = String(currentRoute.query.id || sessionStorage.getItem('routel-app-facility') || '')
    const row = facilities.value.find((f) => f.id === id)
    if (row) void selectFacility(row)
  },
)
function clearRegionSelection(id: string) {
  detailAbort?.abort()
  selectedId.value = ''
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  infoWindow?.close()
  regionId.value = id
}
async function chooseRegion(id: string) {
  clearRegionSelection(id)
  await nextTick()
  if (regionId.value !== id) return
  map?.relayout()
  if (!map) return
  const region = regions.find((r) => r.id === id)
  if (region) {
    map.setCenter(new maps.LatLng(region.latitude, region.longitude))
    const rows = filtered.value.filter(
      (f) =>
        f.regionId === id &&
        f.latitude !== null &&
        f.longitude !== null &&
        Number.isFinite(f.latitude) &&
        Number.isFinite(f.longitude),
    )
    if (rows.length) {
      const bounds = new maps.LatLngBounds()
      rows.forEach((f) => bounds.extend(new maps.LatLng(f.latitude, f.longitude)))
      map.setBounds(bounds, 40, 40, 40, 40)
      if (rows.length === 1) map.setLevel(6)
      else if (props.manager && map.getLevel() > (district.value ? 6 : 7))
        map.setLevel(district.value ? 6 : 7)
    } else map.setLevel(props.manager ? 7 : 10)
  } else fitNation()
}
// 지역 선택(레벨 10) 후 사용자가 이 레벨 이상으로 축소하면 전국 지역 선택 화면으로 돌아갑니다.
const NATION_VIEW_LEVEL = 12
function handleZoomChanged() {
  // Explicit nationwide button preserves selection during programmatic zoom.
}
function fitNation() {
  if (appMap.value && !props.manager) {
    map.setCenter(new maps.LatLng(35.1796, 129.0756))
    map.setLevel(7)
    return
  }
  const bounds = new maps.LatLngBounds()
  const assigned = props.manager ? app.account?.regions || [] : []
  const scoped = facilities.value.filter((f) =>
    assigned.some((r) => {
      const name = busanDistrict(r.name)
      return name
        ? busanDistrict(f.assignedRegionName, f.address) === name
        : String(r.id) === f.assignedRegionId ||
            f.assignedRegionName === r.name ||
            f.address.includes(r.name)
    }),
  )
  const points = (
    scoped.length
      ? scoped
      : facilities.value.filter((f) => !regionId.value || f.regionId === regionId.value)
  ).filter(
    (f) =>
      f.latitude !== null &&
      f.longitude !== null &&
      Number.isFinite(f.latitude) &&
      Number.isFinite(f.longitude),
  )
  if (props.manager && !points.length) {
    map.setCenter(new maps.LatLng(35.1796, 129.0756))
    map.setLevel(7)
    return
  }
  for (const f of points) bounds.extend(new maps.LatLng(f.latitude, f.longitude))
  // Keep the existing service region representatives (including Jeju) in the nationwide view.
  for (const r of points.length ? [] : regions)
    if (Number.isFinite(r.latitude) && Number.isFinite(r.longitude))
      bounds.extend(new maps.LatLng(r.latitude, r.longitude))
  map.setBounds(bounds, props.manager ? 16 : 48, 16, 16, 16)
  if (points.length === 1) map.setLevel(props.manager ? 4 : 6)
  else if (props.manager && !props.mapOnly) {
    const limit = district.value || assigned.some((region) => busanDistrict(region.name)) ? 6 : 7
    if (map.getLevel() > limit) map.setLevel(limit)
  }
}
async function showAllFacilities() {
  query.value = ''
  statusFilter.value = ''
  district.value = ''
  page.value = 1
  closeClusterPopup()
  clusterFacilityIds.value = []
  closeFacility()
  regionId.value = 'busan'
  await nextTick()
  if (!map) return
  map.relayout()
  const rows = facilities.value.filter(
    (row) =>
      row.latitude !== null &&
      row.longitude !== null &&
      Number.isFinite(row.latitude) &&
      Number.isFinite(row.longitude),
  )
  if (!rows.length) {
    map.setCenter(new maps.LatLng(35.1796, 129.0756))
    map.setLevel(9)
    return
  }
  const bounds = new maps.LatLngBounds()
  rows.forEach((row) => bounds.extend(new maps.LatLng(row.latitude, row.longitude)))
  map.setBounds(bounds, 40, 40, 40, 40)
  if (rows.length === 1) map.setLevel(6)
}
let dismissedPopupId = ''
function dismissFacilityPopup() {
  dismissedPopupId = selectedId.value
  infoWindow?.close()
}
function dismissPopupOutside(event: PointerEvent) {
  if (event.target instanceof Element && event.target.closest('.routel-map-popup')) return
  dismissFacilityPopup()
}
function dismissMapPopups() {
  dismissFacilityPopup()
  closeClusterPopup()
}
function openSelectedPopup() {
  if (props.manager && !props.mapOnly) return
  if (!map || !selected.value || detailLoading.value) return
  if (dismissedPopupId === selectedId.value) return
  const item = markers.find((m) => m.id === selectedId.value)
  if (!item) return
  const row = selected.value,
    content = document.createElement('div')
  content.className = 'routel-map-popup'
  content.addEventListener('mousedown', () => maps.event.preventMap?.())
  content.addEventListener('touchstart', () => maps.event.preventMap?.(), { passive: true })
  const heading = document.createElement('strong')
  heading.className = 'routel-map-popup__title'
  heading.textContent = row.name
  content.append(heading)
  const status = document.createElement('p')
  status.className = 'routel-map-popup__row'
  const label = document.createElement('span')
  label.className = 'routel-map-popup__label'
  label.textContent = '현재 상태'
  const badge = document.createElement('span')
  badge.className = 'routel-map-popup__status'
  badge.dataset.state = stateLabel(row)
  badge.textContent = stateLabel(row)
  status.append(label, badge)
  const time = document.createElement('p')
  time.className = 'routel-map-popup__row routel-map-popup__row--time'
  const timeLabel = document.createElement('span')
  timeLabel.className = 'routel-map-popup__label'
  timeLabel.textContent = '기준 시각'
  const timeValue = document.createElement('span')
  timeValue.className = 'routel-map-popup__time'
  timeValue.textContent = formatTime(row.updatedAt)
  time.append(timeLabel, timeValue)
  content.append(status, time)
  if (detailError.value) {
    const p = document.createElement('p')
    p.className = 'routel-map-popup__error'
    p.textContent = detailError.value
    content.append(p)
  }
  infoWindow.setContent(content)
  infoWindow.open(map, item.marker)
}
// 상태별 마커 색상. 위에 있을수록 위험한 상태이며 겹칠 때 앞쪽에 그립니다.
const markerStyles = [
  { label: '통제', color: '#cc6271', zIndex: 6 },
  { label: '위험', color: '#dc8b56', zIndex: 5 },
  { label: '주의', color: '#cfad3e', zIndex: 4 },
  { label: '관심', color: '#777fc1', zIndex: 3 },
  { label: '안전', color: '#4f9f86', zIndex: 2 },
  { label: '미확인', color: '#8793a0', zIndex: 1 },
]
const markerLegendStyles = ['안전', '주의', '관심', '위험', '통제'].map(
  (label) => markerStyles.find((style) => style.label === label)!,
)
function legendState(state: string) {
  return state === '정상' ? '안전' : state === '경고' ? '주의' : ['경계', '심각'].includes(state) ? '위험' : state
}
function toggleLegend(label: string) {
  statusFilter.value = ''
  legendFilter.value = legendFilter.value === label ? '' : label
  page.value = 1
  closeClusterPopup()
  clusterFacilityIds.value = []
  if (selectedId.value && !filtered.value.some(row => row.id === selectedId.value)) closeFacility()
}
watch(statusFilter, value => {
  if (value) legendFilter.value = ''
})
function markerStyle(state: string) {
  const mapped = state === '정상' ? '안전' : state === '경고' ? '주의' : ['경계', '심각'].includes(state) ? '위험' : state
  return markerStyles.find((m) => m.label === mapped) || markerStyles[markerStyles.length - 1]!
}
const markerOpacity = { fill: 0.94, halo: 0.05 }
const markerImages = new Map<string, any>()
function markerImage(state: string, assigned = true, selected = false) {
  const style = markerStyle(state)
  const cacheKey = style.label + (assigned ? '' : ':outside') + (selected ? ':selected' : '')
  const cached = markerImages.get(cacheKey)
  if (cached) return cached
  const svg = mapMarkerSvg(
    props.manager && !assigned ? '#9aa3ae' : style.color,
    markerOpacity.fill,
    undefined,
    selected,
    props.manager && assigned,
    true,
  )
  const image = new maps.MarkerImage(
    'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg),
    new maps.Size(44, 44),
    { offset: new maps.Point(22, 22) },
  )
  markerImages.set(cacheKey, image)
  return image
}
function clusterStyle(_state: string, _count = 1, _maxCount = _count) {
  return {
    width: '44px',
    height: '44px',
    boxSizing: 'border-box',
    background: 'transparent',
    boxShadow: 'none',
    color: '#ffffff',
    border: '0',
    padding: '0',
    minWidth: '0',
    minHeight: '0',
    borderRadius: '50%',
    textAlign: 'center',
    lineHeight: '0',
    fontFamily: 'Arial,sans-serif',
    fontSize: '14px',
    fontWeight: '700',
    display: 'block',
  }
}
let styledClusters: any[] = []
function styleClusters(clusters: any[]) {
  styledClusters = clusters
  const maxCount = Math.max(1, ...clusters.map((cluster) => cluster.getMarkers().length))
  const ordered = [...clusters].sort(
    (a, b) =>
      a.getCenter().getLng() - b.getCenter().getLng() ||
      a.getCenter().getLat() - b.getCenter().getLat(),
  )
  for (const cluster of ordered) {
    const states = cluster.getMarkers().map((marker: any) => {
      const id = markers.find((item) => item.marker === marker)?.id
      const row = facilities.value.find((item) => item.id === id)
      return row ? stateLabel(row) : '미확인'
    })
    const state = markerStyles.find((style) =>
          states.some((state: string) => markerStyle(state).label === style.label),
        )?.label || '미확인'
    const content = cluster.getClusterMarker().getContent()
    if (content instanceof HTMLElement) {
      Object.assign(content.style, clusterStyle(state, cluster.getMarkers().length, maxCount))
      const ids = cluster
        .getMarkers()
        .map((marker: any) => markers.find((item) => item.marker === marker)?.id)
        .filter(Boolean)
      const selected =
        ids.includes(selectedId.value) ||
        (!selectedId.value &&
          clusterFacilityIds.value.length > 0 &&
          ids.length === clusterFacilityIds.value.length &&
          ids.every((id: string) => clusterFacilityIds.value.includes(id)))
      const density =
        cluster.getMarkers().length <= 1
          ? 0
          : Math.min(1, (cluster.getMarkers().length - 1) / Math.max(1, maxCount - 1))
      const assigned =
        props.manager &&
        ids.some((id: string) => ownsFacility(facilities.value.find((row) => row.id === id)))
      content.innerHTML = mapMarkerSvg(
        props.manager && !assigned ? '#9aa3ae' : markerStyle(state).color,
        markerOpacity.fill + (1 - markerOpacity.fill) * density,
        cluster.getMarkers().length,
        selected,
        assigned,
        true,
      )
      content.classList.toggle('routel-marker-selected', selected)
      content.setAttribute(
        'aria-label',
        `${cluster.getMarkers().length}\uAC1C \uC2DC\uC124${selected ? ' (\uC120\uD0DD\uB428)' : ''}`,
      )
      content.style.filter = selected ? 'drop-shadow(0 0 3px #174f8880)' : 'none'
      if (props.manager) {
        const assigned = ids.some((id: string) =>
          ownsFacility(facilities.value.find((row) => row.id === id)),
        )
        content.title = assigned
          ? '\uB2F4\uB2F9 \uC2DC\uC124 \uD3EC\uD568'
          : '\uB2F4\uB2F9 \uC678 \uC2DC\uC124'
      }
    }
  }
}
function removeMarkers() {
  styledClusters = []
  clusterer?.clear()
  for (const { marker, click } of markers) {
    maps.event.removeListener(marker, 'click', click)
    marker.setMap(null)
  }
  markers = []
  overlays.forEach((overlay) => overlay.setMap(null))
  overlays = []
}
let clusterer: any
const clusterRows = computed(() =>
  facilities.value.filter((f) => clusterFacilityIds.value.includes(f.id)),
)
let clusterPopupOpen = false,
  clusterPopupRequest = 0
function closeClusterPopup() {
  clusterPopupRequest++
  if (clusterPopupOpen) clusterInfoWindow?.close()
  clusterPopupOpen = false
}
async function selectCluster(cluster: any) {
  const ids: string[] = cluster
    .getMarkers()
    .map((m: any) => markers.find((x) => x.marker === m)?.id)
    .filter(Boolean)
  const rows = facilities.value.filter((row) => ids.includes(row.id))
  if (!rows.length) return
  const coordinates = rows.filter((row) => row.latitude !== null && row.longitude !== null)
  const position =
    cluster.getCenter?.() ||
    cluster.getClusterMarker?.().getPosition?.() ||
    (coordinates.length
      ? new maps.LatLng(
          coordinates.reduce((sum, row) => sum + row.latitude!, 0) / coordinates.length,
          coordinates.reduce((sum, row) => sum + row.longitude!, 0) / coordinates.length,
        )
      : null)
  if (!position) return
  const request = ++clusterPopupRequest
  if (props.manager) managerPanelExpanded.value = true
  if (appMap.value) sheetOpen.value = false
  clusterFacilityIds.value = ids
  selectedId.value = ''
  detail.value = null
  await nextTick()
  if (!alive || request !== clusterPopupRequest) return
  const content = document.createElement('section')
  content.className = 'routel-cluster-popup'
  content.setAttribute('aria-label', '묶음 지하차도 목록')
  content.addEventListener('mousedown', () => maps.event.preventMap?.())
  content.addEventListener('touchstart', () => maps.event.preventMap?.(), { passive: true })
  const header = document.createElement('header'),
    heading = document.createElement('strong'),
    close = document.createElement('button')
  heading.textContent = `지하차도 ${rows.length}곳`
  close.type = 'button'
  close.textContent = '×'
  close.setAttribute('aria-label', '묶음 목록 닫기')
  close.onclick = closeClusterPopup
  header.append(heading, close)
  const list = document.createElement('ul')
  for (const row of rows) {
    const item = document.createElement('li'),
      button = document.createElement('button'),
      dot = document.createElement('i'),
      name = document.createElement('span'),
      state = document.createElement('small')
    button.type = 'button'
    button.className = 'routel-cluster-popup__item'
    dot.style.background = markerStyle(stateLabel(row)).color
    dot.setAttribute('aria-hidden', 'true')
    name.textContent = row.name
    state.textContent = displayState(row)
    button.append(dot, name, state)
    button.onclick = () => {
      closeClusterPopup()
      void selectFacility(row)
    }
    item.append(button)
    list.append(item)
  }
  content.append(header, list)
  clusterInfoWindow.setContent(content)
  clusterInfoWindow.setPosition?.(position)
  clusterInfoWindow.open(map)
  clusterPopupOpen = true
  maps.event.preventMap?.()
}
function drawMap() {
  if (!map) return
  infoWindow.close()
  clusterInfoWindow?.close()
  clusterPopupOpen = false
  removeMarkers()
  if (!regionId.value && !query.value && !selectedId.value && !legendFilter.value) {
    for (const region of regions) {
      const rows = facilities.value.filter((f) => f.regionId === region.id)
      const blocked = rows.filter((f) => stateLabel(f) === '통제').length
      const unknown = rows.filter((f) => !['정상', '주의', '통제'].includes(stateLabel(f))).length
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'map-region-label'
      const caption =
        !loaded.value || listError.value || statusError.value
          ? '상태 미확인'
          : !rows.length
            ? '등록 시설 없음'
            : `통제 ${blocked} · 미확인 ${unknown}`
      const shortName: Record<string, string> = {
        seoul: '서울',
        busan: '부산',
        daegu: '대구',
        incheon: '인천',
        daejeon: '대전',
        ulsan: '울산',
        sejong: '세종',
        gyeonggi: '경기',
        gangwon: '강원',
        chungbuk: '충북',
        chungnam: '충남',
        jeonbuk: '전북',
        'jeonnam-gwangju': '광주·전남',
        gyeongbuk: '경북',
        gyeongnam: '경남',
        jeju: '제주',
      }
      button.textContent = shortName[region.id] || region.name
      button.title = `${region.name} · ${caption}`
      button.setAttribute('aria-label', `${region.name} 현황 보기`)
      // 전국 보기에서 붙어 있는 라벨을 화면 기준으로 살짝 벌립니다.
      const labelShift: Record<string, string> = {
        chungnam: 'translate(-30px, 0)',
        chungbuk: 'translate(12px, -6px)',
        daejeon: 'translate(10px, 20px)',
        jeonbuk: 'translate(-18px, 4px)',
        ulsan: 'translate(10px, -4px)',
        busan: 'translate(4px, 4px)',
      }
      button.style.transform = labelShift[region.id] || ''
      button.addEventListener('click', () => chooseRegion(region.id))
      overlays.push(
        new maps.CustomOverlay({
          map,
          position: new maps.LatLng(region.latitude, region.longitude),
          content: button,
          // Incheon label is centred on its actual coordinates; no westward offset.
          xAnchor: region.id === 'gyeonggi' ? -0.2 : 0.5,
          yAnchor: 0.5,
        }),
      )
    }
  } else {
    for (const row of filtered.value) {
      if (row.latitude === null || row.longitude === null) continue
      const state = stateLabel(row)
      const marker = new maps.Marker({
        map,
        position: new maps.LatLng(row.latitude, row.longitude),
        title: `${row.name}${props.manager ? (ownsFacility(row) ? ' · 담당 시설' : ' · 담당 외 시설') : ''} · ${state}`,
        image: markerImage(state, !props.manager || ownsFacility(row), selectedId.value === row.id),
        zIndex: selectedId.value === row.id ? 12 : markerStyle(state).zIndex,
      })
      const click = () => selectFacility(row)
      maps.event.addListener(marker, 'click', click)
      markers.push({ id: row.id, marker, click })
    }
    clusterer?.clear()
    clusterer?.addMarkers(markers.map((m) => m.marker))
    openSelectedPopup()
  }
}
// 지역명 없는 좌표는 카카오 행정구역 조회로 보완합니다. 임의로 가까운 시·도에 배정하지 않습니다.
async function resolveRegions() {
  if (!geocoder || !alive) return
  const version = ++regionLookupNumber
  const pending = facilities.value.filter(
    (f) => !f.regionId && f.latitude !== null && f.longitude !== null,
  )
  if (!pending.length) {
    locating.value = false
    return
  }
  locating.value = true
  let cursor = 0
  async function worker() {
    while (cursor < pending.length && alive && version === regionLookupNumber) {
      const row = pending[cursor++]!
      const cacheKey = `${row.latitude},${row.longitude}`
      const region = await new Promise<string>((resolve) => {
        const timer = window.setTimeout(() => resolve(''), 3000)
        geocoder.coord2RegionCode(row.longitude, row.latitude, (result: Json[], status: string) => {
          clearTimeout(timer)
          const admin = result?.find((r) => r.region_type === 'H') || result?.[0]
          resolve(
            status === maps.services.Status.OK && admin
              ? findRegion({ regionName: admin.region_1depth_name })
              : '',
          )
        })
      })
      if (region) {
        regionCache.set(cacheKey, region)
        if (alive && version === regionLookupNumber) {
          const current = facilities.value.find((f) => f.id === row.id)
          if (current) current.regionId = region
        }
      }
    }
  }
  await Promise.all([worker(), worker(), worker()])
  if (alive && version === regionLookupNumber) {
    locating.value = false
    drawMap()
  }
}
let sdkErrorCleanup: (() => void) | undefined
async function initializeMap() {
  try {
    await loadKakao() // 카카오 SDK, 실패하면 OpenStreetMap 호환 계층으로 자동 대체
  } catch {
    mapError.value = '지도를 불러오지 못했습니다. 아래 목록을 이용해 주세요.'
    return
  }
  const start = Date.now()
  sdkPoll = window.setInterval(() => {
    if (!alive) {
      clearInterval(sdkPoll)
      return
    }
    const sdk = (window as unknown as { kakao?: any }).kakao
    if (sdk?.maps?.load) {
      clearInterval(sdkPoll)
      sdkWatchdog = window.setTimeout(() => {
        if (alive && !map) mapError.value = '지도 응답이 지연되고 있습니다. 목록을 이용해 주세요.'
      }, 10000)
      sdk.maps.load(() => {
        clearTimeout(sdkWatchdog)
        if (!alive || !mapElement.value) return
        try {
          mapError.value = ''
          maps = sdk.maps
          map = new maps.Map(mapElement.value, {
            center: new maps.LatLng(
              props.manager || appMap.value ? 35.1796 : 36.3,
              props.manager || appMap.value ? 129.0756 : 127.8,
            ),
            level: props.manager || appMap.value ? 7 : 12,
            scrollwheel: false,
          })
          disposeMapScroll = pageFriendlyMapScroll(mapElement.value, map,
            /^\/(admin|customer)-app\//.test(currentRoute.path))
          trafficMaps.value = maps
          trafficMap.value = map
          clusterer = new maps.MarkerClusterer({
            map,
            averageCenter: true,
            minLevel: 0,
            gridSize: 70,
            disableClickZoom: true,
            styles: markerStyles.map((style) => clusterStyle(style.label)),
          })
          maps.event.addListener(clusterer, 'clusterclick', selectCluster)
          maps.event.addListener(clusterer, 'clustered', styleClusters)
          infoWindow = new maps.InfoWindow({ removable: true, disableAutoPan: true, zIndex: 10 })
          clusterInfoWindow = new maps.InfoWindow({
            removable: false,
            disableAutoPan: false,
            zIndex: 8,
          })
          geocoder = maps.services ? new maps.services.Geocoder() : undefined
          fitNation()
          initialFacilityBoundsApplied = facilities.value.length > 0
          maps.event.addListener(map, 'zoom_changed', handleZoomChanged)
          maps.event.addListener(map, 'click', dismissMapPopups)
          drawMap()
          void resolveRegions()
          observer = new ResizeObserver(() => {
            const center = map.getCenter()
            map.relayout()
            clusterer?.redraw()
            const row = selected.value
            map.setCenter(
              row?.latitude != null && row?.longitude != null
                ? new maps.LatLng(row.latitude, row.longitude)
                : center,
            )
          })
          observer.observe(mapElement.value)
        } catch {
          mapError.value = '지도를 표시할 수 없습니다. 아래 목록을 이용해 주세요.'
        }
      })
    } else if (Date.now() - start > 10000) {
      clearInterval(sdkPoll)
      mapError.value =
        '지도 로딩 시간이 초과되었습니다. 카카오 JavaScript 키와 등록 주소를 확인한 뒤 새로고침해 주세요.'
    }
  }, 100)
}
function handleVisibility() {
  if (!document.hidden) {
    now.value = Date.now()
  }
}
watch([filtered, regionId, selectedId, now, listError, statusError], drawMap)
watch(clusterFacilityIds, () => styleClusters(styledClusters))
watch([regionId, district, query, statusFilter], () => {
  closeClusterPopup()
  clusterFacilityIds.value = []
})
watch(selected, async (facility) => {
  if (!facility || props.manager) return
  await nextTick()
  customerRoot.value?.dispatchEvent(
    new CustomEvent('map-weather-location', {
      detail: {
        lat: facility.latitude ?? 35.1796,
        lng: facility.longitude ?? 129.0756,
        name: facility.name,
      },
    }),
  )
})
watch(filtered, (rows) => {
  const ids = clusterFacilityIds.value.filter((id) => rows.some((f) => f.id === id))
  if (ids.length !== clusterFacilityIds.value.length) clusterFacilityIds.value = ids
  if (props.manager && selectedId.value && !rows.some((row) => row.id === selectedId.value))
    closeFacility()
})
let streamRefresh: ReturnType<typeof setTimeout> | undefined
const streamBuffer: BackendEvent[] = []
function updateFromStream(event: BackendEvent) {
  const update = (row: Facility): Facility => {
    if (row.id !== String(event.data.underpassId)) return row
    const raw = patchFacility(
      {
        ...row,
        currentStatus:
          row.controlled === true ? 'BLOCKED' : row.controlled === false ? 'NORMAL' : undefined,
        currentRiskLevel: row.riskLevel,
      },
      event,
    )
    const controlled =
      raw.currentStatus === 'BLOCKED' ? true : raw.currentStatus === 'NORMAL' ? false : undefined
    return {
      ...row,
      riskLevel: raw.currentRiskLevel,
      controlled,
      status: controlled ? '통제' : statusLabel(raw.currentRiskLevel),
      riskScore: raw.riskScore,
      currentWaterCm: raw.currentWaterCm,
      rainfallMm: raw.rainfallMm,
      updatedAt: raw.updatedAt,
    }
  }
  facilities.value = facilities.value.map(update)
  if (detail.value) detail.value = update(detail.value)
  if (event.name === 'risk-update' && selectedId.value === String(event.data.underpassId))
    riskHistory.value = appendRiskHistory(riskHistory.value, event.data)
  now.value = Date.now()
}
function replayStreamBuffer() {
  if (loading.value || detailLoading.value) return
  for (const event of streamBuffer.splice(0)) updateFromStream(event)
}
useBackendEvents((event) => {
  const { name, data } = event
  if (facilityEvents.includes(name)) {
    if (loading.value || detailLoading.value) {
      streamBuffer.push(event)
      if (streamBuffer.length > 256) streamBuffer.shift()
    }
    updateFromStream(event)
    return
  }
  if (name === 'data-changed' && data.resource === 'risk-forecast') {
    if (selectedId.value === String(data.id) && detailAbort && !detailAbort.signal.aborted)
      void fetchRiskForecast(selectedId.value, detailAbort.signal)
    return
  }
  if (
    name === 'connected' ||
    name === 'transit-update' ||
    name.startsWith('plate-queue-') ||
    (name === 'data-changed' && ['underpass', 'camera', 'threshold'].includes(data.resource))
  ) {
    clearTimeout(streamRefresh)
    streamRefresh = setTimeout(() => {
      if (alive) void refresh()
    }, 250)
  }
})
onMounted(() => {
  sizeCustomerMap()
  if (props.mapOnly && customerRoot.value) {
    const root = customerRoot.value
    const toolbar = root.querySelector<HTMLElement>('.map-overlay-toolbar')
    if (toolbar) {
      toolbarObserver = new ResizeObserver(() => root.style.setProperty('--admin-map-toolbar-height', `${Math.ceil(toolbar.getBoundingClientRect().bottom - root.getBoundingClientRect().top - 12)}px`))
      toolbarObserver.observe(toolbar)
    }
    const sheet = root.querySelector<HTMLElement>('.admin-facility-sheet')
    if (adminAppMap.value && sheet) {
      adminSheetObserver = new ResizeObserver(() => root.style.setProperty('--admin-sheet-actual-height', `${sheet.getBoundingClientRect().height}px`))
      adminSheetObserver.observe(sheet)
    }
  }
  viewportObserver = new ResizeObserver(sizeCustomerMap)
  const header = document.querySelector('.customer-header')
  if (header) viewportObserver.observe(header)
  const breadcrumb = document.querySelector('.customer-breadcrumb')
  if (breadcrumb) viewportObserver.observe(breadcrumb)
  window.addEventListener('resize', sizeCustomerMap)
  void initializeMap()
  void refresh()
  document.addEventListener('visibilitychange', handleVisibility)
  document.addEventListener('pointerdown', dismissPopupOutside, { passive: true })
  document.addEventListener('pointerdown', collapseSheetOutside, { capture: true, passive: true })
})
onBeforeUnmount(() => {
  toolbarObserver?.disconnect()
  adminSheetObserver?.disconnect()
  clearTimeout(streamRefresh)
  listObserver?.disconnect()
  viewportObserver?.disconnect()
  window.removeEventListener('resize', sizeCustomerMap)
  userLocation?.setMap(null)
  disposeMapScroll?.()
  if (clusterer) {
    maps.event.removeListener(clusterer, 'clusterclick', selectCluster)
    maps.event.removeListener(clusterer, 'clustered', styleClusters)
  }
  clusterer?.clear()
  weatherAbort?.abort()
  alive = false
  requestNumber++
  regionLookupNumber++
  listAbort?.abort()
  detailAbort?.abort()
  clearTimeout(sdkWatchdog)
  sdkErrorCleanup?.()
  clearInterval(sdkPoll)
  observer?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibility)
  document.removeEventListener('pointerdown', dismissPopupOutside)
  document.removeEventListener('pointerdown', collapseSheetOutside, true)
  infoWindow?.close()
  closeClusterPopup()
  if (map) {
    maps.event.removeListener(map, 'zoom_changed', handleZoomChanged)
    maps.event.removeListener(map, 'click', dismissMapPopups)
    removeMarkers()
  }
})
</script>

<template>
  <main v-if="props.mapOnly" ref="customerRoot" class="map-only-live" :class="{'admin-app-map':adminAppMap}"
    :style="adminAppMap?{'--map-sheet-height':sheetHeight!==null?sheetHeight+'px':sheetOpen?'72%':'68px'}:undefined"
    ><div ref="mapElement" class="customer-map-canvas" aria-label="카카오 지도"></div>
    <form v-if="adminAppMap" class="admin-map-search" @submit.prevent="sheetOpen=true;searchFacilities()">
      <input v-model.trim="query" type="search" placeholder="지하차도 이름 검색" aria-label="지하차도 이름 검색" /><button type="submit">검색</button>
    </form>
    <div class="map-overlay-toolbar"><ul class="map-only-legend" aria-label="마커 등급 안내"
      ><li v-for="style in markerLegendStyles" :key="style.label"
        ><button type="button" class="legend-filter-button" :aria-pressed="legendFilter===style.label" :class="{'legend-muted':legendFilter&&legendFilter!==style.label}" @click="toggleLegend(style.label)"><i :style="{background:style.color}"></i>{{style.label}}</button></li
      ><li v-if="props.manager"><span style="background:#9aa3ae"></span>담당 외 시설</li></ul>
      <TrafficMapLayers :map="trafficMap" :maps="trafficMaps" :toggle-controls="adminAppMap" /></div>
    <div class="admin-map-control-dock">
      <button v-if="adminAppMap" type="button" class="map-control-toggle" :aria-expanded="mapControlsExpanded" aria-label="지도 조작 접기·펼치기" @click="mapControlsExpanded=!mapControlsExpanded">지도 <span aria-hidden="true">{{mapControlsExpanded?'›':'‹'}}</span></button>
    <div v-show="!adminAppMap||mapControlsExpanded" class="kakao-map-controls"
      ><button
        type="button"
        class="map-fit-all"
        aria-label="지도 전체 보기"
        title="부산 전체 지하차도 보기"
        @click="showAllFacilities"
        >전체<br />보기</button
      ><button class="ui-icon-button" type="button" aria-label="지도 확대" @click="zoomMap(-1)">
          <UiIcon name="plus" />
        </button
      ><button class="ui-icon-button" type="button" aria-label="지도 축소" @click="zoomMap(1)">
          <UiIcon name="minus" />
        </button></div></div
    ><p v-if="mapError" class="map-error-note" role="status">{{mapError}}</p
    ><div v-if="selected" class="map-only-selection"
      ><RouterLink :to="'/admin-app/screen-5?id='+selected.id"
        >{{selected.name}} · 시설 상세 →</RouterLink
      ><button class="ui-icon-button" type="button" aria-label="선택 닫기" @click="selectedId='';detail=null"
        >
          <UiIcon name="close" />
        </button
      ></div
    >
    <aside class="admin-facility-sheet" :class="{'is-dragging':sheetHeight!==null}" :style="sheetStyle" aria-label="지하차도 시설 목록">
      <button type="button" class="admin-sheet-handle" :aria-expanded="sheetOpen" aria-controls="admin-map-facilities"
        @pointerdown="startSheet" @pointermove="moveSheet" @pointerup="endSheet" @pointercancel="endSheet" @lostpointercapture="endSheet" @click="toggleSheet">
        {{sheetOpen?'시설 목록 접기':'위로 밀어 시설 목록 보기'}} · {{filtered.length}}곳
      </button>
      <div v-show="sheetOpen||sheetHeight!==null" id="admin-map-facilities" class="admin-sheet-content">
        <input v-model.trim="query" type="search" placeholder="지하차도 이름 검색" aria-label="시설 이름 검색" />
        <p v-if="listError" role="alert">{{listError}}</p>
        <p v-else-if="loading&&!facilities.length" role="status">시설을 불러오는 중입니다.</p>
        <p v-else-if="!filtered.length" role="status">조회 조건에 맞는 시설이 없습니다.</p>
        <ul>
          <li v-for="row in filtered" :key="row.id" :class="{'is-selected':selectedId===row.id}">
            <button type="button" class="admin-sheet-facility" @click="selectFacility(row)">
              <RiskLevelBadge :value="displayState(row)" />
              <span><strong>{{row.name}}</strong><small>{{controlLabel(row)}} · {{row.address||row.assignedRegionName||'주소 미제공'}}</small></span>
            </button>
            <RouterLink :to="'/admin-app/screen-5?id='+row.id" :aria-label="row.name+' 상세보기'">상세 ›</RouterLink>
          </li>
        </ul>
      </div>
    </aside>
    </main
  >
 <main
    v-else-if="!props.manager"
    ref="customerRoot"
    class="wire-page page-c-map legacy-live-map"
    :class="{'mobile-app-map':appMap,'mobile-app-detail':appDetail,'map-sheet-open':sheetOpen||sheetHeight!==null&&sheetHeight>130}"
    :style="appMap?{'--map-sheet-height':sheetHeight!==null?sheetHeight+'px':sheetOpen?'var(--map-expanded-sheet-height,72%)':'68px'}:undefined"
  >
    <div class="app map-swap-layout map-columns">
      <form
        v-if="appMap"
        class="app-map-search"
        role="search"
        @submit.prevent="sheetOpen=true;searchFacilities()"
        ><input
          v-model.trim="query"
          type="search"
          aria-label="지하차도 이름 검색"
          placeholder="지하차도 이름 검색"
        /><button type="submit">검색</button></form
      >
   <section class="mapw" aria-label="지하차도 지도">
        <div ref="mapElement" class="customer-map-canvas" aria-label="카카오 지도"></div>
    <div class="mtop"
          ><div class="pill" aria-label="마커 색상 안내"
            ><span v-for="style in markerLegendStyles" :key="style.label"
              ><button type="button" class="legend-filter-button" :aria-pressed="legendFilter===style.label" :class="{'legend-muted':legendFilter&&legendFilter!==style.label}" @click="toggleLegend(style.label)"><i :style="{background:style.color}"></i>{{style.label}}</button></span
            ><span><i style="background:#1671d9"></i>내 위치</span></div
          ><TrafficMapLayers :map="trafficMap" :maps="trafficMaps" toggle-controls /></div>
    <div class="customer-map-control-dock">
      <button type="button" class="map-control-toggle" :aria-expanded="mapControlsExpanded" aria-label="지도 조작 접기·펼치기" @click="mapControlsExpanded=!mapControlsExpanded">지도 <span aria-hidden="true">{{mapControlsExpanded?'›':'‹'}}</span></button>
    <div v-show="mapControlsExpanded" class="kakao-map-controls" aria-label="지도 조작"
          ><button
            type="button"
            class="map-fit-all"
            aria-label="지도 전체 보기"
            title="부산 전체 지하차도 보기"
            @click="showAllFacilities"
            >전체<br />보기</button
          ><button class="ui-icon-button" type="button" aria-label="지도 확대" @click="zoomMap(-1)">
          <UiIcon name="plus" />
        </button
          ><button class="ui-icon-button" type="button" aria-label="지도 축소" @click="zoomMap(1)">
          <UiIcon name="minus" />
        </button
          ><button
            v-if="!props.manager"
            type="button"
            aria-label="내 위치 찾기"
            :disabled="findingPosition"
            @click="locateUser"
            ><UiIcon name="location" /></button
        ></div></div>
    <div class="map-left-tools"><MapWeatherWidget /></div>

    <div v-if="mapError" class="map-error-note" role="status">{{mapError}}</div>
    <div v-if="selected&&selected.status==='통제'" class="alertbar" role="status"
          ><strong>■ 통제</strong><span>{{selected.name}} 통제 중입니다.</span></div
        >
      </section>
   <aside
        v-if="selected"
        class="panel swapped-detail"
        aria-label="선택한 지하차도"
        :class="{'app-map-sheet':appMap,'is-collapsed':appMap&&!sheetOpen,'is-dragging':sheetHeight!==null}"
        :style="sheetStyle"
        ><button
          v-if="appMap"
          class="mobile-sheet-handle"
          type="button"
          :aria-expanded="sheetOpen"
          @pointerdown="startSheet"
          @pointermove="moveSheet"
          @pointerup="endSheet"
          @pointercancel="endSheet"
          @lostpointercapture="endSheet"
          @click="toggleSheet"
          >{{sheetOpen?'목록 접기':'목록 펼치기'}} · {{listRows.length}}곳</button
        >
    <button type="button" class="btn back-to-list" @click="closeFacility">목록으로</button>
    <div ref="detailRegion" class="det" tabindex="-1" aria-live="polite">
          <div class="sub">{{formatTime(selected.updatedAt)}} 기준</div>
     <div
            ><h2 class="nm">{{selected.name}}</h2
            ><div class="sub">{{selected.address||'주소 미제공'}}</div></div
          >
     <p v-if="detailLoading" class="sub">최신 상태를 조회 중입니다.</p
          ><p v-if="detailError" role="alert">{{detailError}}</p>
     <div class="detail-badges"
            ><RiskLevelBadge :value="selected.riskLevel||selected.status" /><span
              class="lvb"
              >{{controlLabel(selected)}}</span
            ></div
          >
     <div class="kv"
            ><div
              ><small>위험 점수</small
              ><strong
                >{{selected.riskScore===null?'—':selected.riskScore.toFixed(1)+'점'}}</strong
              ></div
            ><div
              ><small>통제 상태</small><strong>{{controlLabel(selected)}}</strong></div
            ><div
              ><small>현재 수위</small
              ><strong
                >{{selected.currentWaterCm==null?'—':selected.currentWaterCm+' cm'}}</strong
              ></div
            ></div
          >
     <button
            type="button"
            class="btn"
            :aria-expanded="expanded"
            @click="expanded=!expanded"
            >{{expanded?'상세 접기 ▴':'상세 펼치기 ▾'}}</button
          >
     <div v-show="expanded" class="detail-extended">
            <div class="kv"
              ><div
                ><small>현재 강우량</small
                ><strong>{{selected.rainfallMm===null?'—':selected.rainfallMm+' mm'}}</strong></div
              ><div
                ><small>인근 날씨</small
                ><strong
                  class="weather-value"
                  >{{weatherLoading?'조회 중':compactWeather?.temperature!=null?compactWeather.temperature+'°C':compactWeather?.condition||'—'}}</strong
                ></div
              ></div
            >
      <div class="route"
              ><h2 class="trend-title">위험도 추이</h2
              ><svg
                v-if="riskPoints||riskSeries.forecast"
                viewBox="0 0 300 130"
                class="risk-history-chart"
                role="img"
                aria-label="최근 관측 위험도 추이"
              >
                <path d="M20 10V110H280" fill="none" stroke="#c9ced6" />
                <line
                  v-if="riskSeries.boundary!==null"
                  :x1="riskSeries.boundary"
                  :x2="riskSeries.boundary"
                  y1="10"
                  y2="110"
                  stroke="#c9ced6"
                  stroke-dasharray="2 3"
                />
                <text
                  v-for="tick in riskSeries.ticks"
                  :key="tick.x"
                  :x="tick.x"
                  y="126"
                  text-anchor="middle"
                  font-size="9"
                  fill="currentColor"
                >
                  {{tick.label}}
                </text>
                <circle
                  v-if="riskSeries.lastObserved"
                  :cx="riskSeries.lastObserved.x"
                  :cy="riskSeries.lastObserved.y"
                  r="3"
                  fill="#225f9b"
                >
                  <title>
                    {{riskSeries.lastObserved.label}} &#183; {{riskSeries.lastObserved.score}}
                  </title>
                </circle>
                <path
                  v-if="riskSeries.carryPath"
                  :d="riskSeries.carryPath"
                  fill="none"
                  stroke="#225f9b"
                  stroke-opacity=".5"
                  stroke-width="2"
                  stroke-dasharray="2 3"
                />
                <path
                  :d="riskSeries.observedPath"
                  fill="none"
                  stroke="#225f9b"
                  stroke-width="3"
                  stroke-linejoin="round"
                />
                <path
                  v-if="riskSeries.forecastPath"
                  :d="riskSeries.forecastPath"
                  fill="none"
                  stroke="#225f9b"
                  stroke-width="3"
                  stroke-dasharray="6 4"
                />
                <text x="0" y="15" font-size="10" fill="currentColor">100</text>
                <text x="6" y="113" font-size="10" fill="currentColor">0</text></svg
              ><p
                v-else
                class="sub"
                >{{detailLoading?'관측 정보를 조회 중입니다.':'표시할 관측 정보가 없습니다.'}}</p
              ><p v-if="riskSeries.forecast" class="sub">실선: 관측 · 점선: 향후 3시간 예측</p
              ><p v-if="riskSeries.carryPath" class="sub"
                >엷은 점선: 최근 관측값을 현재까지 유지 (추가 관측 아님)</p
              ><p v-if="forecastError" class="sub">{{forecastError}}</p></div
            >
      <RouterLink v-if="appMap" :to="'/customer-app/screen-6?id='+selected.id"
              >상세 화면 보기 →</RouterLink
            ><div class="sub app-guidance"
              >시간대별 예보·길 안내는 <b>앱</b>에서 확인해요. <RouterLink to="/info/app-guide#install">앱 다운로드 →</RouterLink></div
            >
          </div>
        </div>
      </aside>
   <aside
        v-else
        class="panel facility-list-panel"
        aria-label="지하차도 목록"
        :class="{'app-map-sheet':appMap,'is-collapsed':appMap&&!sheetOpen,'is-dragging':sheetHeight!==null}"
        :style="sheetStyle"
        ><button
          v-if="appMap"
          class="mobile-sheet-handle"
          type="button"
          :aria-expanded="sheetOpen"
          @pointerdown="startSheet"
          @pointermove="moveSheet"
          @pointerup="endSheet"
          @pointercancel="endSheet"
          @lostpointercapture="endSheet"
          @click="toggleSheet"
          >{{sheetOpen?'목록 접기':'목록 펼치기'}} · {{listRows.length}}곳</button
        >
    <div class="ph"
          ><h2
            >지하차도 <span class="sub">부산 {{district||'전체'}} · {{filtered.length}}곳</span></h2
          ><select
            :value="district"
            aria-label="부산 구·군 선택"
            @change="chooseDistrict(($event.target as HTMLSelectElement).value)"
            ><option value="">부산 전체</option
            ><option
              v-for="name in availableDistricts"
              :key="name"
              :value="name"
              >{{name}}</option
            ></select
          ><input
            v-model.trim="query"
            type="search"
            placeholder="지하차도 이름 검색"
            aria-label="검색"
          /><div class="seg"
            ><button
              v-for="[label,value] in [['전체',''],['위험','위험'],['주의','경고'],['안전','정상']]"
              :key="label"
              type="button"
              class="chip"
              :aria-pressed="statusFilter===value"
              @click="statusFilter=value!"
              >{{label}}</button
          ><button
            type="button"
            class="btn refresh-facilities"
            :disabled="loading"
            @click="searchFacilities"
            >{{loading?'조회 중…':'새로고침'}}</button
          ></div></div
        >
    <p v-if="listError" class="facility-message" role="alert">{{listError}}</p
        ><p v-else-if="loading&&!facilities.length" class="facility-message" role="status"
          >지하차도를 불러오는 중입니다.</p
        >
    <template v-if="clusterFacilityIds.length"
          ><div class="cluster-list-heading"
            ><strong>묶음 시설 {{clusterRows.length}}곳</strong
            ><button type="button" class="btn" @click="clusterFacilityIds=[]"
              >전체 목록으로</button
            ></div
          ></template
        >
    <ul
          class="lst"
          :class="{'filled-page':!appMap&&shown.length===pageSize}"
          :data-page-size="pageSize"
          ><li v-for="row in shown" :key="row.id"
            ><button type="button" class="it" @click="selectFacility(row)"
              ><span class="lvb" :class="badgeClass(row)">{{displayState(row)}}</span
              ><span
                ><strong>{{row.name}}</strong
                ><small
                  >{{controlLabel(row)}} · {{row.address||row.assignedRegionName||'주소 미제공'}}</small
                ></span
              ></button
            ></li
          ><li v-if="!loading&&!listError&&!filtered.length" class="facility-message"
            >조회 조건에 맞는 지하차도가 없습니다.</li
          ></ul
        >
    <nav
          v-if="!appMap&&listRows.length"
          class="customer-list-pagination"
          aria-label="지하차도 목록 페이지"
          ><div class="list-page-buttons"
            ><button type="button" :disabled="page===1" @click="changePage(page-1)">이전</button
            ><button
              v-for="number in pageNumbers"
              :key="number"
              type="button"
              :aria-label="number+'페이지'"
              :aria-current="page===number?'page':undefined"
              @click="changePage(number)"
              >{{number}}</button
            ><button type="button" :disabled="page===pages" @click="changePage(page+1)"
              >다음</button
            ></div
          ><small>{{page}} / {{pages}} 페이지 · 전체 {{listRows.length}}개</small></nav
        >
      </aside>
    </div>
  </main>
  <main v-else class="wire-page page-c-live-map manager-live-map">
    <form class="underpass-map-form" @submit.prevent="searchFacilities"
      ><label
        >부산 구·군<select
          :value="district"
          aria-label="부산 구·군 선택"
          @change="chooseDistrict(($event.target as HTMLSelectElement).value)"
          ><option value="">부산 전체</option
          ><option
            v-for="name in availableDistricts"
            :key="name"
            :value="name"
            >{{name}}</option
          ></select
        ></label
      ><label class="underpass-map-label-2"
        >지하차도명·주소<input
          v-model.trim="query"
          type="search"
          aria-label="지하차도명·주소 검색" /></label
      ><label
        >상태<select v-model="statusFilter"
          ><option value="">전체 상태</option
          ><option>정상</option
          ><option>경고</option
          ><option>위험</option
          ><option>통제</option></select
        ></label
      ><button type="submit" :disabled="loading">{{loading?'조회 중…':'새로고침'}}</button
      ><button type="button" @click="focusAssignedRegion">담당 지역 보기</button
      ><button type="button" @click="showAllFacilities">부산 전체 보기</button></form
    >
<p
      v-if="listError||statusError"
      class="manager-map-message"
      role="alert"
      >{{listError||statusError}}</p
    ><p v-else-if="loaded&&!filtered.length" class="manager-map-message" role="status"
      >조회 조건에 맞는 지하차도가 없습니다.</p
    >
<div class="manager-map-stage"
      ><div ref="mapElement" class="underpass-map-div" aria-label="카카오 지하차도 지도"></div>
      <div class="map-overlay-toolbar"><ul class="underpass-map-legend manager-map-legend" aria-label="마커 색상 안내"
        ><li v-for="style in markerLegendStyles" :key="style.label"
          ><button type="button" class="legend-filter-button" :aria-pressed="legendFilter===style.label" :class="{'legend-muted':legendFilter&&legendFilter!==style.label}" @click="toggleLegend(style.label)"><i :style="{background:style.color}"></i>{{style.label}}</button></li
        ><li
          ><span style="width:10px;height:10px;background:#9aa3ae;border-radius:50%"></span>담당 외 시설</li
        ></ul><TrafficMapLayers :map="trafficMap" :maps="trafficMaps" /></div>
      <p v-if="mapError" class="manager-sdk-error" role="status">{{mapError}}</p>
<ManagerMapPanel
        v-model:expanded="managerPanelExpanded"
        :dashboard-visual="props.dashboardVisual"
        :facility="selected"
        :cluster="clusterRows"
        :state="selected?stateLabel(selected):''"
        :risk-level="selected?.riskLevel"
        :control="selected?controlLabel(selected):''"
        :observed="selected?formatTime(selected.updatedAt):''"
        :loading="detailLoading"
        :error="detailError"
        :weather="compactWeather||undefined"
        :weather-loading="weatherLoading"
        :weather-error="weatherError"
        :risk-points="riskPoints"
        :forecast-points="riskSeries.forecast"
        :chart-series="riskSeries"
        :forecast-error="forecastError"
        :can-control="ownsFacility(selected)"
        @select="selectManagerRow"
        @clear="closeFacility"
        @control="managerControl"
      />
<div class="kakao-map-controls manager-map-controls" aria-label="지도 조작"
        ><button
          type="button"
          class="map-fit-all"
          aria-label="지도 전체 보기"
          title="부산 전체 지하차도 보기"
          @click="showAllFacilities"
          >전체<br />보기</button
        ><button class="ui-icon-button" type="button" aria-label="카카오 지도 확대" @click="zoomMap(-1)">
          <UiIcon name="plus" />
        </button
        ><button class="ui-icon-button" type="button" aria-label="카카오 지도 축소" @click="zoomMap(1)">
          <UiIcon name="minus" />
        </button
        ><button class="ui-icon-button"
          v-if="!props.manager"
          type="button"
          aria-label="내 위치 찾기"
          :disabled="findingPosition"
          @click="locateUser"
          >
          <UiIcon name="location" />
        </button
        ></div
      ></div
    >
<BackendResource
      v-if="selectedId"
      ref="managerOperations"
      embedded
      operations-only
      resource-key="facilities"
      @saved="afterManagerControl"
    />
  </main>
</template>

<style src="../styles/pages/c-live-map.css"></style>
