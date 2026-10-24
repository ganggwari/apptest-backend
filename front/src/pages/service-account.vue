<script setup lang="ts">
import AdminPageDirectory from '../components/AdminPageDirectory.vue'
import ServiceResource from './backend-resource.vue'
import FeedbackToast from '../components/FeedbackToast.vue'
import AccountActions from '../components/AccountActions.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
import BackendLayout from '../components/BackendLayout.vue'
import ServiceFields from '../components/ServiceFields.vue'
import { formatPhone } from '../utils/phone'
import type { Field } from '../data/backendResources'
const route = useRoute(),
  router = useRouter(),
  app = useApp(),
  manager = computed(() => route.path === '/admin-profile' || route.path.startsWith('/admin-app/')),
  base = computed(() => (manager.value ? '/api/managers/me' : '/api/customers/me')),
  profile = ref<Record<string, any>>({}),
  values = reactive<Record<string, any>>({}),
  passwords = reactive<Record<string, any>>({}),
  withdrawPassword = ref(''),
  error = ref(''),
  message = ref(''),
  loading = ref(true),
  busy = ref(false)
const editing = ref(false)
function cancelEdit() {
  for (const field of fields.value) values[field.key] = profile.value[field.key] || ''
  editing.value = false
}
const managerApp = computed(() => route.path.startsWith('/admin-app/'))
const controller = new AbortController()
const mobileCombined = computed(() => /^\/customer-app\/account\/(profile|emergency|cars)$/.test(route.path))
const combined = computed(() => !manager.value && (route.path === '/my-home' || mobileCombined.value))
const accountSection = computed(() =>
  route.path === '/my-withdraw' || route.path.endsWith('/withdraw')
    ? 'withdraw'
    : route.path === '/my-account' || route.path.endsWith('/settings')
      ? 'settings'
      : route.path === '/my-home'
        ? 'summary'
        : 'profile',
)
const pageTitle = computed(() =>
  mobileCombined.value
    ? '내 정보 관리'
    : manager.value
    ? '관리자 마이페이지'
    : accountSection.value === 'withdraw'
      ? '회원 탈퇴'
      : accountSection.value === 'settings'
        ? '계정 설정'
        : accountSection.value === 'summary'
          ? '내 정보'
          : '회원정보 수정',
)
const fields = computed<Field[]>(() =>
  manager.value
    ? [
        { key: 'phone', label: '연락처', required: true },
        { key: 'email', label: '이메일', type: 'email', required: manager.value },
      ]
    : [
        { key: 'name', label: '이름', required: true },
        { key: 'phone', label: '연락처', required: true },
        { key: 'email', label: '이메일', type: 'email', required: manager.value },
        { key: 'emergency', label: '비상 연락처' },
        { key: 'zipcode', label: '우편번호' },
        { key: 'roadAddress', label: '도로명 주소' },
        { key: 'detailAddress', label: '상세 주소' },
      ],
)
async function load() {
  try {
    profile.value = await backend(base.value, { signal: controller.signal })
    for (const field of fields.value) values[field.key] = profile.value[field.key] || ''
  } catch (e) {
    if (!controller.signal.aborted) error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function save() {
  await execute(async () => {
    profile.value = await backend(base.value, {
      method: 'PUT',
      body: Object.fromEntries(fields.value.map((field) => [field.key, values[field.key] || null])),
    })
    if (app.account) app.account = { ...app.account, name: profile.value.name }
    editing.value = false
    message.value = '기본 정보를 저장했습니다.'
  })
}
async function password() {
  await execute(async () => {
    if (passwords.newPassword !== passwords.newPasswordConfirm)
      throw new Error('새 비밀번호 확인이 일치하지 않습니다.')
    await backend(base.value + '/password', { method: 'PUT', body: passwords })
    for (const key of Object.keys(passwords)) passwords[key] = ''
    message.value = '비밀번호를 변경했습니다.'
  })
}
async function withdraw() {
  if (!window.confirm('정말 탈퇴하시겠습니까? 탈퇴 후 계정과 연결된 정보를 이용할 수 없습니다.'))
    return
  await execute(async () => {
    await backend(base.value + '/withdraw', {
      method: 'POST',
      body: { password: withdrawPassword.value },
    })
    app.clearAccount()
    await router.push(
      route.path.startsWith('/admin-app/')
        ? '/admin-app/screen-1'
        : route.path.startsWith('/customer-app/')
          ? '/customer-app/screen-1'
          : manager.value
            ? '/admin-login'
            : '/login',
    )
  })
}
async function execute(fn: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
watch(
  () => [profile.value.id, route.hash],
  async () => {
    if (
      !combined.value ||
      !profile.value.id ||
      !['#account-settings', '#account-cars'].includes(route.hash)
    )
      return
    await nextTick()
    document
      .getElementById(route.hash.slice(1))
      ?.scrollIntoView({ block: 'start', behavior: 'instant' })
  },
)
onMounted(load)
onBeforeUnmount(() => controller.abort())
</script>
<template>
  <component :is="manager?BackendLayout:'div'"
    ><section class="service-workspace account-service" :class="{'manager-account-page':managerApp}"
      ><h1>{{pageTitle}}</h1
      ><FeedbackToast :message="message" /><p v-if="loading" role="status"
        >정보를 불러오는 중입니다.</p
      ><p v-if="error" class="service-error" role="alert"
        >{{error}} <RouterLink :to="manager?'/admin-login':'/login'">로그인</RouterLink></p
      ><p v-if="message" class="service-message" role="status">{{message}}</p
      ><template v-if="profile.id"
        ><component
          :is="managerApp?'details':'section'"
          v-if="manager||['summary','profile','settings'].includes(accountSection)"
          :id="!manager?'account-settings':undefined"
          class="account-panel"
          ><summary v-if="managerApp" class="manager-profile-summary"><span class="manager-profile-avatar" aria-hidden="true">{{profile.name?.slice(0,1)}}</span><span><strong>{{profile.name}}님</strong><small>기본 정보 확인 및 수정</small></span><span class="manager-profile-chevron" aria-hidden="true">⌄</span></summary><h2>내 정보</h2
          ><p>{{profile.name}} · {{profile.loginId}}</p
          ><template v-if="manager"
            ><p
              >담당 지역: {{profile.regions?.map((region:any)=>region.name).join(' · ')||'미지정'}}</p
            ><p
              >근무조: {{profile.shift?.name||'미지정'}} {{profile.shift?.startTime||''}}–{{profile.shift?.endTime||''}}</p
            ><small>이름·담당 지역·근무조는 지정된 관리자 권한으로 변경됩니다.</small></template
          ><template v-if="!editing"
            ><dl class="account-summary"
              ><template v-for="field in fields" :key="field.key"
                ><dt>{{field.label}}</dt
                ><dd
                  >{{['phone','emergency'].includes(field.key)?formatPhone(profile[field.key])||'미등록':profile[field.key]||'미등록'}}</dd
                ></template
              ></dl
            ><button type="button" @click="editing=true">수정</button></template
          ><form v-if="editing" @submit.prevent="save"
            ><ServiceFields
              :fields="fields"
              :values="values"
              :choices="{}"
              :disabled="busy"
            /><button :disabled="busy" type="submit">기본 정보 저장</button
            ><button type="button" :disabled="busy" @click="cancelEdit">취소</button></form
          ></component
        ><section v-if="combined" id="account-cars" class="account-panel"
          ><ServiceResource resource-key="cars" embedded /></section
        ><section v-if="!manager&&(accountSection==='settings'||combined&&!mobileCombined)" class="account-panel"
          ><h2>비밀번호 변경</h2
          ><form @submit.prevent="password"
            ><label
              v-for="[key,label] in [['currentPassword','현재 비밀번호'],['newPassword','새 비밀번호'],['newPasswordConfirm','새 비밀번호 확인']]"
              :key="key"
              >{{label
              }}<PasswordInput
                v-model="passwords[key!]"
                :match-value="key==='newPasswordConfirm' ? (passwords.newPassword || '') : undefined"
                :autocomplete="key==='currentPassword'?'current-password':'new-password'"
                required
                :disabled="busy" /></label
            ><button :disabled="busy" type="submit">비밀번호 변경</button></form
          ></section
        ><section v-if="!manager&&accountSection==='withdraw'" class="account-panel"
          ><h2>회원 탈퇴</h2><p>탈퇴하면 계정과 연결된 정보를 더 이상 이용할 수 없습니다.</p
          ><form @submit.prevent="withdraw"
            ><label
              >본인 확인 비밀번호<PasswordInput
                v-model="withdrawPassword"
                autocomplete="current-password"
                required /></label
            ><button :disabled="busy" type="submit">계정 탈퇴</button></form
          ></section
        ></template
      ><nav v-if="managerApp" class="app-more-menu"
        ><RouterLink to="/admin-app/history">점검 이력 <span>›</span></RouterLink
        ><RouterLink to="/admin-app/notifications">알림 내역 <span>›</span></RouterLink
        ><RouterLink to="/admin-app/contacts">관할 담당자 연락처 <span>›</span></RouterLink></nav
      ><AdminPageDirectory v-else-if="manager" /><AccountActions v-if="manager" /></section
  ></component>
</template>
<style scoped src="../styles/pages/service-account.css"></style>
