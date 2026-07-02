import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/accounts')({
  component: () => (
    <PlaceholderPage
      title="Accounts"
      description="Financial status for the clinic and per dentist."
    />
  ),
})
