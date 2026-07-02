import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/purchases')({
  component: () => (
    <PlaceholderPage
      title="Purchases"
      description="Purchase orders, suppliers and goods receipt."
    />
  ),
})
