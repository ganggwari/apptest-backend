<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { backend } from '../services/backend'
import { useCustomerNotifications } from '../stores/customerNotifications'
import { enableWebPush, disableWebPush, webPushSupported, deviceTokenPath } from '../services/push'
import { alertCueEnabled, setAlertCueEnabled, playAlertCue } from '../utils/alertCue'
const notifications = useCustomerNotifications(),
  devices = ref<{ id: number; platform: string; active: boolean }[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  message = ref('')
const cue = ref(alertCueEnabled())
function toggleCue() {
  cue.value = !cue.value
  setAlertCueEnabled(cue.value)
  if (cue.value) playAlertCue()
}
const supported = 'Notification' in window,
  permission = ref(supported ? Notification.permission : 'unsupported')
const controller = new AbortController()
async function load() {
  loading.value = true
  error.value = ''
  try {
    devices.value =
      (await backend(deviceTokenPath(), { signal: controller.signal })) || []
  } catch (e) {
    if (!controller.signal.aborted) error.value = (e as Error).message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
async function toggle() {
  if (busy.value) return
  busy.value = true
  message.value = ''
  try {
    if (notifications.browserEnabled) {
      notifications.setBrowserEnabled(false)
      await disableWebPush()
      message.value = '이 브라우저의 새 알림을 껐습니다.'
      return
    }
    if (!supported) {
      message.value = '이 브라우저는 알림을 지원하지 않습니다.'
      return
    }
    const result = await Notification.requestPermission()
    permission.value = result
    if (result !== 'granted') {
      message.value =
        result === 'denied'
          ? '브라우저의 사이트 설정에서 알림 권한을 허용해 주세요.'
          : '알림 권한을 허용하면 새 알림을 받을 수 있습니다.'
      return
    }
    notifications.setBrowserEnabled(true)
    if (webPushSupported()) {
      const pushed = await enableWebPush()
      message.value = pushed.ok
        ? '알림을 켰습니다. 앱을 닫아도 푸시 알림을 받습니다.'
        : '알림을 켰습니다. (앱을 닫은 뒤의 푸시는 사용할 수 없습니다: ' + (pushed.reason || '') + ')'
      void load()
    } else message.value = '이 브라우저의 새 알림을 켰습니다.'
  } catch {
    message.value = '알림 권한을 확인하지 못했습니다. 브라우저의 사이트 설정을 확인해 주세요.'
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  void notifications.refresh()
  void load()
})
onBeforeUnmount(() => controller.abort())
</script>
<template>
  <section class="notification-settings" aria-labelledby="notification-settings-title">
    <h2 id="notification-settings-title">알림 수신 설정</h2>
    <div class="notification-setting-row">
      <div>
        <strong>이 브라우저 알림</strong>
        <p>페이지를 열어 둔 동안 새 알림을 알려드립니다. 이 브라우저에만 적용됩니다.</p>
      </div>
      <button
        type="button"
        class="notification-setting-switch"
        role="switch"
        :aria-checked="notifications.browserEnabled&&permission==='granted'"
        aria-label="이 브라우저 알림"
        :disabled="busy||!supported"
        @click="toggle"
      >
        {{notifications.browserEnabled&&permission==='granted'?'켜짐':'꺼짐'}}
      </button>
    </div>
    <div class="notification-setting-row">
      <div>
        <strong>알림 소리·진동</strong>
        <p>화면을 열어 둔 동안 새 알림이 오면 소리와 진동으로 알려드립니다. (앱을 닫은 뒤에는 기기의 알림 소리·진동 설정을 따릅니다.)</p>
      </div>
      <button type="button" class="notification-setting-switch" role="switch" :aria-checked="cue" aria-label="알림 소리와 진동" @click="toggleCue">
        {{cue?'켜짐':'꺼짐'}}
      </button>
    </div>
    <p v-if="permission==='denied'" class="notification-setting-help">알림 권한이 차단되어 있습니다. 주소창 옆 사이트 설정에서 허용할 수 있습니다.</p>
    <p v-if="!supported" class="notification-setting-help">현재 브라우저는 브라우저 알림을 지원하지 않습니다.</p>
    <p v-if="message" role="status">{{message}}</p>
    <div class="notification-device-heading">
      <h3>등록된 알림 기기</h3>
      <button type="button" :disabled="loading" @click="load">새로고침</button>
    </div>
    <p v-if="loading" role="status">등록된 기기를 확인하고 있습니다.</p>
    <div v-else-if="error" role="alert">
      <p>{{error}}</p>
      <button type="button" @click="load">다시 불러오기</button>
    </div>
    <ul v-else-if="devices.length" class="notification-device-list">
      <li v-for="(device,index) in devices" :key="device.id">
        <span>{{device.platform==='APP'?'모바일 앱':'웹 브라우저'}} {{index+1}}</span>
        <span class="notification-device-state">{{device.active?'알림 수신 중':'수신 중지'}}</span>
      </li>
    </ul>
    <p v-else class="notification-setting-help">등록된 기기가 없습니다. 앱에서 알림을 허용하면 연결된 기기가 표시됩니다.</p>
    <p v-if="devices.length" class="notification-setting-help">모바일 앱의 푸시 알림은 해당 기기의 알림 설정에서 변경할 수 있습니다.</p>
  </section>
</template>
<style scoped src="../styles/components/notification-settings.css"></style>
