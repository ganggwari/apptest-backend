import { useApp } from '../stores/app'
export function ownsFacility(row: Record<string, any> | null | undefined) {
  const account = useApp().account
  if (account?.role === 'REGIONAL_ADMIN' && typeof row?.manageable === 'boolean')
    return row.manageable
  const region = row?.assignedRegionId ?? row?.regionId ?? row?.region?.id
  return (
    account?.role === 'REGIONAL_ADMIN' &&
    region != null &&
    !!account.regions?.some((item) => String(item.id) === String(region))
  )
}
