export type AppointmentStatus =
  | 'registered'
  | 'encounter'
  | 'finished'
  | 'waiting_payment'

export interface Dentist {
  id: string
  /** Honorific shown before the name, e.g. "Drg". */
  title: string
  fullName: string
  avatarUrl?: string
  available: boolean
}

export interface Appointment {
  id: string
  dentistId: string
  patientName: string
  treatmentLabel: string
  /** ISO datetime. */
  start: string
  /** ISO datetime. */
  end: string
  status: AppointmentStatus
}

export type TimeBlockKind = 'break' | 'unavailable'

export interface TimeBlock {
  id: string
  /** `null` spans every dentist column (e.g. a clinic-wide break). */
  dentistId: string | null
  kind: TimeBlockKind
  start: string
  end: string
  label: string
}

export interface DaySchedule {
  appointments: Array<Appointment>
  blocks: Array<TimeBlock>
}

export type CalendarView = 'day' | 'week'

/** `'all'` or a specific dentist id. */
export type DentistFilter = 'all' | string
