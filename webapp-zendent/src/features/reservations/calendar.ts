/** Visible time window and vertical scale of the day grid. */
export const DAY_START_HOUR = 8
export const DAY_END_HOUR = 18
export const HOUR_HEIGHT = 80 // px per hour

export const VISIBLE_HOURS = DAY_END_HOUR - DAY_START_HOUR
export const GRID_HEIGHT = VISIBLE_HOURS * HOUR_HEIGHT

/** Hour labels rendered on the time axis (start of each row). */
export const HOUR_MARKS = Array.from(
  { length: VISIBLE_HOURS },
  (_, i) => DAY_START_HOUR + i,
)

/** Minutes elapsed since the start of the visible window for a given date. */
export function minutesFromDayStart(date: Date): number {
  return (date.getHours() - DAY_START_HOUR) * 60 + date.getMinutes()
}

/** Top offset (px) for a datetime within the grid. */
export function topForDate(date: Date): number {
  return (minutesFromDayStart(date) / 60) * HOUR_HEIGHT
}

/** Height (px) for a [start, end] span, clamped to a sensible minimum. */
export function heightForSpan(start: Date, end: Date): number {
  const minutes = (end.getTime() - start.getTime()) / 60000
  return Math.max((minutes / 60) * HOUR_HEIGHT, 28)
}

const timeFormatter = new Intl.DateTimeFormat('en-US', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

export function formatTime(date: Date): string {
  return timeFormatter.format(date)
}

export function formatTimeRange(start: Date, end: Date): string {
  return `${formatTime(start)} > ${formatTime(end)}`
}

const hourFormatter = new Intl.DateTimeFormat('en-US', {
  hour: 'numeric',
  hour12: true,
})

export function formatHourMark(hour: number): string {
  const d = new Date()
  d.setHours(hour, 0, 0, 0)
  return hourFormatter.format(d).toLowerCase().replace(' ', '')
}

const dateLabelFormatter = new Intl.DateTimeFormat('en-US', {
  weekday: 'short',
  day: '2-digit',
  month: 'long',
  year: 'numeric',
})

export function formatDateLabel(date: Date): string {
  return dateLabelFormatter.format(date)
}

/** Whether `date` falls on the same calendar day as `reference`. */
export function isSameDay(date: Date, reference: Date): boolean {
  return (
    date.getFullYear() === reference.getFullYear() &&
    date.getMonth() === reference.getMonth() &&
    date.getDate() === reference.getDate()
  )
}

/** Local-midnight ISO-ish key (YYYY-MM-DD) used for query keys. */
export function dayKey(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function addDays(date: Date, amount: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}
