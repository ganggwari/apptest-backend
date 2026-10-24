<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { loadPostcode, type PostalAddress } from '../services/postcode'
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ select: [address: PostalAddress] }>(),
  dialog = ref<HTMLDialogElement>(),
  host = ref<HTMLElement>(),
  loading = ref(false),
  error = ref('')
let alive = true,
  request = 0,
  trigger: HTMLElement | null = null
function close() {
  request++
  dialog.value?.close()
  host.value?.replaceChildren()
  loading.value = false
  trigger?.focus()
}
async function open(event: MouseEvent) {
  trigger = event.currentTarget as HTMLElement
  error.value = ''
  loading.value = true
  const ticket = ++request
  dialog.value?.showModal()
  try {
    const Postcode = await loadPostcode()
    if (!alive || ticket !== request || !host.value) return
    new Postcode({
      width: '100%',
      height: '100%',
      oncomplete: (data: any) => {
        if (!alive || ticket !== request) return
        const address = String(
          data.roadAddress || data.autoRoadAddress || data.jibunAddress || data.address || '',
        )
        if (!address) {
          error.value = '주소를 선택해 주세요.'
          return
        }
        close()
        emit('select', { zipcode: String(data.zonecode || ''), roadAddress: address })
        queueMicrotask(() =>
          trigger
            ?.closest('form')
            ?.querySelector<HTMLInputElement>('input[autocomplete="address-line2"]')
            ?.focus(),
        )
      },
    }).embed(host.value)
    loading.value = false
  } catch (e) {
    if (alive && ticket === request) {
      error.value = (e as Error).message
      loading.value = false
    }
  }
}
onBeforeUnmount(() => {
  alive = false
  close()
})
</script>
<template>
  <span class="postcode-search-control">
    <button type="button" class="postcode-search-button" :disabled="disabled" @click="open">우편번호 찾기</button>
  </span>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="postcode-search-dialog"
      aria-label="우편번호·주소 검색"
      @cancel.prevent="close"
      @click="($event.target===dialog)&&close()"
    >
      <header>
        <h2>우편번호·주소 검색</h2>
        <button type="button" @click="close">닫기</button>
      </header>
      <p v-if="loading" role="status">주소 검색을 불러오는 중입니다.</p>
      <p v-if="error" role="alert">{{error}}</p>
      <div ref="host" class="postcode-search-host"></div>
    </dialog>
  </Teleport>
</template>
<style scoped src="../styles/components/postcode-search.css"></style>
