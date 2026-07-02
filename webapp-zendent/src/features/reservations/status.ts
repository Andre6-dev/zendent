import { CalendarClock, CheckCircle2, CircleDot, DollarSign } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { AppointmentStatus } from './types'

export type ChipColor = 'success' | 'warning' | 'accent' | 'danger'

export interface StatusMeta {
  label: string
  icon: LucideIcon
  /** Card container classes (soft tinted surface + border). */
  card: string
  /** Status dot / icon color class. */
  accent: string
  /** HeroUI Chip color for the status badge. */
  chipColor: ChipColor
}

export const STATUS_META: Record<AppointmentStatus, StatusMeta> = {
  finished: {
    label: 'Finished',
    icon: CheckCircle2,
    card: 'bg-success/10 border-success/20',
    accent: 'text-success',
    chipColor: 'success',
  },
  encounter: {
    label: 'Encounter',
    icon: CalendarClock,
    card: 'bg-warning/10 border-warning/20',
    accent: 'text-warning',
    chipColor: 'warning',
  },
  registered: {
    label: 'Registered',
    icon: CircleDot,
    card: 'bg-accent/10 border-accent/20',
    accent: 'text-accent',
    chipColor: 'accent',
  },
  waiting_payment: {
    label: 'Waiting Payment',
    icon: DollarSign,
    card: 'border-warning/50 bg-warning/5',
    accent: 'text-warning',
    chipColor: 'warning',
  },
}
