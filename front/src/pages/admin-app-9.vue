<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend, asPage } from '../services/backend'
import { PM_TYPE, levelOf, PM_STATUS, usePmLive } from '../composables/usePm'
import '../styles/admin-app-9.css'
const router = useRouter(),
  store = useApp()
const items = ref<any[]>([]),
  error = ref(''),
  loading = ref(true),
  typeFilter = ref(''),
  facility = ref('')
async function load() {
  try {
    items.value = asPage<any>(await backend('/api/pm/equipments?size=100')).items
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(load)
usePmLive(load)
const facilities = computed(() => [...new Set(items.value.map((i) => i.underpassName))])
const rows = computed(() =>
  items.value.filter((i) => (!typeFilter.value || i.type === typeFilter.value) && (!facility.value || i.underpassName === facility.value)),
)
const open = (i: any) => router.push('/admin-app/screen-10?id=' + i.equipId)
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-9">
    <div class="ph adm">
      <div class="ah">
        <span>예지보전</span>
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
        <label>시설<select v-model="facility" aria-label="시설 선택">
            <option value="">전체</option>
            <option v-for="f in facilities" :key="f" :value="f">{{f}}</option>
          </select>
        </label>
        <div class="chips">
          <button class="chip" :class="{on:typeFilter===''}" type="button" @click="typeFilter=''">전체</button>
          <button v-for="(label,key) in PM_TYPE" :key="key" class="chip" :class="{on:typeFilter===key}" type="button" @click="typeFilter=String(key)">{{label}}</button>
        </div>
        <p v-if="loading" role="status">설비 조회 중…</p>
        <p v-if="error" role="alert">{{error}}</p>
        <p v-if="!loading&&!rows.length&&!error" class="sub">표시할 설비가 없습니다.</p>
        <div v-for="i in rows" :key="i.equipId" class="row" role="button" tabindex="0" @click="open(i)" @keydown.enter="open(i)">
          <div class="g">
            <b>{{i.name}} {{PM_TYPE[i.type]}}</b>
            <small>{{i.underpassName}}<template v-if="i.scoreChange7d"> · {{i.scoreChange7d>0?'▲':'▼'}}{{Math.abs(Math.round(i.scoreChange7d))}}</template><template v-if="i.openEventStatus"> · {{PM_STATUS[i.openEventStatus]}}</template></small>
            <small v-if="i.reasons&&i.reasons.length">{{i.reasons[0]}}</small>
          </div>
          <b>{{i.score==null?'-':Math.round(i.score)}}</b>
          <span class="lv" :class="levelOf(i.level).cls">{{levelOf(i.level).label}}</span>
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
