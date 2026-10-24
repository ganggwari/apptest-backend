<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import CustomerAppNav from '../components/CustomerAppNav.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-12.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-12">
    <div class="ph">
      <div class="ah">
        <span>알림</span>
        <span class="inl-683edd9226">모두 읽음</span>
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
        <div class="chips">
          <button class="chip on" type="button">전체</button>
          <button class="chip" type="button">위험</button>
          <button class="chip" type="button">공지</button>
          <button class="chip" type="button">문의</button>
        </div>
        <div class="row">
          <span class="lv d inl-478ee99241">위험</span>
          <div class="g">
            <b>초량1지하차도 통제 시작</b>
            <small>방금</small>
          </div>
        </div>
        <div class="row">
          <span class="lv w inl-478ee99241">주의</span>
          <div class="g">
            <b>초량2지하차도 위험 단계 상승</b>
            <small>20분 전</small>
          </div>
        </div>
        <div class="row">
          <span class="lv n inl-478ee99241">공지</span>
          <div class="g">
            <b>시스템 점검 안내</b>
            <small>어제</small>
          </div>
        </div>
        <div class="row">
          <span class="lv n inl-478ee99241">문의</span>
          <div class="g">
            <b>문의 답변이 도착했어요</b>
            <small>어제</small>
          </div>
        </div>
        <div class="row">
          <span class="lv n inl-478ee99241">공지</span>
          <div class="g">
            <b>앱 업데이트 안내</b>
            <small>10/08</small>
          </div>
        </div>
        <button
          class="btn inl-26d3d1951d"
          type="button"
          @click="router.push('/customer-app/notices')"
        >공지사항 · FAQ 보기</button>
      </div>
      <CustomerAppNav />
    </div>
  </div>
</template>
