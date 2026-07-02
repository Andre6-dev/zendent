import { Avatar, Button } from '@heroui/react'
import { MoreHorizontal } from 'lucide-react'
import { AppointmentCard } from './AppointmentCard'
import { heightForSpan, topForDate } from '#/features/reservations/calendar'
import type {
  Appointment,
  Dentist,
  TimeBlock,
} from '#/features/reservations/types'

function initials(fullName: string): string {
  return fullName
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
}

export function DentistColumnHeader({
  dentist,
  appointmentCount,
}: {
  dentist: Dentist
  appointmentCount: number
}) {
  return (
    <div className="flex min-w-[200px] flex-1 items-center gap-2.5 border-l border-separator px-3 py-2.5">
      <Avatar size="sm">
        <Avatar.Fallback>{initials(dentist.fullName)}</Avatar.Fallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-foreground">
          {dentist.title} {dentist.fullName}
        </p>
        <p className="truncate text-xs text-muted">
          Today's appointment: {appointmentCount} patient(s)
        </p>
      </div>
      <Button isIconOnly variant="ghost" size="sm" aria-label="Dentist options">
        <MoreHorizontal className="size-4" />
      </Button>
    </div>
  )
}

export function DentistColumnBody({
  appointments,
  unavailable,
}: {
  appointments: Array<Appointment>
  unavailable?: TimeBlock
}) {
  return (
    <div className="relative min-w-[200px] flex-1 border-l border-separator">
      {unavailable && (
        <div
          style={{
            top: topForDate(new Date(unavailable.start)),
            height: heightForSpan(
              new Date(unavailable.start),
              new Date(unavailable.end),
            ),
          }}
          className="absolute inset-x-0 flex items-center justify-center bg-[repeating-linear-gradient(45deg,var(--color-surface-secondary)_0,var(--color-surface-secondary)_10px,transparent_10px,transparent_20px)]"
        >
          <span className="text-xs font-medium uppercase tracking-wide text-muted">
            {unavailable.label}
          </span>
        </div>
      )}

      {appointments.map((appointment) => {
        const start = new Date(appointment.start)
        const end = new Date(appointment.end)
        return (
          <AppointmentCard
            key={appointment.id}
            appointment={appointment}
            style={{ top: topForDate(start), height: heightForSpan(start, end) }}
          />
        )
      })}
    </div>
  )
}
