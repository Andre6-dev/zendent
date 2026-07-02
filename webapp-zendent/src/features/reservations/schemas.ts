import { z } from 'zod'

export const appointmentStatusSchema = z.enum([
  'registered',
  'encounter',
  'finished',
  'waiting_payment',
])

export const dentistSchema = z.object({
  id: z.string(),
  title: z.string(),
  fullName: z.string(),
  avatarUrl: z.string().url().optional(),
  available: z.boolean(),
})

export const appointmentSchema = z.object({
  id: z.string(),
  dentistId: z.string(),
  patientName: z.string(),
  treatmentLabel: z.string(),
  start: z.string().datetime(),
  end: z.string().datetime(),
  status: appointmentStatusSchema,
})

export const timeBlockSchema = z.object({
  id: z.string(),
  dentistId: z.string().nullable(),
  kind: z.enum(['break', 'unavailable']),
  start: z.string().datetime(),
  end: z.string().datetime(),
  label: z.string(),
})

export const dayScheduleSchema = z.object({
  appointments: z.array(appointmentSchema),
  blocks: z.array(timeBlockSchema),
})
