<script setup lang="ts">
import {
  riskGroup,
  matchesRisk,
  boardCategoryLabel,
  riskSummaryLabel,
} from '../services/backendCatalog'
import { ownsFacility } from '../services/facilityAccess'
import { valueLabels } from '../data/backendResources'
defineProps<{ items: Record<string, any>[]; total: number }>()
defineEmits<{
  detail: [row: Record<string, any>]
  edit: [row: Record<string, any>]
  remove: [row: Record<string, any>]
  control: [row: Record<string, any>]
}>()
const riskClass = (value: string) =>
  riskGroup(value) === 'DANGER' ? 'danger' : riskGroup(value) === 'WARNING' ? 'warn' : 'safe'
</script>
<template>
  <div class="restored-facilities">
    <div class="tbl-wrap">
      <table class="tbl" id="tbl">
        <thead>
          <tr>
            <th>지하차도</th>
            <th>위험 단계 · 점수</th>
            <th>통제</th>
            <th>통과 중 차량</th>
            <th>판별 대기</th>
            <th>지역</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>
              <button type="button" class="link row-open" @click="$emit('detail',row)">
                {{row.name}}
              </button>
            </td>
            <td>
              <span
                class="badge"
                :class="row.currentStatus==='BLOCKED'?'danger':riskClass(row.currentRiskLevel)"
              >
                {{riskSummaryLabel(row.currentRiskLevel,row.currentStatus==='BLOCKED')}}
              </span> <strong>{{row.riskScore??'—'}}</strong>
            </td>
            <td>
              <span :class="row.currentStatus==='BLOCKED'?'stat ack':'sub'">
                {{row.currentStatus==='BLOCKED'?'통제 중':'정상 운영'}}
              </span>
            </td>
            <td>{{row.remainingCount??'—'}}대</td>
            <td>{{row.pendingReviewCount??'—'}}건</td>
            <td>{{row.regionName||'—'}}</td>
            <td>
              <div class="row">
                <RouterLink
                  class="btn sm"
                  :to="'/fac-detail?id='+row.id"
                  target="_blank"
                  rel="noopener"
                >새 탭 ↗</RouterLink>
                <button
                  type="button"
                  class="btn sm control-action"
                  :disabled="!ownsFacility(row)"
                  :title="ownsFacility(row)?'':'담당 외 시설은 통제할 수 없습니다.'"
                  @click="$emit('control',row)"
                >
                  {{row.currentStatus==='BLOCKED'?'해제':'통제'}}
                </button>
              </div>
            </td>
          </tr>
<tr v-if="!items.length"><td colspan="7">조회된 지하차도가 없습니다.</td></tr>
        </tbody>
      </table>
    </div>
    <p class="sub">이름을 누르면 요약 서랍이 열려요. ‘새 탭 ↗’으로 여러 지하차도를 나란히 볼 수 있어요.</p>
  </div>
</template>
