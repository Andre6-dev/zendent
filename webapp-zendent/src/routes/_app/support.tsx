import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/support')({
  component: () => (
    <PlaceholderPage
      title="Customer Support"
      description="Support tickets, FAQ and contact."
    />
  ),
})
