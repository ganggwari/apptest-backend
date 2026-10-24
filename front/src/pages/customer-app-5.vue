<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import CustomerAppNav from '../components/CustomerAppNav.vue'
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useApp } from '../stores/app'
import { makeContext } from '../runtime/context'
import { mountClusters } from '../runtime/mapClusters'
import { navigationRequest } from '../services/navigation'
import seeds from '../data/c-map.json'
import { facilityDistricts } from '../data/busan-facilities'
import BusanCctvModal from '../components/BusanCctvModal.vue'
type Weather = {
  temperature: number | null
  humidity: number | null
  rainfall: number | null
  condition: string
  observedAt: string
}
const liveWeather = ref<Weather | null>(null),
  weatherLoading = ref(false),
  weatherError = ref('')
let weatherRequest: AbortController | undefined
let weatherLocation = { lat: 35.1796, lng: 129.0756, name: '부산' }
async function openWeather() {
  weather.value = !weather.value
  if (!weather.value) return
  weatherRequest?.abort()
  const request = new AbortController()
  weatherRequest = request
  weatherLoading.value = true
  weatherError.value = ''
  liveWeather.value = null
  try {
    const result = await navigationRequest<Weather>(
      'weather',
      { lat: String(weatherLocation.lat), lng: String(weatherLocation.lng) },
      request.signal,
    )
    if (!request.signal.aborted) liveWeather.value = result
  } catch (e) {
    if (!request.signal.aborted) weatherError.value = (e as Error).message
  } finally {
    if (!request.signal.aborted) weatherLoading.value = false
  }
}
function weatherChanged(event: Event) {
  weatherLocation = (event as CustomEvent).detail
  if (weather.value) {
    weather.value = false
    void openWeather()
  }
}
const cctvOpen = ref(false)
import '../styles/customer-app-5.css'
import '../styles/customer-map-sheet.css'
const root = ref<HTMLElement>(),
  router = useRouter(),
  store = useApp(),
  weather = ref(false),
  collapsed = ref(true),
  district = ref(''),
  query = ref(''),
  risk = ref(''),
  selected = ref<number | null>(null),
  scroll = ref(0),
  list = ref<HTMLElement>(),
  status = ref('')
const cluster = ref<number[] | null>(null)
const facilities = seeds.F,
  shown = computed(() =>
    facilities
      .map((f, i) => ({ f, i }))
      .filter(
        ({ f }) =>
          (!district.value || facilityDistricts[String(f[0])] === district.value) &&
          (!query.value || String(f[0]).includes(query.value)) &&
          (!risk.value || f[1] === risk.value),
      ),
  )
const listed = computed(() =>
  shown.value.filter((x) => !cluster.value || cluster.value.includes(x.i)),
)
const current = computed(() => (selected.value === null ? null : facilities[selected.value])),
  districts = [...new Set(facilities.map((f) => facilityDistricts[String(f[0])]))]
    .filter((name): name is string => !!name && name !== '구 확인 필요')
const stage = ref<HTMLElement>(),
  dragHeight = ref<number | null>(null)
const sheetStyle = computed(() =>
  dragHeight.value === null ? {} : { height: `${dragHeight.value}px` },
)
let dispose: (() => void) | undefined,
  startY = 0,
  startHeight = 0,
  moved = false,
  suppressClick = false
