import { Outlet, createFileRoute } from '@tanstack/react-router'
import { AppLayout } from '#/components/layout/AppLayout'

export const Route = createFileRoute('/_app')({
  component: AppLayoutRoute,
})

function AppLayoutRoute() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}
