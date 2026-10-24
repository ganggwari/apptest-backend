<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApp } from '../stores/app'
import JurisdictionContactList from '../components/JurisdictionContactList.vue'
import { useAdminProfile } from '../stores/adminProfile'
import { backend, asPage } from '../services/backend'
import { PM_TYPE, hhmm } from '../composables/usePm'
import '../styles/admin-app-14.css'
function printDocument() {
  window.print()
}
const route = useRoute(),
  app = useApp(),
  admin = useAdminProfile(),
  query = ref('')
const section = computed(() => route.path.split('/').at(-1)),
  title = computed(() =>
    section.value === 'history'
      ? '점검 이력'
      : section.value === 'contacts'
        ? '관할 담당자 연락처'
        : '통제 전달 문서',
  )
const actions = ref<any[]>([]),
  loadError = ref('')
onMounted(async () => {
  if (section.value !== 'history') return
  try {
    actions.value = asPage<any>(await backend('/api/pm/actions?size=100')).items
  } catch (e) {
    loadError.value = (e as Error).message
  }
})
const rows = computed(() =>
  actions.value.filter((a) => [a.name, a.underpassName, a.content, a.managerName].join(' ').includes(query.value)),
)
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-14">
    <div class="ph adm">
      <header class="ah">
        <RouterLink to="/admin-app/screen-14">‹</RouterLink>
        <span>{{title}}</span>
        <button
          type="button"
          class="ui-icon-button theme-toggle"
          aria-label="라이트·다크 전환"
          @click="app.themeToggle()"
        >
          <UiIcon name="theme" />
        </button>
      </header>
      <main class="bd app-extra-body">
        <template v-if="section==='history'">
          <input
            v-model="query"
            type="search"
            placeholder="설비·시설·조치 내용 검색"
            aria-label="점검 이력 검색"
          />
          <div class="inspection-history-list" role="list" aria-label="점검 이력 목록">
          <article v-for="a in rows" :key="a.actionId" class="history-record" role="listitem">
            <time>{{hhmm(a.createdAt)}}</time>
            <h2>{{a.name}} · {{PM_TYPE[a.type]}}</h2>
            <p class="history-facility">{{a.underpassName}}</p>
            <p>{{a.content}} · {{a.managerName}}</p>
            <p class="history-result">점검 전 {{a.scoreBefore==null?'-':Math.round(a.scoreBefore)}}점 → 점검 후 {{a.scoreAfter==null?'대기':Math.round(a.scoreAfter)+'점'}}</p>
          </article>
          </div>
          <p v-if="loadError" role="alert">{{loadError}}</p>
          <p v-else-if="!rows.length">표시할 점검 이력이 없습니다.</p>
        </template>
        <template v-else-if="section==='contacts'">
          <JurisdictionContactList />
        </template>
        <template v-else>
          <p class="sub">구성 예시 · 실제 통제 문서가 아닙니다.</p>
          <article class="card">
            <h2>도로 통제 정보 전달서</h2>
            <dl>
              <dt>시설</dt>
              <dd>초량1지하차도</dd>
              <dt>구분</dt>
              <dd>통제 시작</dd>
              <dt>사유</dt>
              <dd>침수 위험</dd>
              <dt>통제 방식</dt>
              <dd>입구 차단 · 전광판 우회 안내</dd>
              <dt>담당 지역</dt>
              <dd>{{admin.region}}</dd>
            </dl>
          </article>
          <button type="button" class="btn" @click="printDocument">문서 인쇄 · PDF 저장</button>
        </template>
      </main>
      <nav class="tb">
        <RouterLink to="/admin-app/screen-3">현황</RouterLink>
        <RouterLink to="/admin-app/screen-4">통제</RouterLink>
        <RouterLink to="/admin-app/screen-9">예지보전</RouterLink>
        <RouterLink to="/admin-app/screen-13">알림</RouterLink>
        <RouterLink to="/admin-app/screen-14">더보기</RouterLink>
      </nav>
    </div>
  </div>
</template>
<style scoped src="../styles/pages/admin-app-extra.css"></style>
