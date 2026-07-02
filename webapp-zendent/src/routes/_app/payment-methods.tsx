import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/payment-methods')({
  component: () => (
    <PlaceholderPage
      title="Payment Method"
      description="Configure the payment methods the clinic accepts."
    />
  ),
})
