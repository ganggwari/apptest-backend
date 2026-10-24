<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import '../styles/map-weather.css'
import { getWeather, weatherHourLabel } from '../services/weather'
import type { Weather } from '../services/weather'

type Location = { lat: number; lng: number; name: string; weather?: string; rainfall?: string }
const root = ref<HTMLElement>(),
  opened = ref(false),
  loading = ref(false),
  error = ref(''),
  location = ref<Location>({ lat: 35.1796, lng: 129.0756, name: '부산' })

const weather = ref<Weather | null>(null)
const show = (value: number | null | undefined, unit: string) =>
  value == null ? '—' : `${value}${unit}`
const observation = computed(() => {
  const value = weather.value?.observedAt
  if (!value) return '관측 시각 미제공'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '관측 시각 미제공' : date.toLocaleString('ko-KR')
})
let parent: HTMLElement | null = null,
  request: AbortController | undefined
async function refresh() {
  request?.abort()
  error.value = ''

  const controller = new AbortController()
  request = controller
  loading.value = true
  weather.value = null
  const timeout = setTimeout(() => controller.abort(), 15000)
  try {
    const data = await getWeather(location.value.lat, location.value.lng, controller.signal)
    if (!controller.signal.aborted) weather.value = data
  } catch (cause) {
    if (request === controller)
      error.value = controller.signal.aborted
        ? '날씨 응답이 지연되고 있습니다. 다시 시도해 주세요.'
        : cause instanceof Error
          ? cause.message
          : '날씨 연결을 확인해 주세요.'
  } finally {
    clearTimeout(timeout)
    if (request === controller) loading.value = false
  }
}
function changed(event: Event) {
  location.value = (event as CustomEvent<Location>).detail
  void refresh()
}
function outside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) opened.value = false
}
onMounted(() => {
  parent = root.value?.closest('.page-c-map') || null
  parent?.addEventListener('map-weather-location', changed)
  document.addEventListener('pointerdown', outside)
  void refresh()
})
onBeforeUnmount(() => {
  request?.abort()
  parent?.removeEventListener('map-weather-location', changed)
  document.removeEventListener('pointerdown', outside)
})
</script>
<template>
  <div
    ref="root"
    class="map-weather-widget"
    @keydown.esc="opened=false"
    @pointerdown.stop
    @wheel.stop
  >
    <section
      v-if="opened"
      id="map-weather-detail"
      class="map-weather-detail"
      aria-label="인근 날씨 상세"
    >
      <header>
        <strong>{{location.name}}</strong>
        <button class="ui-icon-button" type="button" aria-label="날씨 상세 닫기" @click="opened=false">
          <UiIcon name="close" />
        </button>
      </header>
      <p v-if="loading" role="status">날씨를 확인하고 있습니다.</p>
      <div v-else-if="error" class="weather-error" role="status">
        <p>{{error}}</p>
        <button type="button" @click="refresh">다시 불러오기</button>
      </div>
      <template v-else-if="weather">
        <div class="map-weather-overview">
          <svg viewBox="0 0 48 40" width="55" height="48" aria-hidden="true">
            <path d="M13 30a10 10 0 1 1 1-20 13 13 0 0 1 25 5 8 8 0 0 1-1 16H13Z" fill="#bbcbd8" />
            <path
              v-if="/비|소나기/.test(weather.condition||'')"
              d="m16 34-2 4m12-4-2 4m12-4-2 4"
              stroke="#4d8ed3"
              stroke-width="3"
              stroke-linecap="round"
            />
          </svg>
          <div>
            <b>{{show(weather.temperature,'°')}}</b>
            <span>{{weather.condition||'기상 상태 미제공'}}</span>
          </div>
        </div>
        <dl>
          <div>
            <dt>습도</dt>
            <dd>{{show(weather.humidity,'%')}}</dd>
          </div>
          <div>
            <dt>강우량</dt>
            <dd>{{show(weather.rainfall,' mm')}}</dd>
          </div>
          <div>
            <dt>풍속</dt>
            <dd>{{show(weather.windSpeed,' m/s')}}</dd>
          </div>
        </dl>
        <div class="map-weather-hourly" v-if="weather.hourly?.length">
          <h3>시간별 예보</h3>
          <ul>
            <li v-for="hour in weather.hourly.slice(0,6)" :key="hour.time">
              <span>{{weatherHourLabel(hour.time)}}</span>
              <b>{{show(hour.temperature,'°')}}</b>
              <small>{{show(hour.rainfall,' mm')}}</small>
            </li>
          </ul>
        </div>
        <footer>기상청 · {{observation}}</footer>
      </template>
    </section>
 <button
      type="button"
      class="map-weather-marker"
      :aria-expanded="opened"
      aria-controls="map-weather-detail"
      @click="opened=!opened"
    >
      <svg viewBox="0 0 40 32" width="32" height="28" aria-hidden="true">
        <path d="M11 26a8 8 0 1 1 0-16 11 11 0 0 1 21 4 6 6 0 0 1-1 12H11Z" fill="#afc3d3" />
      </svg>
      <span>
        <strong>{{show(weather?.temperature,'°')}}</strong>
        <small>{{loading?'조회 중':weather?.condition||'날씨'}}</small>
      </span>
      <span aria-hidden="true">{{opened?'⌄':'⌃'}}</span>
    </button>
  </div>
</template>