function beginSwipe(e: PointerEvent) {
  if (e.pointerType === 'mouse' && e.button !== 0) return
  suppressClick = false
  startY = e.clientY
  startHeight = collapsed.value ? 68 : (stage.value?.clientHeight || 600) * 0.72
  moved = false
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}
function moveSwipe(e: PointerEvent) {
  if (!(e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) return
  const delta = startY - e.clientY
  if (Math.abs(delta) > 6) moved = true
  if (moved)
    dragHeight.value = Math.max(
      68,
      Math.min((stage.value?.clientHeight || 600) * 0.88, startHeight + delta),
    )
}
function endSwipe(e: PointerEvent) {
  if (!(e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) return
  ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  if (moved) {
    collapsed.value = e.clientY - startY > 0
    suppressClick = true
  }
  dragHeight.value = null
}
function toggleSheet() {
  if (suppressClick) {
    suppressClick = false
    return
  }
  collapsed.value = !collapsed.value
}
function cancelSwipe() {
  dragHeight.value = null
  moved = false
  suppressClick = false
}
function pick(index: number) {
  if (!shown.value.some((x) => x.i === index)) return
  scroll.value = list.value?.scrollTop || 0
  selected.value = index
  store.selectedFacility = index
  collapsed.value = false
  root.value?.dispatchEvent(new CustomEvent('facility-focus', { detail: { index } }))
}
function searchFacilities() {
  cluster.value = null
  selected.value = null
  collapsed.value = false
  filter()
  nextTick(() => {
    if (list.value) list.value.scrollTop = 0
  })
}
async function back() {
  selected.value = null
  await nextTick()
  if (list.value) list.value.scrollTop = scroll.value
}
function filter() {
  if (selected.value !== null && !shown.value.some((x) => x.i === selected.value))
    selected.value = null
  nextTick(() => root.value?.dispatchEvent(new Event('facility-filter')))
}
onMounted(() => {
  if (!root.value) return
  const runtime = makeContext(root.value, router)
  runtime.ctx.on(root.value, 'map-weather-location', weatherChanged)
  runtime.ctx.store.data[`${store.roleKey}:customer-app:F`] = seeds.F
  runtime.ctx.selectedIndexes = () => shown.value.map((x) => x.i)
  runtime.ctx.onCluster = (group: { index: number }[]) => {
    cluster.value = group.map((p) => p.index)
    selected.value = null
    collapsed.value = false
  }
  runtime.ctx.on(root.value, 'click', (e: Event) => {
    const marker = (e.target as Element).closest<SVGElement>('.mk')
    if (marker) pick(Number(marker.dataset.i))
  })
  mountClusters(root.value, runtime.ctx, 'customer-app')
  dispose = runtime.dispose
})
onBeforeUnmount(() => {
  weatherRequest?.abort()
  dispose?.()
})
</script>
<template>
  <div ref="root" class="wire-page mobile-page page-customer-app-5">
    <div class="ph">
      <header class="ah">
        <span>ROUTEL</span>
        <NotificationBell />
        <button class="ui-icon-button theme-toggle" aria-label="라이트·다크 전환" @click="store.themeToggle()">
          <UiIcon name="theme" />
        </button>
      </header>
 <div ref="stage" class="bd map-stage">
        <form class="map-facility-search" role="search" @submit.prevent="searchFacilities">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 5 5" />
          </svg>
          <input
            v-model="query"
            type="search"
            placeholder="지하차도 이름 검색"
            aria-label="지하차도 이름 검색"
            @input="searchFacilities"
          />
          <button class="ui-icon-button"
            v-if="query"
            type="button"
            aria-label="검색어 지우기"
            @click="query='';searchFacilities()"
          >
          <UiIcon name="close" />
        </button>
          <button type="submit">검색</button>
        </form>
        <div class="map-floating-actions">
          <RouterLink class="btn" to="/customer-app/screen-7">길찾기</RouterLink>
          <button
            class="app-weather-toggle"
            :aria-expanded="weather"
            aria-controls="app-weather-card"
            @click="openWeather"
          >☁ 날씨</button>
        </div>
 <div class="map meeting-mobile-map">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-label="시설 위치 도식">
            <g v-for="(f,i) in facilities" :key="i" class="mk" :data-i="i">
              <circle :cx="Number(f[6])/560*100" :cy="Number(f[7])/640*100" r="3" />
            </g>
          </svg>
        </div>
 <div class="app-map-legend" aria-label="지도 범례">
          <span>
            <i class="danger"></i>위험</span>
          <span>
            <i class="warning"></i>주의</span>
          <span>
            <i class="safe"></i>안전</span>
          <span>
            <i class="location"></i>내 위치</span>
        </div>
 <section
          v-if="weather"
          id="app-weather-card"
          class="app-weather-card"
          aria-label="인근 날씨"
        >
          <header>
            <b>{{weatherLocation.name}} 날씨</b>
            <button class="ui-icon-button" aria-label="날씨 닫기" @click="weather=false">
          <UiIcon name="close" />
        </button>
          </header>
          <p v-if="weatherLoading" role="status">기상청 관측 정보 조회 중…</p>
          <p v-else-if="weatherError" role="status">{{weatherError}}</p>
          <template v-else-if="liveWeather">
            <p class="app-weather-temperature">
              <strong>{{liveWeather.temperature??'—'}}°</strong> {{liveWeather.condition}}
            </p>
            <p>습도 {{liveWeather.humidity??'—'}}% · 강우량 {{liveWeather.rainfall??'—'}} mm</p>
            <small>기상청 · {{new Date(liveWeather.observedAt).toLocaleString('ko-KR')}}</small>
          </template>
        </section>
 <section
          class="meeting-bottom-sheet"
          :class="{'is-collapsed':collapsed,'is-dragging':dragHeight!==null}"
          :style="sheetStyle"
        >
          <button
            class="mobile-sheet-handle"
            @pointerdown="beginSwipe"
            @pointermove="moveSwipe"
            @pointerup="endSwipe"
            @pointercancel="cancelSwipe"
            @click="toggleSheet"
            aria-controls="facility-sheet-content"
            :aria-expanded="!collapsed"
          >
            {{collapsed?'목록 펼치기':'목록 접기'}} · {{listed.length}}곳</button>
          <div
            id="facility-sheet-content"
            class="sheet-content"
            v-show="!collapsed||dragHeight!==null"
          >
            <form class="facility-filter" @submit.prevent="filter">
              <label>부산 구<select v-model="district" @change="filter">
                  <option value="">구 전체</option>
                  <option v-for="d in districts" :key="d">{{d}}</option>
                </select>
              </label>
            </form>
            <nav class="chips">
              <button
                v-for="label in ['', '위험','주의','안전']"
                :key="label"
                :aria-pressed="risk===label"
                @click="risk=label;filter()"
              >
                {{label||'전체'}}
              </button>
            </nav>
<p class="data-provenance">시설명은 부산 공개 자료이며, 상태와 날씨는 구성 예시입니다.</p>
            <button v-if="cluster" @click="cluster=null;selected=null">전체 목록으로</button>
            <div v-if="current" class="mobile-sheet-detail">
              <h2>{{current[0]}}</h2>
              <p>{{current[1]}} · {{current[2]}}</p>
              <p>{{current[5]}}</p>
              <button @click="cctvOpen=true">CCTV 보기</button>
              <p role="status">{{status}}</p>
              <button @click="back">{{cluster?'묶음 목록으로':'목록으로'}}</button>
            </div>
            <div v-else ref="list" class="mobile-facility-list">
              <button v-for="{f,i} in listed" :key="i" @click="pick(i)">
                {{f[0]}} · {{f[1]}} · {{f[2]}}
              </button>
              <p v-if="!listed.length">조건에 맞는 시설이 없습니다.</p>
            </div>
          </div>
        </section>
      </div>
      <CustomerAppNav />
<BusanCctvModal v-model="cctvOpen" :facility-name="String(current?.[0]||'')" />
    </div>
  </div>
</template>
