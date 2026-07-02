import { queryOptions } from '@tanstack/react-query'
import { getDentists } from '#/mocks/dentists'
import { getDaySchedule } from '#/mocks/reservations'

// Phase 1: query functions return mock data. In Phase 3 only the queryFn bodies
// are swapped for the HTTP client — components and query keys stay the same.

export function dentistsQueryOptions() {
  return queryOptions({
    queryKey: ['dentists'] as const,
    queryFn: () => getDentists(),
  })
}

export function dayScheduleQueryOptions(dayKey: string) {
  return queryOptions({
    queryKey: ['reservations', dayKey] as const,
    queryFn: () => getDaySchedule(new Date(`${dayKey}T00:00:00`)),
  })
}
