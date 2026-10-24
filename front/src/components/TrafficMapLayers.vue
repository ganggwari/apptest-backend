<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getBusanCameras } from '../services/busanCctv'
import TrafficVideo from './TrafficVideo.vue'
import VmsLedDisplay from './VmsLedDisplay.vue'
type Point = { id: string; name: string; address: string; lat: number; lng: number; kind: 'cctv' | 'vms'; type?: string; url?: string; content?: string; messages?: string[]; example?: boolean; updatedAt?: string }
const props = defineProps<{ map: any; maps: any; toggleControls?: boolean }>()
const showCctv = ref(false), showVms = ref(false), loading = ref(false), error = ref('')
const controlsExpanded = ref(true)
const points = ref<Point[]>([]), active = ref<Point>(), dialog = ref<HTMLDialogElement>()
const groupItems = ref<Point[]>([])
function closeInfo() { active.value = undefined; groupItems.value = [] }
let backdropPress = false
function outsideDialog(event: PointerEvent | MouseEvent) {
  if (event.target !== dialog.value || !dialog.value) return false
  const bounds = dialog.value.getBoundingClientRect()
  return event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom
}
function backdropClick(event: MouseEvent) {
  if (backdropPress && outsideDialog(event)) closeInfo()
  backdropPress = false
}
let overlays: any[] = [], disposed = false, generation = 0
const icon = (kind: 'cctv' | 'vms') => kind === 'cctv'
  ? '<svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path fill="currentColor" stroke="none" d="m4 4 14 3-2 9L2 11z"/><path stroke="white" stroke-width="1.4" d="m6 7 8 2"/><path d="m18 9 3 1-1 4-3-1M8 14l-1 5h7M4 19h3"/></svg>'
  : '<svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2.5" y="3" width="19" height="13" rx="2.5" fill="currentColor" stroke="none"/><path stroke="white" stroke-width="1.5" d="M6 7h5m3 0h4M6 11h12"/><path d="M7 16v5m10-5v5M5 21h4m6 0h4"/></svg>'
