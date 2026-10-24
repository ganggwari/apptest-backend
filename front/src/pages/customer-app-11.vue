<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import CustomerAppNav from '../components/CustomerAppNav.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-11.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-11">
    <div class="ph">
      <div class="ah">
        <span>날씨</span>
        <span class="inl-683edd9226">초량1지하차도 ▾</span>
        <NotificationBell />
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
        <div class="card">
          <div class="inl-b7db32fc7f">
            <div>
              <div class="inl-756597519d">18.2°C</div>
              <div class="sub">비 · 습도 92% · 풍속 3.1m/s</div>
            </div>
            <span class="lv d">위험</span>
          </div>
        </div>
        <div class="card">
          <h4>시간대별 강수 예보 (mm)</h4>
          <div class="bars">
            <u class="inl-374ca1c8c4"></u>
            <u class="inl-ef757385e4"></u>
            <u class="mid inl-d8e3db3ff7"></u>
            <u class="hi inl-2ea7fe0f2b"></u>
            <u class="hi inl-44022f3ed4"></u>
            <u class="mid inl-0511b8db5f"></u>
            <u class="mid inl-25fb879645"></u>
            <u class="inl-4bd94b65f6"></u>
            <u class="inl-9d371dc68a"></u>
            <u class="inl-bda09e0338"></u>
            <u class="inl-b0fc662f84"></u>
            <u class="inl-7a73f5ee96"></u>
          </div>
          <div class="sub inl-a3d12b9b83">
            <span>10시</span>
            <span>14시</span>
            <span>18시</span>
            <span>21시</span>
          </div>
        </div>
        <div class="card">
          <h4>3일 예보</h4>
          <div class="row">
            <div class="g">오늘</div>
            <span>비</span>
            <small>14° / 19°</small>
          </div>
          <div class="row">
            <div class="g">내일</div>
            <span>흐림</span>
            <small>13° / 18°</small>
          </div>
          <div class="row">
            <div class="g">모레</div>
            <span>맑음</span>
            <small>12° / 20°</small>
          </div>
        </div>
        <div class="sub">출처: 기상청 </div>
      </div>
      <CustomerAppNav />
    </div>
  </div>
</template>
