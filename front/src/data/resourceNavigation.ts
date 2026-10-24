const groups = [
  ['ocr', 'reviews'],
  ['customers', 'inquiries'],
  ['notices', 'faqs'],
  ['managers', 'shifts'],
  ['display', 'presets', 'reasons'],
]
export function resourceTabs(id: string) {
  return groups.find((group) => group.includes(id)) || []
}
export const resourceShortcuts: Record<string, { id: string; label: string }[]> = {
  ocr: [{ id: 'transits', label: '차량 통과 기록' }],
  reviews: [{ id: 'transits', label: '차량 통과 기록' }],
  transits: [
    { id: 'ocr', label: 'OCR 인식 기록' },
    { id: 'events', label: '시설 제어 이력' },
  ],
  events: [
    { id: 'transits', label: '차량 통과 기록' },
    { id: 'transfers', label: '경찰·소방 이관 문서' },
  ],
  transfers: [{ id: 'events', label: '시설 제어 이력' }],
  cameras: [{ id: 'facilities', label: '지하차도 목록' }],
  thresholds: [{ id: 'facilities', label: '지하차도 목록' }],
  'risk-history': [{ id: 'thresholds', label: '위험도 임계치 설정' }],
  regions: [{ id: 'managers', label: '관리자·근무조' }],
  alerts: [
    { id: 'manager-notifications', label: '내 관리자 알림' },
    { id: 'faqs', label: 'FAQ' },
  ],
  'manager-notifications': [{ id: 'alerts', label: '알림 발송 기록' }],
}
