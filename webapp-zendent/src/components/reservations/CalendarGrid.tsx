import { Coffee } from 'lucide-react'
import { TimeAxis } from './TimeAxis'
import { DentistColumnBody, DentistColumnHeader } from './DentistColumn'
import { NowIndicator } from './NowIndicator'
import {
  DAY_START_HOUR,
  GRID_HEIGHT,
  HOUR_HEIGHT,
  HOUR_MARKS,
  heightForSpan,
  topForDate,
} from '#/features/reservations/calendar'
import type { DaySchedule, Dentist } from '#/features/reservations/types'

interface CalendarGridProps {
  dentists: Array<Dentist>
  schedule: DaySchedule
  selectedDate: Date
  timezoneLabel?: string
}

export function CalendarGrid({
  dentists,
  schedule,
  selectedDate,
  timezoneLabel = 'GMT +07:00',
}: CalendarGridProps) {
  const globalBreaks = schedule.blocks.filter((b) => b.dentistId === null)

  return (
    <div className="h-full overflow-hidden rounded-2xl border border-separator bg-background">
      {/* Single scroll container so the header and body scroll horizontally in
          sync on narrow viewports; the header stays pinned while scrolling down. */}
      <div className="h-full overflow-auto">
        {/* Header row */}
        <div className="sticky top-0 z-30 flex min-w-full border-b border-separator bg-background">
          <div className="flex w-16 shrink-0 items-center justify-center px-1 py-2 text-center text-[10px] font-medium leading-tight text-muted">
            {timezoneLabel}
          </div>
          <div className="flex flex-1">
            {dentists.map((dentist) => (
              <DentistColumnHeader
                key={dentist.id}
                dentist={dentist}
                appointmentCount={
                  schedule.appointments.filter(
                    (a) => a.dentistId === dentist.id,
                  ).length
                }
              />
            ))}
          </div>
        </div>

        {/* Body */}
        <div className="flex min-w-full" style={{ height: GRID_HEIGHT }}>
          <TimeAxis />
          <div className="relative flex flex-1">
            {/* Hour gridlines */}
            {HOUR_MARKS.map((hour) => (
              <div
                key={hour}
                style={{ top: (hour - DAY_START_HOUR) * HOUR_HEIGHT }}
                className="pointer-events-none absolute inset-x-0 border-t border-separator/60"
              />
            ))}

            {/* Dentist columns */}
            {dentists.map((dentist) => (
              <DentistColumnBody
                key={dentist.id}
                appointments={schedule.appointments.filter(
                  (a) => a.dentistId === dentist.id,
                )}
                unavailable={schedule.blocks.find(
                  (b) => b.dentistId === dentist.id && b.kind === 'unavailable',
                )}
              />
            ))}

            {/* Clinic-wide break bands */}
            {globalBreaks.map((block) => (
              <div
                key={block.id}
                style={{
                  top: topForDate(new Date(block.start)),
                  height: heightForSpan(
                    new Date(block.start),
                    new Date(block.end),
                  ),
                }}
                className="absolute inset-x-0 z-20 flex items-center justify-center gap-2 border-y border-separator bg-surface-secondary/80"
              >
                <Coffee className="size-4 text-muted" />
                <span className="text-xs font-medium uppercase tracking-wide text-muted">
                  {block.label}
                </span>
              </div>
            ))}

            <NowIndicator selectedDate={selectedDate} />
          </div>
        </div>
      </div>
    </div>
  )
}
