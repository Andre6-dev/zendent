import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/sales')({
  component: () => (
    <PlaceholderPage
      title="Sales"
      description="Patient billing table and the Pay Bill flow."
    />
  ),
})
