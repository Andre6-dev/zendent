import {
  HOUR_HEIGHT,
  HOUR_MARKS,
  formatHourMark,
} from '#/features/reservations/calendar'

/** Left rail with the hour labels, aligned to the grid rows. */
export function TimeAxis() {
  return (
    <div className="w-16 shrink-0">
      {HOUR_MARKS.map((hour) => (
        <div
          key={hour}
          style={{ height: HOUR_HEIGHT }}
          className="relative"
        >
          <span className="absolute -top-2 right-2 text-xs tabular-nums text-muted">
            {formatHourMark(hour)}
          </span>
        </div>
      ))}
    </div>
  )
}
