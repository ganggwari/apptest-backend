/** Leave ordinary wheel gestures to the page; zoom only with Ctrl or Command. */
export function pageFriendlyMapScroll(host: HTMLElement, map: any, touchZoom = false) {
  const previousTouchAction = host.style.touchAction
  const previousTitle = host.title
  // 앱에서는 SDK의 핀치 확대를 허용하고 브라우저 화면 확대와 분리합니다.
  map.setZoomable(touchZoom)
  if (touchZoom) host.style.touchAction = 'none'
  host.title = touchZoom
    ? '두 손가락으로 지도 확대·축소'
    : '지도 확대·축소: Ctrl(⌘) + 스크롤 또는 + / − 버튼'
  const wheel = (event: WheelEvent) => {
    event.stopImmediatePropagation()
    if (!event.ctrlKey && !event.metaKey) return
    event.preventDefault()
    if (event.deltaY)
      map.setLevel(Math.max(1, Math.min(14, map.getLevel() + (event.deltaY > 0 ? 1 : -1))))
  }
  host.addEventListener('wheel', wheel, { capture: true, passive: false })
  return () => {
    host.removeEventListener('wheel', wheel, true)
    host.style.touchAction = previousTouchAction
    host.title = previousTitle
  }
}
