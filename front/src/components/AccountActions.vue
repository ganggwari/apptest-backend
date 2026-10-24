<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
const app = useApp(),
  route = useRoute(),
  router = useRouter(),
  dialog = ref<HTMLDialogElement>(),
  password = ref(''),
  error = ref(''),
  busy = ref(false)
const passwordDialog = ref<HTMLDialogElement>(),
  currentPassword = ref(''),
  newPassword = ref(''),
  newPasswordConfirm = ref(''),
  passwordError = ref(''),
  message = ref('')
function openPassword() {
  currentPassword.value = ''
  newPassword.value = ''
  newPasswordConfirm.value = ''
  passwordError.value = ''
  message.value = ''
  passwordDialog.value?.showModal()
}
async function changePassword() {
  if (busy.value) return
  passwordError.value = ''
  if (newPassword.value !== newPasswordConfirm.value) {
    passwordError.value = '새 비밀번호 확인이 일치하지 않습니다.'
    return
  }
  if (!live()) {
    passwordError.value = '관리자 로그인 후 비밀번호를 변경할 수 있습니다.'
    return
  }
  busy.value = true
  try {
    await backend('/api/managers/me/password', {
      method: 'PUT',
      body: {
        currentPassword: currentPassword.value,
        newPassword: newPassword.value,
        newPasswordConfirm: newPasswordConfirm.value,
      },
    })
    currentPassword.value = ''
    newPassword.value = ''
    newPasswordConfirm.value = ''
    passwordDialog.value?.close()
    message.value = '비밀번호를 변경했습니다.'
  } catch (e) {
    passwordError.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
const live = () =>
  !!app.account ||
  localStorage.getItem('routel-session-kind') === 'manager' ||
  import.meta.env.VITE_DATA_MODE === 'server'
async function withdraw() {
  if (busy.value) return
  error.value = ''
  if (!live()) {
    error.value = '관리자 로그인 후 회원탈퇴를 진행해 주세요.'
    return
  }
  busy.value = true
  try {
    await backend('/api/managers/me/withdraw', {
      method: 'POST',
      body: { password: password.value },
    })
    app.authVersion++
    app.clearAccount()
    dialog.value?.close()
    await router.push(route.path.startsWith('/admin-app/') ? '/admin-app/screen-1' : '/admin-login')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="account-actions">
    <h2>계정 관리</h2>
    <div>
      <button type="button" :disabled="busy" @click="openPassword">비밀번호 변경</button>
      <button type="button" :disabled="busy" @click="error='';password='';dialog?.showModal()">회원탈퇴</button>
    </div>
    <p v-if="error&&!dialog?.open" role="alert">{{error}}</p>
    <dialog
      ref="dialog"
      class="account-withdraw"
      @click="($event.target===dialog)&&!busy&&dialog?.close()"
    >
      <form @submit.prevent="withdraw">
        <h2>회원탈퇴</h2>
        <p>탈퇴 후 이 계정으로 관리자 서비스를 이용할 수 없습니다.</p>
        <label>본인 확인 비밀번호<PasswordInput
            v-model="password"
            autocomplete="current-password"
            required
            :disabled="busy"
          />
        </label>
        <p v-if="error" role="alert">{{error}}</p>
        <div>
          <button type="button" :disabled="busy" @click="dialog?.close()">취소</button>
          <button type="submit" :disabled="busy">회원탈퇴</button>
        </div>
      </form>
    </dialog>
    <p v-if="message" role="status">{{message}}</p>
    <dialog ref="passwordDialog" class="account-withdraw" aria-labelledby="change-password-title">
      <form @submit.prevent="changePassword">
        <h2 id="change-password-title">비밀번호 변경</h2>
        <label>현재 비밀번호<PasswordInput
            v-model="currentPassword"
            autocomplete="current-password"
            required
            :disabled="busy"
          />
        </label>
        <label>새 비밀번호<PasswordInput
            v-model="newPassword"
            autocomplete="new-password"
            required
            :disabled="busy"
          />
        </label>
        <label>새 비밀번호 확인<PasswordInput
            v-model="newPasswordConfirm"
            :match-value="newPassword"
            autocomplete="new-password"
            required
            :disabled="busy"
          />
        </label>
        <p v-if="passwordError" role="alert">{{passwordError}}</p>
        <div>
          <button type="button" :disabled="busy" @click="passwordDialog?.close()">취소</button>
          <button type="submit" :disabled="busy">비밀번호 변경</button>
        </div>
      </form>
    </dialog>
  </section>
</template>
<style scoped src="../styles/components/account-actions.css"></style>