function clear() { overlays.forEach(overlay => overlay.setMap(null)); overlays = [] }
function draw() {
  clear()
  if (!props.map || !props.maps) return
  const bounds = props.map.getBounds()
  const projection = props.map.getProjection()
  const groups: { points: Point[]; pixels: { x: number; y: number }[] }[] = []
  for (const point of points.value) {
    if (!(point.kind === 'cctv' ? showCctv.value : showVms.value)) continue
    const position = new props.maps.LatLng(point.lat, point.lng)
    if (!bounds.contain(position)) continue
    const pixel = projection.containerPointFromCoords(position)
    const overlaps = groups.filter(group => group.pixels.some(other => Math.abs(other.x - pixel.x) < 40 && Math.abs(other.y - pixel.y) < 40))
    const group = overlaps[0] || { points: [], pixels: [] }
    if (!overlaps.length) groups.push(group)
    for (const other of overlaps.slice(1)) {
      group.points.push(...other.points); group.pixels.push(...other.pixels)
      groups.splice(groups.indexOf(other), 1)
    }
    group.points.push(point); group.pixels.push(pixel)
  }
  for (const group of groups) {
    const point = group.points[0]!
    const bundled = group.points.length > 1
    const position = new props.maps.LatLng(
      group.points.reduce((sum, row) => sum + row.lat, 0) / group.points.length,
      group.points.reduce((sum, row) => sum + row.lng, 0) / group.points.length,
    )
    const button = document.createElement('button')
    button.type = 'button'; button.className = `traffic-device-marker ${bundled ? 'device-group' : point.kind}`
    button.dataset.cctvCount = String(group.points.filter(row => row.kind === 'cctv').length)
    button.dataset.vmsCount = String(group.points.filter(row => row.kind === 'vms').length)
    button.title = bundled ? `CCTV·전광판 ${group.points.length}개 목록 보기` : point.name
    button.setAttribute('aria-label', bundled ? button.title : `${point.kind === 'cctv' ? 'CCTV' : 'VMS 전광판'} · ${point.name}`)
    if (bundled) button.textContent = String(group.points.length)
    else button.innerHTML = icon(point.kind)
    button.onclick = event => {
      event.stopPropagation()
      groupItems.value = bundled ? [...group.points] : []
      active.value = bundled ? undefined : point
    }
    button.onmousedown = () => props.maps.event.preventMap()
    overlays.push(new props.maps.CustomOverlay({ map: props.map, position, content: button, yAnchor: 1, zIndex: 6 }))
  }
}
async function load() {
  const ticket = ++generation
  error.value = ''
  if (!showCctv.value && !showVms.value) { loading.value = false; draw(); return }
  loading.value = true
  const results = await Promise.allSettled([
    showCctv.value ? getBusanCameras() : Promise.resolve([]),
    showVms.value ? fetch('/map-vms.json', { cache: 'no-store' }).then(async response => {
      if (!response.ok) throw Error('VMS 목록을 불러오지 못했습니다.')
      const rows = await response.json()
      if (!Array.isArray(rows)) throw Error('VMS 목록 형식이 올바르지 않습니다.')
      return rows
    }) : Promise.resolve([]),
  ])
  if (disposed || ticket !== generation) return
  const rows: Point[] = [], messages: string[] = []
  results.forEach((result, index) => {
    if (result.status === 'rejected') { messages.push(result.reason?.message || '교통 정보를 불러오지 못했습니다.'); return }
    for (const row of result.value) {
      if (row.lat == null || row.lng == null || !Number.isFinite(Number(row.lat)) || !Number.isFinite(Number(row.lng)) || Math.abs(Number(row.lat)) > 90 || Math.abs(Number(row.lng)) > 180) continue
      rows.push({ ...row, id: String(row.id), lat: Number(row.lat), lng: Number(row.lng), kind: index === 0 ? 'cctv' : 'vms' })
    }
  })
  if (showVms.value && !rows.some(row => row.kind === 'vms') && results[1]?.status === 'fulfilled') messages.push('등록된 VMS 전광판이 없습니다.')
  points.value = rows; error.value = messages.join(' '); loading.value = false; draw()
}
watch([showCctv, showVms], () => { closeInfo(); void load() })
watch(() => props.map, (map, previous) => {
  if (previous && props.maps) {
    props.maps.event.removeListener(previous, 'idle', draw)
    props.maps.event.removeListener(previous, 'zoom_changed', draw)
  }
  if (map && props.maps) {
    props.maps.event.addListener(map, 'idle', draw)
    props.maps.event.addListener(map, 'zoom_changed', draw)
  }
  draw()
}, { immediate: true })
watch([active, groupItems], async () => {
  await nextTick()
  if ((active.value || groupItems.value.length) && !dialog.value?.open) dialog.value?.showModal()
  if (!active.value && !groupItems.value.length) dialog.value?.close()
})
onBeforeUnmount(() => { disposed = true; generation++; clear(); if (props.map && props.maps) { props.maps.event.removeListener(props.map, 'idle', draw); props.maps.event.removeListener(props.map, 'zoom_changed', draw) } dialog.value?.close() })
</script>
<template>
  <div class="traffic-map-layers" :class="{'traffic-map-toggles':toggleControls,'traffic-controls-collapsed':toggleControls&&!controlsExpanded}" aria-label="교통 시설 표시">
    <button v-if="toggleControls" type="button" class="traffic-controls-heading" :aria-expanded="controlsExpanded" @click="controlsExpanded=!controlsExpanded">
      <span>교통 시설</span><span aria-hidden="true">{{controlsExpanded?'‹':'›'}}</span>
    </button>
    <div v-show="!toggleControls||controlsExpanded" class="traffic-controls-body">
    <div class="traffic-layer-switches">
      <button type="button" :aria-pressed="showCctv" @click="showCctv=!showCctv"><span v-html="icon('cctv')"></span>CCTV<span v-if="toggleControls" class="traffic-toggle-track" aria-hidden="true"></span></button>
      <button type="button" :aria-pressed="showVms" @click="showVms=!showVms"><span v-html="icon('vms')"></span>VMS 전광판<span v-if="toggleControls" class="traffic-toggle-track" aria-hidden="true"></span></button>
    </div>
    <p v-if="loading" role="status">교통 시설을 불러오는 중…</p>
    <p v-if="error" role="status">{{error}} <button type="button" @click="load">다시 조회</button></p>
    </div>
  </div>
  <Teleport to="body">
  <dialog ref="dialog" class="traffic-device-dialog" aria-label="교통 시설 정보" @cancel.prevent="closeInfo" @close="closeInfo" @pointerdown.stop="backdropPress=outsideDialog($event)" @pointercancel="backdropPress=false" @click.stop="backdropClick">
    <template v-if="!active && groupItems.length">
      <header><span class="traffic-device-type">이 위치의 CCTV·전광판 {{groupItems.length}}개</span><button type="button" aria-label="닫기" @click="closeInfo">✕</button></header>
      <h2>확인할 시설을 선택하세요</h2>
      <ul class="traffic-device-list">
        <li v-for="point in groupItems" :key="point.kind+':'+point.id">
          <button type="button" @click="active=point"><span class="device-list-icon" :class="point.kind" v-html="icon(point.kind)"></span><span><small>{{point.kind==='cctv'?'CCTV':'VMS 전광판'}}</small><strong>{{point.name}}</strong></span><span aria-hidden="true">›</span></button>
        </li>
      </ul>
    </template>
    <template v-if="active">
      <header><button v-if="groupItems.length" type="button" @click="active=undefined" aria-label="목록으로 돌아가기">←</button><span class="traffic-device-type">{{active.kind==='cctv'?'도로 CCTV':'VMS 전광판'}}</span><button type="button" aria-label="닫기" @click="closeInfo">✕</button></header>
      <h2>{{active.name}}</h2><p v-if="active.address">{{active.address}}</p>
      <TrafficVideo v-if="active.kind==='cctv' && active.url" :key="active.id" :src="active.url" hls />
      <template v-else-if="active.kind==='vms'"><p v-if="active.type">전광판 규격: {{active.type}}</p><p>위치: {{active.lat}}, {{active.lng}}</p><p v-if="active.example" class="vms-example-note">예시 문구 · 실제 전광판 표출 내용과 다를 수 있습니다.</p><VmsLedDisplay v-if="active.content || active.messages?.length" :key="active.id" :content="active.content" :messages="active.messages" :example="active.example" /><p v-else>현재 표출 문구는 제공되지 않습니다.</p><p v-if="active.updatedAt">갱신 시각: {{active.updatedAt}}</p></template>
      <small v-if="active.kind==='cctv'">부산광역시 교통정보 제공 · 제공처 사정에 따라 영상이 지연되거나 중단될 수 있습니다.</small>
    </template>
  </dialog>
  </Teleport>
</template>
<style src="../styles/components/traffic-map-layers.css"></style>
