export const seoulTunnelSource = 'https://seoultunnel.co.kr/usage/traffic.do'

export type TunnelCamera = { id: string }
export type TunnelCameraStream = {
  id: string
  name: string
  direction: string
  streamUrl: string
}

// The public traffic page uses these same read-only POST endpoints.
async function publicTunnelData(path: string, values: Record<string, string>, signal: AbortSignal) {
  const response = await fetch('/tunnel-source/' + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body: new URLSearchParams(values),
    credentials: 'omit',
    signal,
  })
  if (!response.ok) throw new Error('신월여의지하도로 CCTV 제공처에 연결하지 못했습니다.')
  return response.json()
}

export async function getTunnelCameras(signal: AbortSignal): Promise<TunnelCamera[]> {
  const data = await publicTunnelData('getCctv-data.do', { lvl: '1' }, signal)
  if (!Array.isArray(data?.list)) throw new Error('공개 CCTV 목록을 확인하지 못했습니다.')
  const ids = new Set<string>()
  return data.list.flatMap((row: Record<string, unknown>) => {
    const id = String(row.CCTV_ID || '')
    if (!id || ids.has(id)) return []
    ids.add(id)
    return [{ id }]
  })
}

export async function getTunnelCameraStream(id: string, signal: AbortSignal): Promise<TunnelCameraStream> {
  const data = await publicTunnelData('getCctvInfo-data.do', { cctvId: id }, signal)
  const info = data?.info
  if (!info || String(info.CCTV_ID) !== id || typeof info.STREAMING2_ADRES !== 'string')
    throw new Error('선택한 CCTV 영상 정보를 확인하지 못했습니다.')
  const upstream = new URL(info.STREAMING2_ADRES)
  if (!['http:', 'https:'].includes(upstream.protocol) || upstream.username || upstream.password)
    throw new Error('지원하지 않는 CCTV 영상 주소입니다.')
  // Match the official map player: its legacy streaming host is exposed through
  // the operator's HTTPS origin, which also permits cross-origin HLS playback.
  if (upstream.origin === 'http://211.61.148.68:1935') {
    upstream.protocol = 'https:'
    upstream.hostname = 'www.seoultunnel.co.kr'
    upstream.port = ''
  }
  if (upstream.protocol !== 'https:' || !['seoultunnel.co.kr', 'www.seoultunnel.co.kr'].includes(upstream.hostname))
    throw new Error('공식 CCTV 영상 주소를 확인하지 못했습니다. 공식 사이트에서 확인해 주세요.')
  return {
    id,
    name: String(info.CCTV_NM || '공개 CCTV'),
    direction: String(info.DRC || ''),
    streamUrl: upstream.href,
  }
}
