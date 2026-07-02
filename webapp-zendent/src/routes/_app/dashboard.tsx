import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/dashboard')({
  component: () => (
    <PlaceholderPage
      title="Dashboard"
      description="Clinic KPIs, upcoming appointments and sales overview."
    />
  ),
})
