import { useMemo, useState } from 'react'
import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Spinner } from '@heroui/react'
import { CalendarRange } from 'lucide-react'
import { CalendarToolbar } from '#/components/reservations/CalendarToolbar'
import { CalendarGrid } from '#/components/reservations/CalendarGrid'
import {
  dayScheduleQueryOptions,
  dentistsQueryOptions,
} from '#/features/reservations/queries'
import { addDays, dayKey } from '#/features/reservations/calendar'
import type {
  CalendarView,
  DentistFilter,
} from '#/features/reservations/types'
import { cn } from '#/lib/cn'

export const Route = createFileRoute('/_app/reservations')({
  component: ReservationsPage,
})

type PageTab = 'calendar' | 'history'

function ReservationsPage() {
  const [tab, setTab] = useState<PageTab>('calendar')
  const [selectedDate, setSelectedDate] = useState(() => new Date())
  const [view, setView] = useState<CalendarView>('day')
  const [dentistFilter, setDentistFilter] = useState<DentistFilter>('all')

  const dentistsQuery = useQuery(dentistsQueryOptions())
  const scheduleQuery = useQuery(dayScheduleQueryOptions(dayKey(selectedDate)))

  const dentists = dentistsQuery.data
  const schedule = scheduleQuery.data

  const displayedDentists = useMemo(() => {
    if (!dentists) return []
    return dentistFilter === 'all'
      ? dentists
      : dentists.filter((d) => d.id === dentistFilter)
  }, [dentists, dentistFilter])

  const totalAppointments = useMemo(() => {
    if (!schedule) return 0
    const ids = new Set(displayedDentists.map((d) => d.id))
    return schedule.appointments.filter((a) => ids.has(a.dentistId)).length
  }, [schedule, displayedDentists])

  return (
    <div className="flex h-full flex-col px-6 pt-6">
      <h1 className="text-2xl font-semibold tracking-tight text-foreground">
        Reservations
      </h1>

      <div
        role="tablist"
        aria-label="Reservations views"
        className="mt-4 flex gap-6 border-b border-separator"
      >
        <TabButton active={tab === 'calendar'} onPress={() => setTab('calendar')}>
          Calendar
        </TabButton>
        <TabButton active={tab === 'history'} onPress={() => setTab('history')}>
          Log History
        </TabButton>
      </div>

      {tab === 'history' ? (
        <EmptyArea
          title="Log History"
          message="Appointment activity log will appear here."
        />
      ) : !dentists || !schedule ? (
        <div className="flex flex-1 items-center justify-center">
          <Spinner />
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-4 py-4">
          <CalendarToolbar
            totalAppointments={totalAppointments}
            selectedDate={selectedDate}
            onToday={() => setSelectedDate(new Date())}
            onPrev={() => setSelectedDate((d) => addDays(d, -1))}
            onNext={() => setSelectedDate((d) => addDays(d, 1))}
            view={view}
            onViewChange={setView}
            dentists={dentists}
            dentistFilter={dentistFilter}
            onDentistFilterChange={setDentistFilter}
          />

          {view === 'week' ? (
            <EmptyArea
              title="Week view"
              message="The weekly resource view is coming soon. Use Day for now."
            />
          ) : (
            <div className="min-h-0 flex-1">
              <CalendarGrid
                dentists={displayedDentists}
                schedule={schedule}
                selectedDate={selectedDate}
              />
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function TabButton({
  active,
  onPress,
  children,
}: {
  active: boolean
  onPress: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onPress}
      className={cn(
        '-mb-px cursor-[var(--cursor-interactive)] border-b-2 px-1 pb-3 text-sm font-medium',
        active
          ? 'border-accent text-foreground'
          : 'border-transparent text-muted hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}

function EmptyArea({ title, message }: { title: string; message: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-surface-secondary text-muted">
        <CalendarRange className="size-6" />
      </span>
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <p className="max-w-xs text-sm text-muted">{message}</p>
    </div>
  )
}
