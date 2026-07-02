import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/treatments')({
  component: () => (
    <PlaceholderPage
      title="Treatments"
      description="Treatment catalog and the add-treatment wizard."
    />
  ),
})
