import { Outlet, createFileRoute, redirect } from '@tanstack/react-router'
import { createIsomorphicFn } from '@tanstack/react-start'
import { getRequest } from '@tanstack/react-start/server'
import { AppLayout } from '#/components/layout/AppLayout'
import { hasSession } from '#/server/session'

/**
 * Whether the request resolving this route carries a session.
 *
 * On the server that is read off the incoming request. On the client the answer
 * is `true` — not because the browser knows, but because it cannot: the session
 * cookie is HTTP-only by design (ADR 0017), so anything computed there would be
 * a guess. `createIsomorphicFn` keeps the two apart, and the server half — with
 * `getRequest` and everything it reaches — is compiled out of the browser
 * bundle rather than merely unused in it.
 *
 * The document that puts the application on screen is served only after the
 * server has said yes, and every navigation afterwards happens inside a shell
 * that already passed. A session lapsing mid-use is renewal's problem, not the
 * door's.
 */
const requestHasSession = createIsomorphicFn()
  .server(() => hasSession(getRequest()))
  .client(() => true)

export const Route = createFileRoute('/_app')({
  /**
   * The guard, and the reason it lives here rather than in the shell it
   * guards: `beforeLoad` runs while the route resolves, which on a document
   * request means on the server, before a single element is rendered. A check
   * inside the component — or an effect after paint — would ship the shell and
   * whatever the screens put in it to someone with no session, then redirect
   * them once it had already arrived.
   */
  beforeLoad: () => {
    if (!requestHasSession()) {
      throw redirect({ to: '/login' })
    }
  },
  component: AppLayoutRoute,
})

function AppLayoutRoute() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}
