<script setup lang="ts">
import RiskLevelBadge from './RiskLevelBadge.vue'
import { nextTick, ref, watch } from 'vue'
type Facility = {
  id: string
  name: string
  address: string
  riskScore: number | null
  rainfallMm: number | null
  currentWaterCm?: number | null
  remainingCount?: number | null
  pendingReviewCount?: number | null
}
const props = defineProps<{
  facility: Facility | null
  cluster: Facility[]
  state: string
  riskLevel?: string
  control: string
  observed: string
  loading: boolean
  error: string
  weather?: { temperature?: number | null; condition?: string }
  weatherLoading: boolean
  weatherError: string
  chartSeries?: ReturnType<typeof import('../utils/risk-chart').riskChartSeries>
  riskPoints: string
  forecastPoints?: string
  forecastError?: string
  canControl: boolean
  dashboardVisual?: boolean
}>()
const expanded = defineModel<boolean>('expanded', { default: true }),
  panel = ref<HTMLElement>(),
  body = ref<HTMLElement>()
function scrollPanel(event: WheelEvent) {
  if (event.ctrlKey || !event.deltaY) return
  let remaining =
    event.deltaY *
    (event.deltaMode === 1
      ? 16
      : event.deltaMode === 2
        ? body.value?.clientHeight || window.innerHeight
        : 1)
  const scroll = (element: Element) => {
    const before = element.scrollTop
    element.scrollTop += remaining
    remaining -= element.scrollTop - before
  }
  if (expanded.value && body.value) scroll(body.value)
  // At either edge, continue in the surrounding page instead of trapping the wheel.
  let ancestor = panel.value?.parentElement
  while (ancestor && Math.abs(remaining) > 0.5) {
    if (/auto|scroll/.test(getComputedStyle(ancestor).overflowY)) scroll(ancestor)
    ancestor = ancestor.parentElement
  }
  if (Math.abs(remaining) > 0.5 && document.scrollingElement) scroll(document.scrollingElement)
  event.preventDefault()
}

