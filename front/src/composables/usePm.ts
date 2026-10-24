// 예지보전(관리자 앱) 공통: 라벨, 불러오기, pm-update/알림 SSE 로 자동 새로고침
import { onBeforeUnmount } from 'vue'
import { useBackendEvents } from './useBackendEvents'

export const PM_TYPE: Record<string, string> = {
  PUMP: '배수펌프',
  DISPLAY: '전광판',
  CCTV: 'CCTV',
  WATER_LEVEL: '수위센서',
}
export const PM_LEVEL: Record<string, { cls: string; label: string }> = {
  DANGER: { cls: 'd', label: '위험' },
  CAUTION: { cls: 'w', label: '주의' },
  SAFE: { cls: 's', label: '안전' },
}
export const PM_TRIGGER: Record<string, string> = {
  DANGER_ENTRY: '위험 진입',
  SURGE: '급상승',
  NO_DATA: '데이터 미수신',
}
export const PM_STATUS: Record<string, string> = {
  CHECK_NEEDED: '확인 필요',
  IN_PROGRESS: '점검 중',
  RESOLVED: '조치 완료',
  ETC: '기타 종료',
}
export const levelOf = (level?: string | null) => PM_LEVEL[level || ''] || { cls: 'n', label: '-' }
export const hhmm = (value?: string | null) => String(value || '').replace('T', ' ').slice(5, 16)

/** pm-update / 예지보전 알림이 오면(0.6초 모아서) reload 를 부른다 */
export function usePmLive(reload: () => void) {
  let timer: ReturnType<typeof setTimeout> | undefined
  useBackendEvents(({ name, data }) => {
    if (name === 'pm-update' || (name === 'notification-new' && data?.category === 'EQUIPMENT') || name === 'connected') {
      clearTimeout(timer)
      timer = setTimeout(reload, 600)
    }
  })
  onBeforeUnmount(() => clearTimeout(timer))
}
