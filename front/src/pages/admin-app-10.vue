<script setup lang="ts">
import AdminBackButton from '../components/AdminBackButton.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
import { PM_TYPE, levelOf, PM_STATUS, hhmm, usePmLive } from '../composables/usePm'
import '../styles/admin-app-10.css'
const route = useRoute(),
  router = useRouter(),
  store = useApp()
const id = computed(() => String(route.query.id || ''))
const data = ref<any>(null),
  points = ref<{ at: string; score: number }[]>([]),
  error = ref('')
async function load() {
  if (!id.value) return
  try {
    data.value = await backend('/api/pm/equipments/' + encodeURIComponent(id.value))
    points.value = await backend('/api/pm/equipments/' + encodeURIComponent(id.value) + '/scores?days=1')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
watch(id, load, { immediate: true })
usePmLive(load)
const eq = computed(() => data.value?.equipment)
const poly = computed(() => {
  const p = points.value
  if (p.length < 2) return ''
  return p.map((v, i) => `${(i / (p.length - 1)) * 100},${40 - (Math.min(100, v.score) / 100) * 38}`).join(' ')
})
const register = () => router.push('/admin-app/screen-12?eventId=' + eq.value.openEventId)
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-10">
    <div class="ph adm">
      <div class="ah">
        <span class="app-header-title">
          <AdminBackButton fallback="/admin-app/screen-9" />
          <span>설비 상세</span>
        </span>
        <span class="inl-683edd9226"></span>
        <a class="app-bell" href="/admin-app/screen-13">알림</a>
        <button
          class="ui-icon-button theme-toggle"
          type="button"
          aria-label="라이트·다크 전환"
          @click="store.themeToggle()"
        >
          <UiIcon name="theme" />
        </button>
      </div>
            <div class="bd">
        <p v-if="error" role="alert">{{error}}</p>
        <p v-if="!eq&&!error" role="status">설비 조회 중…</p>
        <template v-if="eq">
        <div class="card inl-13313b1a3a">
          <div>
            <b class="inl-5e0faad207">{{eq.name}} {{PM_TYPE[eq.type]}}</b>
            <div class="sub">{{eq.underpassName}}<template v-if="eq.openEventStatus"> · {{PM_STATUS[eq.openEventStatus]}}</template></div>
          </div>
          <div class="inl-54c2afb7ba">
            <b class="inl-81351bd1ac">{{eq.score==null?'-':Math.round(eq.score)}}</b>
            <span class="sub"> /100</span>
            <br />
            <span class="lv" :class="levelOf(eq.level).cls">{{levelOf(eq.level).label}}</span>
          </div>
        </div>
        <div class="card">
          <h4>점수 추이 (최근 24시간)</h4>
          <svg v-if="poly" viewBox="0 0 100 40" preserveAspectRatio="none" style="width:100%;height:56px" aria-label="점수 추이">
            <line x1="0" x2="100" :y1="40-(data.cautionMin/100)*38" :y2="40-(data.cautionMin/100)*38" stroke="#f0a020" stroke-dasharray="2 2" stroke-width=".6" />
            <line x1="0" x2="100" :y1="40-(data.dangerMin/100)*38" :y2="40-(data.dangerMin/100)*38" stroke="#c62828" stroke-dasharray="2 2" stroke-width=".6" />
            <polyline :points="poly" fill="none" stroke="currentColor" stroke-width="1.2" vector-effect="non-scaling-stroke" />
          </svg>
          <p v-else class="sub">아직 점수 이력이 충분하지 않습니다.</p>
          <small class="sub">주의 {{data.cautionMin}} · 위험 {{data.dangerMin}} 이상 · 마지막 수신 {{hhmm(data.scoredAt)}}</small>
        </div>
        <div class="card">
          <h4>헬스 인디케이터</h4>
          <div class="health-indicator">
            <strong>{{eq.level==='DANGER'?'점검 필요':eq.level==='CAUTION'?'주의 관찰':'정상'}}</strong>
            <p>{{(eq.reasons&&eq.reasons.length)?eq.reasons[0]:'특이 사항이 없습니다.'}}</p>
          </div>
        </div>
        <div v-if="data.topCauses&&data.topCauses.length" class="card">
          <h4>판단 근거</h4>
          <div v-for="c in data.topCauses" :key="c.key" class="row">
            <div class="g">{{c.label}}</div>
            <b>+{{Math.round(c.points)}}</b>
          </div>
        </div>
        <div class="card">
          <h4>최근 점검 이력</h4>
          <p v-if="!data.recentActions.length" class="sub">점검 이력이 없습니다.</p>
          <div v-for="a in data.recentActions" :key="a.actionId" class="row">
            <div class="g"><b>{{a.content}}</b><small>{{hhmm(a.createdAt)}} · {{a.managerName}}</small></div>
          </div>
        </div>
        <button
          class="btn p inl-26d3d1951d"
          type="button"
          :disabled="!eq.openEventId"
          :title="eq.openEventId?'':'열려 있는 점검 대상이 없습니다.'"
          @click="register"
        >{{eq.openEventId?'점검 등록':'점검 대상 아님'}}</button>
        </template>
      </div>
<div class="tb">
        <a class="" href="/admin-app/screen-3">
          <b>▦</b>현황</a>
        <a class="" href="/admin-app/screen-4">
          <b>◼</b>통제</a>
        <a class="on" href="/admin-app/screen-9">
          <b>⚙</b>예지보전</a>
        <a class="" href="/admin-app/screen-13">
          <b>◔</b>알림</a>
        <a class="" href="/admin-app/screen-14">
          <b>☰</b>더보기</a>
      </div>
    </div>
  </div>
</template>
