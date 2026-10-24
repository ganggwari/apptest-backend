<script setup lang="ts">
import { displayDate } from '../utils/display-date'
import { valueLabels } from '../data/backendResources'
defineProps<{ record: Record<string, any>; image: string }>()
</script>
<template>
  <article class="ocr-record">
    <h3>{{record.underpassName||'시설 미제공'}} · OCR 인식 결과</h3>
    <div class="ocr-image">
      <img
        v-if="image&&!record.imageUnavailable"
        :src="image"
        alt="OCR 번호판 원본 이미지"
        @error="record.imageUnavailable=true"
      />
      <p v-else>등록된 번호판 이미지가 없거나 불러올 수 없습니다.</p>
    </div>
    <dl>
      <dt>인식 번호</dt>
      <dd>
        <strong>{{record.detectedNum||'인식값 없음'}}</strong>
      </dd>
      <dt>정정 번호</dt>
      <dd>{{record.correctedNum||'—'}}</dd>
      <dt>신뢰도</dt>
      <dd>{{record.confidence??'—'}}</dd>
      <dt>인식 상태</dt>
      <dd>{{valueLabels[record.recognitionStatus]||record.recognitionStatus||'—'}}</dd>
      <dt>판별 상태</dt>
      <dd>{{valueLabels[record.reviewStatus]||record.reviewStatus||'—'}}</dd>
      <dt>인식 시각</dt>
      <dd>{{displayDate(record.detectedAt)}}</dd>
      <dt>판별 시각</dt>
      <dd>{{displayDate(record.reviewedAt)}}</dd>
      <dt>카메라 번호</dt>
      <dd>{{record.cameraId??'—'}}</dd>
      <dt>판별 담당자 번호</dt>
      <dd>{{record.reviewedBy??'—'}}</dd>
    </dl>
    <RouterLink
      v-if="record.reviewStatus==='PENDING'"
      :to="{path:'/verify',query:{underpassId:record.underpassId,carNum:record.detectedNum||undefined}}"
    >이 시설의 수동 판별 대기 보기 →</RouterLink>
  </article>
</template>
<style scoped src="../styles/components/ocr-record-detail.css"></style>
