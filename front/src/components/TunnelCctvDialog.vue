<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import TrafficVideo from './TrafficVideo.vue'
import { getTunnelCameras, getTunnelCameraStream, seoulTunnelSource, type TunnelCamera, type TunnelCameraStream } from '../services/seoulTunnelCctv'

defineProps<{ facilityName?: string }>()
const dialog = ref<HTMLDialogElement>(), opened = ref(false), loading = ref(false), videoLoading = ref(false)
const cameras = ref<TunnelCamera[]>([]), selected = ref(''), stream = ref<TunnelCameraStream>(), error = ref('')
const names = ref<Record<string, string>>({}), playbackVersion = ref(0)
let listRequest: AbortController | undefined, videoRequest: AbortController | undefined

async function loadList() {
  listRequest?.abort()
  const request = new AbortController()
  listRequest = request
  const timeout = setTimeout(() => request.abort(), 15000)
  loading.value = true
  error.value = ''
  try {
    const rows = await getTunnelCameras(request.signal)
    if (listRequest !== request || !opened.value) return
    cameras.value = rows
    selected.value = rows[0]?.id || ''
  } catch (cause) {
    if (listRequest !== request || !opened.value) return
    error.value = request.signal.aborted ? 'CCTV 목록 응답이 지연되고 있습니다. 다시 시도해 주세요.' : cause instanceof Error ? cause.message : 'CCTV 목록을 불러오지 못했습니다.'
  } finally {
    clearTimeout(timeout)
    if (listRequest === request) loading.value = false
  }
}
async function loadStream() {
  videoRequest?.abort()
  stream.value = undefined
  if (!selected.value || !opened.value) return
  const id = selected.value, request = new AbortController()
  videoRequest = request
  const timeout = setTimeout(() => request.abort(), 15000)
  videoLoading.value = true
  error.value = ''
  try {
    const result = await getTunnelCameraStream(id, request.signal)
    if (videoRequest !== request || !opened.value || selected.value !== id) return
    stream.value = result
    names.value[id] = [result.name, result.direction].filter(Boolean).join(' · ')
    playbackVersion.value++
  } catch (cause) {
    if (videoRequest !== request || !opened.value) return
    error.value = request.signal.aborted ? 'CCTV 영상 정보 응답이 지연되고 있습니다. 다시 시도해 주세요.' : cause instanceof Error ? cause.message : 'CCTV 영상을 불러오지 못했습니다.'
  } finally {
    clearTimeout(timeout)
    if (videoRequest === request) videoLoading.value = false
  }
}
watch(selected, loadStream)
async function open() {
  opened.value = true
  await nextTick()
  dialog.value?.showModal()
  void loadList()
}
function release() {
  opened.value = false
  listRequest?.abort()
  videoRequest?.abort()
  listRequest = videoRequest = undefined
  stream.value = undefined
  cameras.value = []
  selected.value = ''
  loading.value = videoLoading.value = false
  error.value = ''
}
function close() { dialog.value?.close(); release() }
onBeforeUnmount(release)
</script>
<template>
  <button class="btn tunnel-cctv-trigger" type="button" @click="open">내부 CCTV 보기</button>
  <Teleport to="body">
    <dialog ref="dialog" class="tunnel-cctv-modal" aria-label="신월여의지하도로 내부 CCTV" @close="release" @cancel="release">
      <header>
        <div><p class="tunnel-cctv-eyebrow">서울터널 · 공개 교통 영상</p><h2>신월여의지하도로 CCTV</h2></div>
        <button class="tunnel-cctv-close" type="button" aria-label="내부 CCTV 창 닫기" @click="close">닫기</button>
      </header>
      <p class="tunnel-cctv-source-note">서울 신월여의지하도로의 공개 영상입니다.<template v-if="facilityName"> 현재 선택한 {{facilityName}}의 내부 영상과는 다른 시설입니다.</template></p>
      <div v-if="opened" class="tunnel-cctv-content">
        <p v-if="loading" role="status">공개 CCTV 목록을 불러오는 중입니다.</p>
        <label v-if="cameras.length" class="tunnel-cctv-select">공개 CCTV 선택 <small>{{cameras.length}}대</small>
          <select v-model="selected" aria-label="신월여의지하도로 CCTV 선택">
            <option v-for="(camera,index) in cameras" :key="camera.id" :value="camera.id">{{names[camera.id] || `${String(index+1).padStart(2,'0')} · 카메라 ${camera.id}`}}</option>
          </select>
        </label>
        <p v-if="videoLoading" role="status">선택한 CCTV 영상을 연결하는 중입니다.</p>
        <p v-if="error" class="tunnel-cctv-error" role="alert">{{error}}</p>
        <section v-if="stream" class="tunnel-cctv-player" :aria-label="names[stream.id]">
          <h3>{{stream.name}}<span v-if="stream.direction">{{stream.direction}}</span></h3>
          <TrafficVideo :key="stream.id+':'+playbackVersion" :src="stream.streamUrl" hls />
        </section>
        <p v-if="!loading&&!cameras.length&&!error" role="status">현재 제공되는 공개 CCTV가 없습니다.</p>
      </div>
      <footer>
        <p>제공처 사정에 따라 영상이 지연되거나 일시 중단될 수 있습니다.</p>
        <div><button type="button" :disabled="loading||videoLoading" @click="selected?loadStream():loadList()">다시 연결</button><a :href="seoulTunnelSource" target="_blank" rel="noopener noreferrer">공식 사이트에서 보기 ↗</a></div>
      </footer>
    </dialog>
  </Teleport>
</template>
<style scoped src="../styles/components/tunnel-cctv-dialog.css"></style>
