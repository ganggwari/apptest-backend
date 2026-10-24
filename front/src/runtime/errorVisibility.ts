// 새로 표시되거나 내용이 바뀐 오류만 안내한다. 기존 오류로 반복 이동하지 않는다.
export function installErrorVisibility(root: HTMLElement) {
  const selector = '[role="alert"], [data-error], .error, [class*="-error"], [class*="error-"]'
  const seen = new WeakMap<HTMLElement, string>()
  let frame = 0
  const visible = (element: HTMLElement) => {
    if (!element.getClientRects().length || element.closest('[hidden], [aria-hidden="true"]')) return false
    const style = getComputedStyle(element)
    return style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0'
  }
  const reveal = (element: HTMLElement) => {
    const rect = element.getBoundingClientRect()
    let clipped = rect.top < 80 || rect.bottom > window.innerHeight - 24
    for (let parent = element.parentElement; parent && !clipped; parent = parent.parentElement) {
      if (/(auto|scroll|hidden)/.test(getComputedStyle(parent).overflowY)) {
        const bounds = parent.getBoundingClientRect()
        clipped = rect.top < bounds.top + 16 || rect.bottom > bounds.bottom - 16
      }
    }
    if (clipped) element.scrollIntoView({ block: 'center', inline: 'nearest', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }
  const scan = () => {
    frame = 0
    const dialogs = [...document.querySelectorAll<HTMLDialogElement>('dialog[open]')]
    const activeDialog = dialogs[dialogs.length - 1]
    const changed: HTMLElement[] = []
    root.querySelectorAll<HTMLElement>(selector).forEach(element => {
      const text = visible(element) ? element.textContent?.trim() || '' : ''
      const previous = seen.get(element) || ''
      seen.set(element, text)
      if (text && text !== previous && (!activeDialog || activeDialog.contains(element))) changed.push(element)
    })
    const target = changed.find(element => !changed.some(other => other !== element && other.contains(element)))
    if (target) reveal(target)
  }
  const schedule = () => { if (!frame) frame = requestAnimationFrame(scan) }
  const observer = new MutationObserver(schedule)
  observer.observe(root, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['class', 'style', 'hidden', 'open', 'role', 'aria-hidden'] })
  const invalid = (event: Event) => {
    const field = event.target
    if (field instanceof HTMLElement) requestAnimationFrame(() => reveal(field))
  }
  root.addEventListener('invalid', invalid, true)
  schedule()
  return () => { observer.disconnect(); cancelAnimationFrame(frame); root.removeEventListener('invalid', invalid, true) }
}
