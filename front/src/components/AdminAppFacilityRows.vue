<script setup lang="ts">
import {
  riskGroup,
  matchesRisk,
  boardCategoryLabel,
  riskSummaryLabel,
} from '../services/backendCatalog'
import { valueLabels } from '../data/backendResources'
defineProps<{ items: Record<string, any>[] }>()
</script>
<template>
  <div class="app-facility-rows">
    <RouterLink v-for="row in items" :key="row.id" :to="'/admin-app/screen-5?id='+row.id">
      <div class="g">
        <b>{{row.name}}</b>
        <small>
          {{row.currentStatus==='BLOCKED'?'통제 중':'정상 운영'}} · 차량 {{row.remainingCount??'—'}}대</small>
      </div>
      <span
        class="lv"
        :class="row.currentStatus==='BLOCKED'||riskGroup(row.currentRiskLevel)==='DANGER'?'d':riskGroup(row.currentRiskLevel)==='WARNING'?'w':'s'"
      >
        {{riskSummaryLabel(row.currentRiskLevel,row.currentStatus==='BLOCKED')}}
      </span>
    </RouterLink>
    <p v-if="!items.length">조회 조건에 맞는 시설이 없습니다.</p>
  </div>
</template>
