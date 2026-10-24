<script setup lang="ts">
import { computed, ref, watch } from 'vue'
const props=defineProps<{content?:string; messages?:string[]; example?:boolean}>()
const index=ref(0)
const pages=computed(()=>{
  const supplied=props.messages?.filter(message=>typeof message==='string'&&message.trim())
  if(supplied?.length)return supplied
  if(!props.content)return []
  return props.example?[props.content,'차간거리를 유지하세요\n서행 운전 바랍니다','우천 시 감속 운행\n안전거리를 확보하세요']:[props.content]
})
watch(pages,()=>{index.value=0})
function move(amount:number){if(pages.value.length)index.value=(index.value+amount+pages.value.length)%pages.value.length}
let touchX:number|undefined
function touchEnd(event:TouchEvent){const end=event.changedTouches[0];if(touchX!==undefined&&end&&Math.abs(end.clientX-touchX)>45)move(end.clientX<touchX?1:-1);touchX=undefined}
</script>
<template>
  <section v-if="pages.length" class="vms-led-carousel" aria-label="전광판 표출 문구">
    <div class="vms-display" tabindex="0" aria-label="좌우 방향키로 전광판 문구 넘기기" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)" @touchstart.passive="touchX=$event.touches[0]?.clientX" @touchend.passive="touchEnd" @touchcancel="touchX=undefined">
      <div class="vms-led-caption"><span class="vms-led-indicator"></span>도로 안내</div>
      <div :key="index" class="vms-led-text" aria-live="polite" aria-atomic="true">{{pages[index]}}</div>
    </div>
    <nav v-if="pages.length>1" class="vms-led-controls" aria-label="전광판 문구 넘기기"><button type="button" aria-label="이전 문구" @click="move(-1)">‹</button><div class="vms-led-dots"><button v-for="(_,position) in pages" :key="position" type="button" :aria-label="`${position+1}번째 문구`" :aria-current="index===position?'true':undefined" @click="index=position"></button></div><span class="vms-led-counter">{{index+1}} / {{pages.length}}</span><button type="button" aria-label="다음 문구" @click="move(1)">›</button></nav>
  </section>
</template>
<style scoped src="../styles/components/vms-led-display.css"></style>
