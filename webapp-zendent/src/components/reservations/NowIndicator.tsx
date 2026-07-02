import { useEffect, useState } from 'react'
import {
  DAY_END_HOUR,
  DAY_START_HOUR,
  formatTime,
  isSameDay,
  topForDate,
} from '#/features/reservations/calendar'

/** Red current-time line. Client-only (depends on the real clock). */
export function NowIndicator({ selectedDate }: { selectedDate: Date }) {
  const [now, setNow] = useState<Date | null>(null)

  useEffect(() => {
    setNow(new Date())
    const id = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(id)
  }, [])

  if (!now || !isSameDay(now, selectedDate)) return null

  const hour = now.getHours()
  if (hour < DAY_START_HOUR || hour >= DAY_END_HOUR) return null

  return (
    <div
      style={{ top: topForDate(now) }}
      className="pointer-events-none absolute inset-x-0 z-30 flex items-center"
    >
      <span className="rounded bg-danger px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-danger-foreground">
        {formatTime(now)}
      </span>
      <span className="h-px flex-1 bg-danger" />
    </div>
  )
}
