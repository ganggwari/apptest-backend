<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BusanCctvViewer from './BusanCctvViewer.vue'
const open = defineModel<boolean>({ default: false }),
  props = defineProps<{ facilityName: string; lat?: number | null; lng?: number | null }>(),
  dialog = ref<HTMLDialogElement>()
const route = useRoute()
const appScreen = computed(() => /^\/(customer|admin)-app\//.test(route.path))
watch(open, async (value) => {
  await nextTick()
  if (value && !dialog.value?.open) dialog.value?.showModal()
  if (!value && dialog.value?.open) dialog.value.close()
})
function outside(event: MouseEvent) {
  if (event.target !== dialog.value) return
  const rect = dialog.value.getBoundingClientRect()
  if (
    event.clientX < rect.left ||
    event.clientX > rect.right ||
    event.clientY < rect.top ||
    event.clientY > rect.bottom
  )
    open.value = false
}
onBeforeUnmount(() => dialog.value?.close())
</script>
<template>
  <dialog
    ref="dialog"
    class="busan-cctv-modal"
    :class="{ 'app-cctv-modal': appScreen }"
    :aria-label="props.facilityName+' CCTV'"
    @close="open=false"
    @cancel.prevent="open=false"
    @click="outside"
  >
    <header>
      <h2>{{props.facilityName}} · CCTV</h2>
    </header>
    <BusanCctvViewer
      v-if="open"
      :facility-name="props.facilityName"
      :lat="props.lat"
      :lng="props.lng"
    />
    <footer class="cctv-confirm-footer">
      <button type="button" @click="open=false">확인</button>
    </footer>
  </dialog>
</template>
