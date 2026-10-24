<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import CustomerAppNav from '../components/CustomerAppNav.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-6.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-6">
    <div class="ph">
      <div class="ah">
        <span>← 초량1지하차도</span>
        <span class="inl-683edd9226"></span>
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
        <div class="inl-31a824a217">
          <span class="lv d">위험</span>
          <span class="lv n">통제 중</span>
        </div>
        <div class="sub">부산 · 상세 주소 확인 필요</div>
        <div class="kv">
          <div>
            <small>강우량</small>
            <b>18.0 mm</b>
          </div>
          <div>
            <small>인근 날씨</small>
            <b>비 · 18.2°C</b>
          </div>
        </div>
        <div class="card">
          <h4>위험도 추이 · 예측</h4>
          <svg viewBox="0 0 250 120" width="100%" role="img" aria-label="위험도 추이와 예측 ">
            <rect x="134.0" y="8" width="108.0" height="92" fill="#eef2f7"></rect>
            <line
              x1="26"
              x2="242"
              y1="54.0"
              y2="54.0"
              stroke="#f0a020"
              stroke-width="1"
              stroke-dasharray="2 3"
            ></line>
            <text x="23" y="57.0" font-size="8" fill="#f0a020" text-anchor="end">50</text>
            <line
              x1="26"
              x2="242"
              y1="35.6"
              y2="35.6"
              stroke="#c62828"
              stroke-width="1"
              stroke-dasharray="2 3"
            ></line>
            <text x="23" y="38.6" font-size="8" fill="#c62828" text-anchor="end">70</text>
            <line x1="26" x2="242" y1="100" y2="100" stroke="#c9d0d8"></line>
            <line x1="134.0" x2="134.0" y1="8" y2="100" stroke="#142033" stroke-width="1"></line>
            <polyline
              points="26.0,87.1 44.0,83.4 62.0,77.9 80.0,69.6 98.0,55.8 116.0,43.0 134.0,30.1"
              fill="none"
              stroke="#1a6fd6"
              stroke-width="2.4"
              stroke-linejoin="round"
            ></polyline>
            <polyline
              points="134.0,30.1 152.0,24.6 170.0,28.2 188.0,39.3 206.0,52.2 224.0,63.2 242.0,72.4"
              fill="none"
              stroke="#1a6fd6"
              stroke-width="2.4"
              stroke-dasharray="5 4"
              stroke-linejoin="round"
            ></polyline>
            <circle
              cx="134.0"
              cy="30.1"
              r="4"
              fill="#1a6fd6"
              stroke="#fff"
              stroke-width="2"
            ></circle>
            <text x="26.0" y="114" font-size="9" fill="#5a6b82" text-anchor="start">-6시간</text>
            <text x="80.0" y="114" font-size="9" fill="#5a6b82" text-anchor="middle">-3시간</text>
            <text
              x="134.0"
              y="114"
              font-size="9"
              fill="#5a6b82"
              text-anchor="middle"
              font-weight="700"
            >지금</text>
            <text x="188.0" y="114" font-size="9" fill="#5a6b82" text-anchor="middle">+3시간</text>
            <text x="242.0" y="114" font-size="9" fill="#5a6b82" text-anchor="end">+6시간</text>
            <text x="188.0" y="18" font-size="9" fill="#5a6b82" text-anchor="middle">예측</text>
          </svg>
          <div class="sub">
            <span class="inl-d9fbc26031">━</span> 현재까지  <span class="inl-d9fbc26031">┅</span> 예측</div>
        </div>
        <div class="vid inl-b6fd870864">CCTV 영상 </div>
        <button class="btn" type="button" @click="router.push('/customer-app/screen-7')">이 지하차도를 피해 길찾기 →</button>
      </div>
      <CustomerAppNav />
    </div>
  </div>
</template>
