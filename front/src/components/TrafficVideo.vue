<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
const props = defineProps<{ src: string; hls?: boolean }>(),
  video = ref<HTMLVideoElement>(),
  error = ref('')
let hls: import('hls.js').default | undefined,
  version = 0
async function start(src: string) {
  const ticket = ++version
  hls?.destroy()
  hls = undefined
  error.value = ''
  await nextTick()
  if (!video.value || ticket !== version) return
  if (props.hls || /\.m3u8(?:\?|$)/i.test(src)) {
    const { default: Hls } = await import('hls.js')
    if (ticket !== version || !video.value) return
    if (!Hls.isSupported()) {
      if (video.value.canPlayType('application/vnd.apple.mpegurl')) {
        video.value.src = src
        video.value.play().catch(() => {})
        return
      }
      error.value = '이 브라우저에서는 스트리밍 재생을 지원하지 않습니다.'
      return
    }
    hls = new Hls()
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) error.value = '영상 제공처에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.'
    })
    hls.on(Hls.Events.MANIFEST_PARSED, () => video.value?.play().catch(() => {}))
    hls.loadSource(src)
    hls.attachMedia(video.value)
  } else {
    video.value.src = src
    video.value.play().catch(() => {})
  }
}
onMounted(() => void start(props.src))
watch(
  () => props.src,
  (src) => void start(src),
  { flush: 'post' },
)
onBeforeUnmount(() => {
  version++
  hls?.destroy()
  if (video.value) {
    video.value.pause()
    video.value.removeAttribute('src')
    video.value.load()
  }
})
</script>
<template>
  <div>
    <video
      ref="video"
      controls
      playsinline
      autoplay
      muted
      @error="error='영상을 재생하지 못했습니다.'"
    ></video>
    <p v-if="error" role="status">{{error}}</p>
  </div>
</template>
