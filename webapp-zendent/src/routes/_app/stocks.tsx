import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/stocks')({
  component: () => (
    <PlaceholderPage
      title="Stocks"
      description="Consumable inventory, levels and low-stock alerts."
    />
  ),
})
