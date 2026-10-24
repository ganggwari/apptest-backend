<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend, asPage } from '../services/backend'
import { PM_TYPE } from '../composables/usePm'
import '../styles/admin-app-12.css'
const route = useRoute(),
  router = useRouter(),
  store = useApp()
const eventId = computed(() => String(route.query.eventId || ''))
const ev = ref<any>(null),
  kind = ref('현장 점검'),
  result = ref('정상 복구'),
  text = ref(''),
  error = ref(''),
  saving = ref(false)
onMounted(async () => {
  try {
    const all = asPage<any>(await backend('/api/pm/events?size=100')).items
    ev.value = all.find((e) => String(e.eventId) === eventId.value) || null
    if (!ev.value) error.value = '처리할 점검 대상을 찾지 못했습니다.'
  } catch (e) {
    error.value = (e as Error).message
  }
})
const content = () => `[${kind.value}] ${text.value || '내용 없음'} — ${result.value}`
async function submit(finish: boolean) {
  if (!ev.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    if (finish)
      await backend('/api/pm/events/' + ev.value.eventId + '/status', {
        method: 'PATCH',
        body: { status: 'RESOLVED', content: content() },
      })
    else
      await backend('/api/pm/events/' + ev.value.eventId + '/actions', {
        method: 'POST',
        body: { content: content() },
      })
    await router.push('/admin-app/screen-11')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-12">
    <div class="ph adm">
      <div class="ah">
        <span>점검 등록</span>
        <span class="inl-683edd9226">닫기</span>
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
        <div v-if="ev" class="card"><b>{{ev.name}} · {{PM_TYPE[ev.type]}} · {{ev.underpassName}}</b></div>
        <div class="sub">조치 유형</div>
        <div class="chips">
          <button v-for="k in ['현장 점검','부품 교체','설정 조정','원격 확인','이상 없음']" :key="k" class="chip" :class="{on:kind===k}" type="button" @click="kind=k">{{k}}</button>
        </div>
        <div class="sub">조치 내용</div>
        <input v-model="text" class="inp inl-b6fd870864" placeholder="내용 입력" aria-label="내용 입력" type="text" />
        <div class="sub">점검 결과</div>
        <div class="chips">
          <button v-for="r in ['정상 복구','추가 점검 필요','교체 필요']" :key="r" class="chip" :class="{on:result===r}" type="button" @click="result=r">{{r}}</button>
        </div>
        <div class="sub">등록자 · 로그인 계정 자동 기록</div>
        <button class="btn p inl-26d3d1951d" type="button" :disabled="!ev||saving" @click="submit(true)">조치 완료로 등록</button>
        <button class="btn inl-26d3d1951d" type="button" :disabled="!ev||saving" @click="submit(false)">점검 기록만 추가</button>
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
