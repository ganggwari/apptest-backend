<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-9.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-9">
    <div class="ph">
      <div class="ah">ROUTEL<NotificationBell />
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
        <div class="inl-c20b650261">
          <span class="inl-c22576a4c7">↰</span>
          <div>
            <b class="inl-433de30b36">300m 앞 좌회전</b>
            <div class="inl-d06a1531ea">△△로 방면</div>
          </div>
        </div>
        <div class="inl-49cd09213e">
          <span class="lv w">우장춘지하차도 주의</span>
          <span class="lv n">초량1지하차도 회피 중</span>
        </div>
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
              opacity=".35"
            ></path>
            <path
              d="M18 84C20 62 34 46 52 44S76 36 84 14"
              stroke="#1a6fd6"
              stroke-width="3"
              fill="none"
            ></path>
          </svg>
          <i class="me inl-bb39974101"></i>
          <i class="s inl-1ce555177d"></i>
          <i class="d inl-94de920e87"></i>
        </div>
        <div class="card inl-13313b1a3a">
          <div>
            <b class="inl-de00808e96">22분</b>
            <div class="sub">도착 11:04 · 7.4km</div>
          </div>
          <span class="btn inl-75091b1bbb">종료</span>
        </div>
      </div>
    </div>
  </div>
</template>