defineEmits<{ select: [id: string]; clear: []; control: [index: number] }>()
watch(
  () => props.facility?.id,
  async (id) => {
    if (id) {
      await nextTick()
      panel.value?.focus({ preventScroll: true })
    }
  },
)
</script>
<template>
  <section
    ref="panel"
    @wheel.stop="scrollPanel"
    class="manager-map-panel"
    aria-label="선택 시설"
    tabindex="-1"
    ><header
      ><h2>{{cluster.length&&!facility?'묶음 시설 '+cluster.length+'곳':'선택 시설'}}</h2
      ><button
        v-if="!facility"
        class="manager-detail-toggle"
        type="button"
        :aria-expanded="expanded"
        aria-controls="manager-map-panel-body"
        @click="expanded=!expanded"
        >{{expanded?'상세 접기 ▴':'상세 펼치기 ▾'}}</button
      ><button v-if="facility||cluster.length" class="ui-icon-button" type="button" aria-label="시설 선택 해제" @click="$emit('clear')">
        <UiIcon name="close" />
      </button></header
    ><div v-if="facility" class="manager-facility-heading"
        ><strong>{{facility.name}}</strong
        ><button
          class="manager-detail-toggle"
          type="button"
          :aria-expanded="expanded"
          aria-controls="manager-map-panel-body"
          @click="expanded=!expanded"
          >{{expanded?'상세 접기 ▴':'상세 펼치기 ▾'}}</button
      ><p>{{facility.address||'주소 미제공'}}</p
      ><div class="manager-facility-status"
        ><RiskLevelBadge :value="riskLevel||state" /><span>{{control}}</span
        ></div
      ></div
    ><div ref="body" v-show="expanded" id="manager-map-panel-body" class="manager-panel-body"
      ><template v-if="facility"
        ><p v-if="loading" role="status">최신 상태 조회 중…</p
        ><p v-if="error" role="alert">{{error}}</p
        ><div class="manager-risk"
          ><strong
            >{{facility.riskScore===null?'—':facility.riskScore.toFixed(1)
            }}<small>점</small></strong
          ><span>위험 점수</span></div
        ><div class="manager-gauge"
          ><b :style="{width:Math.max(0,Math.min(100,facility.riskScore||0))+'%'}"></b></div
        ><dl
          ><dt>현재 수위</dt><dd>{{facility.currentWaterCm??'—'}} cm</dd><dt>강우량</dt
          ><dd>{{facility.rainfallMm??'—'}} mm</dd><dt>잔류 차량</dt
          ><dd>{{facility.remainingCount??'—'}}대</dd><dt>판별 대기</dt
          ><dd>{{facility.pendingReviewCount??'—'}}건</dd><dt>관측 시각</dt
          ><dd>{{observed}}</dd></dl
        ><div class="manager-weather"
          ><b>시설 위치 날씨</b><p v-if="weatherLoading">날씨 조회 중…</p
          ><p v-else-if="weatherError">{{weatherError}}</p
          ><p v-else
            >{{weather?.condition||'기상 상태 미제공'}} · {{weather?.temperature??'—'}}°C</p
          ></div
        ><details
          ><summary>관측 기록</summary
          ><svg
            v-if="riskPoints||forecastPoints"
            viewBox="0 0 300 130"
            :class="{'risk-history-chart':dashboardVisual}"
            role="img"
            aria-label="선택 시설 위험도 관측 기록"
          >
            <g v-if="dashboardVisual" class="dashboard-chart-grid" aria-hidden="true"><path v-for="y in [10,35,60,85]" :key="y" :d="`M20 ${y}H280`" fill="none" stroke-width=".6" /><text x="2" y="13" font-size="8" fill="currentColor" stroke="none">100</text><text x="6" y="63" font-size="8" fill="currentColor" stroke="none">50</text><text x="10" y="113" font-size="8" fill="currentColor" stroke="none">0</text></g>
            <path d="M20 10V110H280" fill="none" stroke="#c9ced6" />
            <line
              v-if="chartSeries?.boundary!=null"
              :x1="chartSeries.boundary"
              :x2="chartSeries.boundary"
              y1="10"
              y2="110"
              stroke="#c9ced6"
              stroke-dasharray="2 3"
            />
            <text
              v-for="tick in chartSeries?.ticks"
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
              v-if="chartSeries?.lastObserved"
              class="dashboard-observed-point"
              :cx="chartSeries.lastObserved.x"
              :cy="chartSeries.lastObserved.y"
              r="3"
              fill="#225f9b"
            />
            <path
              v-if="chartSeries?.carryPath"
              :d="chartSeries.carryPath"
              fill="none"
              stroke="#225f9b"
              stroke-opacity=".5"
              stroke-width="2"
              stroke-dasharray="2 3"
            />
            <path :key="dashboardVisual?chartSeries?.samples.map(sample=>sample.time+':'+sample.score).join(';'):undefined" :d="chartSeries?.observedPath" class="dashboard-observed-line dashboard-chart-motion" fill="none" stroke="#225f9b" stroke-width="3" :stroke-linecap="dashboardVisual?'round':undefined" :stroke-linejoin="dashboardVisual?'round':undefined" />
            <path
              v-if="chartSeries?.forecastPath"
              class="dashboard-forecast-line"
              :d="chartSeries.forecastPath"
              fill="none"
              stroke="#225f9b"
              stroke-width="3"
              stroke-dasharray="6 4"
            />
            <g v-if="dashboardVisual"><circle v-for="sample in chartSeries?.samples" :key="sample.time" :cx="sample.x" :cy="sample.y" r="2.5" stroke-width="1.5" class="dashboard-chart-hover" :tabindex="sample.time===chartSeries?.samples.at(-1)?.time?0:-1" role="img" :aria-label="`${new Date(sample.time).toLocaleTimeString('ko-KR',{timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit'})}, 위험 점수 ${sample.score}점`"><title>{{new Date(sample.time).toLocaleTimeString('ko-KR',{timeZone:'Asia/Seoul',hour:'2-digit',minute:'2-digit'})}} · {{sample.score}}점</title></circle></g>
            </svg
          ><p v-else>조회된 관측 기록이 없습니다.</p
          ><p v-if="forecastPoints">실선: 관측 · 점선: 향후 3시간 예측</p
          ><p v-if="chartSeries?.carryPath" class="sub"
            >엷은 점선: 최근 관측값을 현재까지 유지 (추가 관측 아님)</p
          ><p v-if="forecastError">{{forecastError}}</p></details
        ><p class="manager-assignment">{{canControl?'담당 시설':'담당 외 시설 · 통제 권한 없음'}}</p
        ><div class="manager-control-actions"
          ><button
            type="button"
            :disabled="!canControl||loading||!!error||control!=='정상 운영'"
            @click="$emit('control',0)"
            >통제</button
          ><button
            type="button"
            :disabled="!canControl||loading||!!error||control!=='통제 중'"
            @click="$emit('control',1)"
            >통제 해제</button
          ></div
        ><RouterLink :to="'/fac-detail?id='+facility.id">시설 상세 →</RouterLink></template
      ><ul v-else-if="cluster.length" class="manager-cluster-options"
        ><li v-for="row in cluster" :key="row.id"
          ><button type="button" @click="$emit('select',row.id)"
            >{{row.name}} <span aria-hidden="true">›</span></button
          ></li
        ></ul
      ><p v-else class="manager-selection-hint"
        >지도에서 마커를 누르면 해당 시설의 상세 정보가 표시됩니다.</p
      ></div
    ></section
  >
</template>
<style scoped src="../styles/components/manager-map-panel.css"></style>
