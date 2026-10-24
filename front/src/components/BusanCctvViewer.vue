<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import TrafficVideo from './TrafficVideo.vue'
import {
  getBusanCameras,
  distanceMeters,
  findFacilityLocation,
  type BusanCamera,
} from '../services/busanCctv'
import '../styles/busan-cctv.css'
const props = defineProps<{ facilityName: string; lat?: number | null; lng?: number | null }>()
const cameras = ref<BusanCamera[]>([]),
  selected = ref(''),
  loading = ref(false),
  error = ref(''),
  reload = ref(0)
const current = computed(() => cameras.value.find((camera) => camera.id === selected.value))
let version = 0
watch(
  [() => props.facilityName, () => props.lat, () => props.lng, reload],
  async () => {
    const ticket = ++version
    loading.value = true
    error.value = ''
    cameras.value = []
    selected.value = ''
    try {
      const [all, location] = await Promise.all([
        getBusanCameras(),
        props.lat != null && props.lng != null
          ? Promise.resolve({ lat: props.lat, lng: props.lng })
          : findFacilityLocation(props.facilityName),
      ])
      if (ticket !== version) return
      if (!location) {
        error.value = '선택한 시설의 좌표를 확인하지 못했습니다.'
        return
      }
      const name = props.facilityName.replace(/\s/g, '')
      cameras.value = all
        .map((camera) => ({
          ...camera,
          distance: distanceMeters(location.lat, location.lng, camera),
        }))
        .filter((camera) => camera.distance! <= 5000)
        .sort(
          (a, b) =>
            Number(b.name.replace(/\s/g, '').includes(name)) -
              Number(a.name.replace(/\s/g, '').includes(name)) || a.distance! - b.distance!,
        )
        .slice(0, 15)
      selected.value = cameras.value[0]?.id || ''
    } catch (cause) {
      if (ticket === version)
        error.value = cause instanceof Error ? cause.message : 'CCTV 목록 연결에 실패했습니다.'
    } finally {
      if (ticket === version) loading.value = false
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => version++)
const distance = (meters?: number) =>
  meters == null ? '' : meters < 1000 ? `${Math.round(meters)}m` : `${(meters / 1000).toFixed(1)}km`
</script>
<template>
  <section class="busan-cctv-viewer" aria-label="부산시 CCTV 영상">
    <p v-if="loading" class="busan-cctv-status" role="status">부산시 CCTV를 불러오는 중입니다.</p>
    <div v-else-if="error" class="busan-cctv-status" role="status">
      <p>{{error}}</p>
      <button type="button" @click="reload++">다시 시도</button>
    </div>
    <template v-else-if="current">
      <label class="busan-cctv-select">주변 CCTV 선택<select v-model="selected">
          <option v-for="camera in cameras" :key="camera.id" :value="camera.id">
            {{camera.name}} · {{distance(camera.distance)}}
          </option>
        </select>
      </label>
      <TrafficVideo :key="current.id" :src="current.url" :hls="true" />
      <div class="busan-cctv-camera-info">
        <strong>{{current.name}}</strong>
        <span>{{current.address}}</span>
        <small>선택 시설에서 약 {{distance(current.distance)}} · 부산광역시 교통정보서비스센터</small>
        <p class="busan-cctv-disclaimer">인근 도로 CCTV입니다. 선택한 지하차도의 내부 영상과 다를 수 있으며 제공처 상황에 따라 재생이 지연될 수 있습니다.</p>
      </div>
    </template>
    <p v-else class="busan-cctv-status">선택한 시설 반경 5km 이내에 제공되는 CCTV가 없습니다.</p>
  </section>
</template>
