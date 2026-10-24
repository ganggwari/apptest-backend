export function mapMarkerSvg(
  color: string,
  fill = 0.94,
  count?: number,
  selected = false,
  assigned = false,
  dashed = true,
) {
  const stroke = selected ? '#155fc0' : assigned ? '#344b61' : '#526175'
  return `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44" aria-hidden="true"><style>@keyframes selection-breathe{0%,100%{stroke-opacity:.15;stroke-width:2}50%{stroke-opacity:.5;stroke-width:4}}.selection-halo{animation:selection-breathe 2.8s ease-in-out infinite}@media(prefers-reduced-motion:reduce){.selection-halo{animation:none}}</style>${selected ? '<circle cx="22" cy="22" r="19.5" fill="none" stroke="white" stroke-width="5"/><circle class="selection-halo" cx="22" cy="22" r="20" fill="none" stroke="#155fc0" stroke-width="3" stroke-opacity=".3"/>' : ''}<circle cx="22" cy="22" r="16" fill="${color}" fill-opacity="${fill.toFixed(3)}"/><circle cx="22" cy="22" r="${selected ? 17.5 : 19}" fill="none" stroke="${stroke}" stroke-width="${selected ? 2 : 1.8}" ${dashed && !selected ? 'stroke-dasharray="3 3"' : ''}/>${count === undefined ? '' : `<text x="22" y="22" dy=".35em" text-anchor="middle" fill="#ffffff" font-family="Arial,sans-serif" font-size="14" font-weight="700">${count}</text>`}</svg>`
}
