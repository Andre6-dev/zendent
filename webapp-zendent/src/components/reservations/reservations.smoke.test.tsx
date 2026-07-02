// @vitest-environment jsdom
import { expect, test, vi } from 'vitest'
import { render } from '@testing-library/react'
import { CalendarGrid } from './CalendarGrid'
import { CalendarToolbar } from './CalendarToolbar'
import { getDentists } from '#/mocks/dentists'
import { getDaySchedule } from '#/mocks/reservations'

vi.stubGlobal(
  'matchMedia',
  vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
)

const date = new Date('2026-06-23T10:00:00')

test('CalendarGrid renders dentists, appointments and blocks', () => {
  const dentists = getDentists()
  const schedule = getDaySchedule(date)
  const { getByText, getAllByText } = render(
    <CalendarGrid dentists={dentists} schedule={schedule} selectedDate={date} />,
  )
  expect(getByText('Drg Soap Mactavish')).toBeTruthy()
  expect(getAllByText('Finished').length).toBeGreaterThan(0)
  expect(getByText('Break Time')).toBeTruthy()
  expect(getByText('Not Available')).toBeTruthy()
})

test('CalendarToolbar renders without throwing (ToggleButtonGroup, Dropdown)', () => {
  const dentists = getDentists()
  const { getByText } = render(
    <CalendarToolbar
      totalAppointments={8}
      selectedDate={date}
      onToday={() => {}}
      onPrev={() => {}}
      onNext={() => {}}
      view="day"
      onViewChange={() => {}}
      dentists={dentists}
      dentistFilter="all"
      onDentistFilterChange={() => {}}
    />,
  )
  expect(getByText('total appointments')).toBeTruthy()
  expect(getByText('All Dentist')).toBeTruthy()
  expect(getByText('Day')).toBeTruthy()
})
