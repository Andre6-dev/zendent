import { createFileRoute } from '@tanstack/react-router'
import { PlaceholderPage } from '#/components/common/PlaceholderPage'

export const Route = createFileRoute('/_app/patients')({
  component: () => (
    <PlaceholderPage
      title="Patients"
      description="Patient list, medical record and odontogram."
    />
  ),
})
