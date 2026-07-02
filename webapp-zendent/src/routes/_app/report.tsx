import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/report')({
  component: () => (
    <PlaceholderPage
      title="Report"
      description="Financial, treatment and schedule-occupancy reports."
    />
  ),
})
