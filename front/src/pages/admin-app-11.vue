<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend, asPage } from '../services/backend'
import { PM_TYPE, PM_TRIGGER, PM_STATUS, levelOf, hhmm, usePmLive } from '../composables/usePm'
import '../styles/admin-app-11.css'
const router = useRouter(),
  store = useApp()
const items = ref<any[]>([]),
  tab = ref<'OPEN' | 'CHECK_NEEDED' | 'IN_PROGRESS' | 'RESOLVED'>('OPEN'),
  error = ref(''),
  loading = ref(true),
  busyId = ref(0)
async function load() {
  try {
    const q = tab.value === 'OPEN' ? '' : '&status=' + tab.value
    items.value = asPage<any>(await backend('/api/pm/events?size=100' + q)).items
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(load)
usePmLive(load)
function choose(t: typeof tab.value) {
  tab.value = t
  loading.value = true
  void load()
}
async function start(ev: any) {
  busyId.value = ev.eventId
  try {
    await backend('/api/pm/events/' + ev.eventId + '/status', { method: 'PATCH', body: { status: 'IN_PROGRESS' } })
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busyId.value = 0
  }
}
const open = (ev: any) => (ev.status === 'RESOLVED' || ev.status === 'ETC' ? router.push('/admin-app/screen-10?id=' + ev.equipId) : router.push('/admin-app/screen-12?eventId=' + ev.eventId))
const done = computed(() => tab.value === 'RESOLVED')
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-11">
    <div class="ph adm">
      <div class="ah">
        <span>점검 대상·알림</span>
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
        <div class="chips">
          <button class="chip" :class="{on:tab==='OPEN'}" type="button" @click="choose('OPEN')">처리 대기</button>
          <button class="chip" :class="{on:tab==='CHECK_NEEDED'}" type="button" @click="choose('CHECK_NEEDED')">확인 필요</button>
          <button class="chip" :class="{on:tab==='IN_PROGRESS'}" type="button" @click="choose('IN_PROGRESS')">점검 중</button>
          <button class="chip" :class="{on:tab==='RESOLVED'}" type="button" @click="choose('RESOLVED')">조치 완료</button>
        </div>
        <p v-if="loading" role="status">조회 중…</p>
        <p v-if="error" role="alert">{{error}}</p>
        <p v-if="!loading&&!items.length&&!error" class="sub">해당하는 점검 대상이 없습니다.</p>
        <div v-for="ev in items" :key="ev.eventId" class="row" role="button" tabindex="0" @click="open(ev)" @keydown.enter="open(ev)">
          <div class="g">
            <b>{{ev.name}} {{PM_TYPE[ev.type]}}</b>
            <small>{{ev.underpassName}} · {{PM_TRIGGER[ev.triggerType]}} · {{hhmm(ev.createdAt)}}</small>
            <small>발생 {{ev.score==null?'-':Math.round(ev.score)}}점 → 현재 {{ev.currentScore==null?'-':Math.round(ev.currentScore)}}점</small>
          </div>
          <span class="lv" :class="levelOf(ev.level).cls">{{PM_STATUS[ev.status]}}</span>
          <button v-if="ev.status==='CHECK_NEEDED'" class="chip on inl-e3ec02ace9" type="button" :disabled="busyId===ev.eventId" @click.stop="start(ev)">확인</button>
        </div>
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
