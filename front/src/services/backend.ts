export class BackendError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
  }
}
export interface PageData<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
export function queryString(values: Record<string, unknown>) {
  const p = new URLSearchParams()
  Object.entries(values).forEach(([k, v]) => {
    if (v !== '' && v != null) p.set(k, String(v))
  })
  return p.size ? '?' + p : ''
}
export async function backend<T = any>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal; pdf?: boolean } = {},
): Promise<T> {
  const controller = new AbortController(),
    timeout = setTimeout(() => controller.abort(), 20000)
  const abort = () => controller.abort()
  options.signal?.addEventListener('abort', abort, { once: true })
  if (options.signal?.aborted) controller.abort()
  try {
    const r = await fetch(path, {
      method: options.method || 'GET',
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal,
      headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
    if (options.pdf && r.ok) {
      const blob = await r.blob()
      if (!r.headers.get('content-type')?.includes('application/pdf'))
        throw new BackendError('PDF 문서 응답을 확인하지 못했습니다.', r.status)
      return blob as T
    }
    const data = await r.json().catch(() => null)
    if (!r.ok || data?.success === false)
      throw new BackendError(
        data?.message ||
          (r.status === 401
            ? '로그인이 필요합니다.'
            : r.status === 403
              ? '이 작업에 대한 권한이 없습니다.'
              : '요청을 처리하지 못했습니다.'),
        r.status,
      )
    if (!data || !Object.hasOwn(data, 'data'))
      throw new BackendError('서버 응답 형식이 올바르지 않습니다.', r.status)
    return data.data as T
  } catch (e) {
    if (e instanceof BackendError) throw e
    if (options.signal?.aborted) throw e
    throw new BackendError(
      controller.signal.aborted
        ? '응답 시간이 초과되었습니다. 다시 시도해 주세요.'
        : '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      0,
    )
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abort)
  }
}
export function asPage<T>(data: T[] | PageData<T>, page = 1, size = 20): PageData<T> {
  if (Array.isArray(data))
    return { items: data, page: 1, size: data.length, totalItems: data.length, totalPages: 1 }
  if (!data || !Array.isArray(data.items)) throw new Error('목록 응답 형식이 올바르지 않습니다.')
  return { ...data, page: data.page || page, size: data.size || size }
}
export function downloadPdf(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob),
    a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 30000)
}
