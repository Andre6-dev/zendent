import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/staff')({
  component: () => (
    <PlaceholderPage
      title="Staff List"
      description="Dentists and personnel, schedules and specialties."
    />
  ),
})
