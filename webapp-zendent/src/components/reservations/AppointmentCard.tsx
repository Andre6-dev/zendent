import { Chip } from '@heroui/react'
import { formatTimeRange } from '#/features/reservations/calendar'
import { STATUS_META } from '#/features/reservations/status'
import type { Appointment } from '#/features/reservations/types'
import { cn } from '#/lib/cn'

interface AppointmentCardProps {
  appointment: Appointment
  style?: React.CSSProperties
}

export function AppointmentCard({ appointment, style }: AppointmentCardProps) {
  const meta = STATUS_META[appointment.status]
  const start = new Date(appointment.start)
  const end = new Date(appointment.end)

  if (appointment.status === 'waiting_payment') {
    return (
      <div
        style={style}
        className="absolute inset-x-1 flex items-center justify-center gap-2 rounded-lg border border-dashed border-warning/60 bg-warning/5 px-2 text-center"
      >
        <meta.icon className="size-3.5 shrink-0 text-warning" />
        <span className="truncate text-xs font-medium uppercase tracking-wide text-warning">
          Waiting payment for {appointment.patientName}
        </span>
      </div>
    )
  }

  return (
    <div
      style={style}
      className={cn(
        'absolute inset-x-1 overflow-hidden rounded-lg border p-2 shadow-surface',
        meta.card,
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <meta.icon className={cn('size-4 shrink-0', meta.accent)} />
          <span className="truncate text-sm font-semibold text-foreground">
            {appointment.patientName}
          </span>
        </div>
        <Chip color={meta.chipColor} variant="soft" size="sm" className="shrink-0">
          {meta.label}
        </Chip>
      </div>
      <p className="mt-1 text-xs tabular-nums text-muted">
        {formatTimeRange(start, end)}
      </p>
      <Chip variant="secondary" size="sm" className="mt-1.5">
        {appointment.treatmentLabel}
      </Chip>
    </div>
  )
}
