<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { logoutCustomer } from '../services/customerSession'
import CustomerAppPage from '../components/CustomerAppPage.vue'
const app = useApp(),
  router = useRouter(),
  error = ref(''),
  member = computed(() => app.account?.role === 'customer')
const menus = [
  ['알림 수신 설정', 'alerts'],
  ['내 문의', 'inquiries'],
  ['회원 탈퇴', 'withdraw'],
]
function target(section: string) {
  if (section === 'alerts') return '/customer-app/screen-12'
  const next = `/customer-app/account/${section}`
  return member.value ? next : { path: '/customer-app/screen-1', query: { next } }
}
async function signout() {
  try {
    if (app.account) await logoutCustomer()
    else app.clearAccount()
    router.replace('/customer-app/screen-1')
  } catch (e) {
    error.value = (e as Error).message
  }
}
</script>
<template>
  <CustomerAppPage title="마이페이지">
    <section class="app-account-card">
      <h2>{{app.account?.name||''}}</h2>
      <p>
        {{member?'내 정보와 이용 설정을 관리하세요.':'공지사항과 FAQ는 로그인 없이 이용할 수 있습니다.'}}
      </p>
      <RouterLink v-if="!member" class="app-account-primary" to="/customer-app/screen-1">로그인</RouterLink>
    </section>
    <nav class="app-account-menu" aria-label="마이페이지 메뉴">
      <RouterLink to="/customer-app/notices">공지사항 <span>›</span>
      </RouterLink>
      <RouterLink to="/customer-app/faq">FAQ <span>›</span>
      </RouterLink>
        <RouterLink :to="target('profile')">
          내 정보 관리 <small v-if="!member">로그인 필요</small>
          <span>›</span>
        </RouterLink>
      <RouterLink v-for="[label,section] in menus" :key="section" :to="target(section!)">
        {{label}} <small v-if="!member">로그인 필요</small>
        <span>›</span>
      </RouterLink>
    </nav>
    <button v-if="member" class="app-account-primary" @click="signout">로그아웃</button>
    <p role="status">{{error}}</p>
  </CustomerAppPage>
</template>
