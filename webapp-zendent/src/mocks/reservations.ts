import { faker } from '@faker-js/faker'
import { dentists } from './dentists'
import type {
  Appointment,
  DaySchedule,
  TimeBlock,
} from '#/features/reservations/types'

function at(date: Date, hour: number, minute: number): string {
  const d = new Date(date)
  d.setHours(hour, minute, 0, 0)
  return d.toISOString()
}

interface ApptTemplate {
  dentist: string
  startHour: number
  startMin: number
  durationMin: number
  status: Appointment['status']
  treatmentLabel: string
  /** Force a specific patient name (else faker provides one). */
  patientName?: string
}

const templates: Array<ApptTemplate> = [
  { dentist: 'd1', startHour: 9, startMin: 0, durationMin: 60, status: 'finished', treatmentLabel: 'General Checkup' },
  { dentist: 'd1', startHour: 10, startMin: 0, durationMin: 60, status: 'finished', treatmentLabel: 'Scaling' },
  { dentist: 'd1', startHour: 12, startMin: 0, durationMin: 60, status: 'encounter', treatmentLabel: 'Extraction' },
  { dentist: 'd1', startHour: 14, startMin: 30, durationMin: 60, status: 'registered', treatmentLabel: 'General Checkup' },
  { dentist: 'd2', startHour: 11, startMin: 0, durationMin: 60, status: 'finished', treatmentLabel: 'Bleaching' },
  { dentist: 'd2', startHour: 14, startMin: 0, durationMin: 60, status: 'waiting_payment', treatmentLabel: 'Root Canal', patientName: 'Raihan Pratama' },
  { dentist: 'd4', startHour: 9, startMin: 30, durationMin: 60, status: 'registered', treatmentLabel: 'Tooth Filling' },
  { dentist: 'd4', startHour: 15, startMin: 0, durationMin: 45, status: 'finished', treatmentLabel: 'Consultation' },
]

/**
 * Deterministic demo schedule for a given day. faker is seeded so server and
 * client render identical data (no hydration drift) and the calendar is stable.
 */
export function getDaySchedule(date: Date): DaySchedule {
  faker.seed(7)

  const appointments: Array<Appointment> = templates.map((t, index) => ({
    id: `appt-${index}`,
    dentistId: t.dentist,
    patientName: t.patientName ?? faker.person.fullName(),
    treatmentLabel: t.treatmentLabel,
    start: at(date, t.startHour, t.startMin),
    end: at(date, t.startHour, t.startMin + t.durationMin),
    status: t.status,
  }))

  const blocks: Array<TimeBlock> = [
    {
      id: 'break-lunch',
      dentistId: null,
      kind: 'break',
      start: at(date, 13, 0),
      end: at(date, 14, 0),
      label: 'Break Time',
    },
    ...dentists
      .filter((d) => !d.available)
      .map<TimeBlock>((d) => ({
        id: `unavailable-${d.id}`,
        dentistId: d.id,
        kind: 'unavailable',
        start: at(date, 8, 0),
        end: at(date, 18, 0),
        label: 'Not Available',
      })),
  ]

  return { appointments, blocks }
}
