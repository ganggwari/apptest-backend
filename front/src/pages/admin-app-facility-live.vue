<script setup lang="ts">
import RiskLevelBadge from '../components/RiskLevelBadge.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { patchFacility, facilityEvents, appendRiskHistory } from '../services/streamUpdates'
import { useBackendEvents } from '../composables/useBackendEvents'
import { ownsFacility } from '../services/facilityAccess'
import { backend, asPage } from '../services/backend'
import { backendResources, valueLabels, type Action } from '../data/backendResources'
import BackendResource from './backend-resource.vue'
import TunnelCctvDialog from '../components/TunnelCctvDialog.vue'
const route = useRoute(),
  id = computed(() => String(route.query.id || '')),
  facility = ref<Record<string, any>>(),
  remaining = ref<any[]>([]),
  operations = ref<InstanceType<typeof BackendResource>>(),
  error = ref(''),
  loading = ref(false)
let abort: AbortController | undefined
async function load() {
  abort?.abort()
  if (!id.value) {
    facility.value = undefined
    remaining.value = []
    return
  }
  const request = new AbortController()
  abort = request
  loading.value = true
  error.value = ''
  if (String(facility.value?.id ?? '') !== id.value) {
    facility.value = undefined
    remaining.value = []
  }
  try {
    facility.value = await backend('/api/underpasses/' + encodeURIComponent(id.value), {
      signal: request.signal,
    })
    // OCR 영상 대신 잔류 차량 목록(통과 기록 중 REMAINING)을 보여 준다
    remaining.value = asPage<any>(
      await backend(
        '/api/underpasses/transits?status=REMAINING&size=20&underpassId=' + encodeURIComponent(id.value),
        { signal: request.signal },
      ),
    ).items
  } catch (e) {
    if (!request.signal.aborted) {
      remaining.value = []
      error.value = (e as Error).message
    }
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
let streamRefresh: ReturnType<typeof setTimeout> | undefined
useBackendEvents((event) => {
  const { name, data } = event
  if (facilityEvents.includes(name) && facility.value) {
    facility.value = patchFacility(facility.value, event)
    return
  }
  if (
    name === 'connected' ||
    (name === 'data-changed' && ['underpass', 'camera'].includes(data.resource)) ||
    (String(data.underpassId) === id.value &&
      ['transit-update', 'plate-queue-add', 'plate-queue-done'].includes(name))
  ) {
    clearTimeout(streamRefresh)
    streamRefresh = setTimeout(() => void load(), 200)
  }
})
watch(id, load, { immediate: true })
onBeforeUnmount(() => {
  clearTimeout(streamRefresh)
  abort?.abort()
})
function action(item: Action) {
  if (facility.value && ownsFacility(facility.value))
    void operations.value?.open('action', facility.value, item)
}
</script>
<template>
  <section class="app-facility-detail">
    <p v-if="loading" role="status">시설 정보 조회 중…</p>
    <p v-if="error" role="alert">{{error}}</p>
    <template v-if="facility">
      <h1>{{facility.name}}</h1>
      <div class="app-detail-badges">
        <RiskLevelBadge :value="facility.currentRiskLevel" />
        <span>{{facility.currentStatus==='BLOCKED'?'통제 중':'정상 운영'}}</span>
      </div>
      <p class="sub">{{facility.roadAddress}}</p>
      <div class="kv">
        <div>
          <small>위험 점수</small>
          <b>{{facility.riskScore??facility.currentRiskScore??'—'}}</b>
        </div>
        <div>
          <small>강우량</small>
          <b>{{facility.rainfallMm??'—'}} mm</b>
        </div>
        <div>
          <small>진입 차량</small>
          <b>{{facility.remainingCount??'—'}}대</b>
        </div>
        <div>
          <small>판별 대기</small>
          <b>{{facility.pendingReviewCount??'—'}}건</b>
        </div>
      </div>
      <TunnelCctvDialog :facility-name="facility.name" />
      <section class="card app-remaining" aria-label="잔류 차량">
        <h2>잔류 차량 {{remaining.length}}대</h2>
        <p v-if="!remaining.length" class="sub">지하차도 안에 남은 차량이 없습니다.</p>
        <ul v-else>
          <li v-for="t in remaining" :key="t.id">
            <b>{{t.displayCarNum||t.carNum}}</b>
            <small> · 진입 {{String(t.entryAt||'').replace('T',' ').slice(5,16)}}</small>
          </li>
        </ul>
      </section>
      <button
        type="button"
        class="app-control-primary"
        :disabled="!ownsFacility(facility)"
        :title="ownsFacility(facility)?'':'담당 외 시설은 통제할 수 없습니다.'"
        @click="action(backendResources.facilities!.actions![facility.currentStatus==='BLOCKED'?1:0]!)"
      >
        {{facility.currentStatus==='BLOCKED'?'통제 해제':'통제 시작'}}
      </button>
      <details class="card">
        <summary>시설 설정·추가 업무</summary>
        <button type="button" @click="operations?.open('edit',facility)">시설 정보 수정</button>
        <button
          v-for="item in backendResources.facilities!.actions!.slice(2)"
          :key="item.label"
          type="button"
          @click="action(item)"
        >
          {{item.label}}
        </button>
        <RouterLink :to="'/admin-app/transits?underpassId='+id">차량 통과 기록</RouterLink>
      </details>
      <div class="app-hidden-operations">
        <BackendResource
          ref="operations"
          operations-only
          resource-key="facilities"
          embedded
          @saved="load"
        />
      </div>
    </template>
    <RouterLink v-else-if="!loading" to="/admin-app/screen-4">통제 목록에서 시설을 선택해 주세요.</RouterLink>
  </section>
</template>
