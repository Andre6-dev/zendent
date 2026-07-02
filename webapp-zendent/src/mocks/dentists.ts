import type { Dentist } from '#/features/reservations/types'

/** Static demo roster (replaced by the `staff` module API in Phase 3). */
export const dentists: Array<Dentist> = [
  { id: 'd1', title: 'Drg', fullName: 'Soap Mactavish', available: true },
  { id: 'd2', title: 'Drg', fullName: "Jerald O'Hara", available: true },
  { id: 'd3', title: 'Drg', fullName: 'Putri Larasati', available: false },
  { id: 'd4', title: 'Drg', fullName: 'Nadia Pramudita', available: true },
]

export function getDentists(): Array<Dentist> {
  return dentists
}
