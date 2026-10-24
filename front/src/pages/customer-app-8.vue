<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import CustomerAppNav from '../components/CustomerAppNav.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-8.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-8">
    <div class="ph">
      <div class="ah">
        <span>← 경로 선택</span>
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
        <input
          class="inp inl-7aab68d540"
          placeholder="내 위치 → 부산역"
          aria-label="내 위치 → 부산역"
          type="text"
        />
        <div class="map">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path d="M0 0H100V28C80 36 70 24 50 32S20 40 0 34Z" fill="#cfe2f3" opacity=".6"></path>
            <path
              d="M0 60C30 52 40 70 70 62S90 70 100 66"
              stroke="#fff"
              stroke-width="2.4"
              fill="none"
            ></path>
            <path
              d="M35 100C38 70 55 55 60 20"
              stroke="#fff"
              stroke-width="2.4"
              fill="none"
            ></path></svg
          ><svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path
              d="M18 84C34 70 46 56 62 48S80 28 84 14"
              stroke="#c62828"
              stroke-width="2.4"
              fill="none"
              stroke-dasharray="3 3"
            ></path>
            <path
              d="M18 84C20 62 34 46 52 44S76 36 84 14"
              stroke="#1a6fd6"
              stroke-width="3"
              fill="none"
            ></path>
          </svg>
          <i class="me inl-5dab5fb7d3"></i>
          <i class="s inl-1ce555177d"></i>
          <i class="d inl-94de920e87"></i>
          <i class="w inl-139b471a12"></i>
        </div>
        <div class="card inl-df4d144e7b">
          <div class="inl-a3d12b9b83">
            <b>최단 경로 16분</b>
            <span class="lv d">통제 중</span>
          </div>
          <div class="sub">초량1지하차도 통과 · 이용할 수 없어요</div>
        </div>
        <div class="card inl-fe22a97dbc">
          <div class="inl-a3d12b9b83">
            <b>추천 · 우회 경로 22분 (+6분)</b>
            <span class="lv s">추천</span>
          </div>
          <div class="sub">초량1지하차도 회피 · 경로상 우장춘지하차도 <span class="lv w">주의</span>
          </div>
        </div>
        <button class="btn p" type="button" @click="router.push('/customer-app/screen-9')">안내 시작</button>
      </div>
      <CustomerAppNav />
    </div>
  </div>
</template>
