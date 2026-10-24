<script setup lang="ts">
import { useAdminProfile } from '../stores/adminProfile'
import { useApp } from '../stores/app'
import { computed } from 'vue'
const admin = useAdminProfile(),
  app = useApp(),
  region = computed(() =>
    app.account?.role === 'REGIONAL_ADMIN'
      ? app.account.regions?.map((region) => region.name).join(' · ') || '담당 지역 미지정'
      : admin.region,
  )
</script>
<template>
  <RouterLink class="side-user" to="/admin-profile" aria-label="관리자 마이페이지로 이동"
    >{{region}} · 지역 관리자<br />{{app.account?.role==='REGIONAL_ADMIN'?app.account.name:admin.profile.name
    }}님</RouterLink
  >
</template>
