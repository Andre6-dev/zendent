import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/peripherals')({
  component: () => (
    <PlaceholderPage
      title="Peripherals"
      description="Equipment, maintenance and room/dentist assignment."
    />
  ),
})
