// Values are taken from the supplied v4 schema and backend enums.
export const noticeCategories: [string, string][] = [
  ['INFO', '안내'],
  ['MAINTENANCE', '점검'],
  ['UPDATE', '업데이트'],
  ['URGENT', '긴급'],
]
export const faqCategories: [string, string][] = [
  ['USAGE', '이용 방법'],
  ['NOTIFICATION', '알림·푸시'],
  ['VEHICLE', '차량·번호판'],
  ['ACCOUNT', '계정'],
  ['ETC', '기타'],
]
export function riskGroup(value: unknown) {
  const code = String(value || '').toUpperCase()
  if (['DANGER', 'ALERT', 'SEVERE', '위험', '경계', '심각'].includes(code)) return 'DANGER'
  if (['WARNING', 'WATCH', 'CAUTION', '경고', '관심', '주의'].includes(code)) return 'WARNING'
  if (['SAFE', '안전', '정상'].includes(code)) return 'SAFE'
  return ''
}
export function matchesRisk(value: unknown, filter: string) {
  return !filter || riskGroup(value) === filter
}
export function riskSummaryLabel(value: unknown, controlled = false) {
  if (controlled) return '통제'
  const group = riskGroup(value)
  return group === 'DANGER'
    ? '위험'
    : group === 'WARNING'
      ? '주의'
      : group === 'SAFE'
        ? '안전'
        : String(value || '미확인')
}
export function boardCategoryLabel(value: unknown) {
  const code = String(value || '')
  return [...noticeCategories, ...faqCategories].find(([key]) => key === code)?.[1] || code
}
