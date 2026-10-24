export interface ControlInput {
  facilityId: string
  action: 'block' | 'release'
  reason: string
}
export interface MaintenanceInput {
  equipmentId: string
  at: string
  type: string
  memo: string
  result: string
}
export interface WorkflowAdapters {
  control?: (input: ControlInput) => Promise<void>
  maintenance?: (input: MaintenanceInput) => Promise<void>
  facilitySave?: (input: {
    name: string
    address: string
    lat: number
    lng: number
  }) => Promise<void>
  sign?: (input: { facilityId: string; text: string }) => Promise<void>
}
// Register the team's actual services here; no unverified URL is supplied.
export const workflows: WorkflowAdapters = {}
