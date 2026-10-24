export const busanDistricts = [
  '중구',
  '서구',
  '동구',
  '영도구',
  '부산진구',
  '동래구',
  '남구',
  '북구',
  '해운대구',
  '사하구',
  '금정구',
  '강서구',
  '연제구',
  '수영구',
  '사상구',
  '기장군',
] as const
export function busanDistrict(...values: unknown[]): string {
  for (const value of values) {
    if (typeof value !== 'string') continue
    const name = value.trim().replace(/^부산(?:광역)?시\s*/, '')
    const found = busanDistricts.find(
      (district) => name === district || name.startsWith(district + ' '),
    )
    if (found) return found
  }
  return ''
}
