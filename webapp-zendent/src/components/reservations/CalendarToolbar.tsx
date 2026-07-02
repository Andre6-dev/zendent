import {
  Button,
  Dropdown,
  ToggleButton,
  ToggleButtonGroup,
} from '@heroui/react'
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ListFilter,
  UserRound,
} from 'lucide-react'
import { formatDateLabel } from '#/features/reservations/calendar'
import type {
  CalendarView,
  Dentist,
  DentistFilter,
} from '#/features/reservations/types'

interface CalendarToolbarProps {
  totalAppointments: number
  selectedDate: Date
  onToday: () => void
  onPrev: () => void
  onNext: () => void
  view: CalendarView
  onViewChange: (view: CalendarView) => void
  dentists: Array<Dentist>
  dentistFilter: DentistFilter
  onDentistFilterChange: (filter: DentistFilter) => void
}

export function CalendarToolbar({
  totalAppointments,
  selectedDate,
  onToday,
  onPrev,
  onNext,
  view,
  onViewChange,
  dentists,
  dentistFilter,
  onDentistFilterChange,
}: CalendarToolbarProps) {
  const activeDentist =
    dentistFilter === 'all'
      ? null
      : dentists.find((d) => d.id === dentistFilter)
  const dentistLabel = activeDentist
    ? `${activeDentist.title} ${activeDentist.fullName}`
    : 'All Dentist'

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-2">
        <span className="flex size-9 items-center justify-center rounded-lg bg-surface-secondary text-muted">
          <CalendarDays className="size-5" />
        </span>
        <p className="text-sm text-muted">
          <span className="font-semibold text-foreground">
            {totalAppointments}
          </span>{' '}
          total appointments
        </p>
      </div>

      <div className="ml-auto flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1">
          <Button variant="outline" size="sm" onPress={onToday}>
            Today
          </Button>
          <Button
            isIconOnly
            variant="ghost"
            size="sm"
            aria-label="Previous day"
            onPress={onPrev}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button
            isIconOnly
            variant="ghost"
            size="sm"
            aria-label="Next day"
            onPress={onNext}
          >
            <ChevronRight className="size-4" />
          </Button>
          <span className="ml-1 text-sm font-medium tabular-nums text-foreground">
            {formatDateLabel(selectedDate)}
          </span>
        </div>

        <ToggleButtonGroup
          isDetached
          size="sm"
          selectionMode="single"
          disallowEmptySelection
          selectedKeys={new Set([view])}
          onSelectionChange={(keys) => {
            const next = [...keys][0]
            if (next) onViewChange(next as CalendarView)
          }}
        >
          <ToggleButton id="day">Day</ToggleButton>
          <ToggleButton id="week">Week</ToggleButton>
        </ToggleButtonGroup>

        <Dropdown>
          <Dropdown.Trigger>
            <Button variant="outline" size="sm" className="gap-2">
              <UserRound className="size-4" />
              {dentistLabel}
            </Button>
          </Dropdown.Trigger>
          <Dropdown.Popover placement="bottom end" className="min-w-56">
            <Dropdown.Menu
              onAction={(key) => onDentistFilterChange(String(key))}
            >
              <Dropdown.Item key="all" id="all">
                All Dentist
              </Dropdown.Item>
              {dentists.map((dentist) => (
                <Dropdown.Item
                  key={dentist.id}
                  id={dentist.id}
                  textValue={dentist.fullName}
                >
                  {dentist.title} {dentist.fullName}
                </Dropdown.Item>
              ))}
            </Dropdown.Menu>
          </Dropdown.Popover>
        </Dropdown>

        <Button variant="outline" size="sm" className="gap-2">
          <ListFilter className="size-4" />
          Filters
        </Button>
      </div>
    </div>
  )
}
