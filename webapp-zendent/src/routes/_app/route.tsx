import { Outlet, createFileRoute } from '@tanstack/react-router'
import { createIsomorphicFn, createServerFn } from '@tanstack/react-start'
import { getRequest, setResponseHeader } from '@tanstack/react-start/server'
import { AppLayout } from '#/components/layout/AppLayout'
import { renewOnce } from '#/server/session-calls'
import { readSignedInMember } from '#/server/session-handlers'
import { hasSession } from '#/server/session'

/**
 * Lets the request resolving this route through, or turns it away.
 *
 * On the server the incoming request decides. On the client nothing is checked
 * — not because the browser is trusted, but because it cannot see: the session
 * cookie is HTTP-only by design (ADR 0017), so anything computed there would be
 * a guess. `createIsomorphicFn` keeps the two apart, and the server half — with
 * `getRequest` and everything it reaches — is compiled out of the browser
 * bundle rather than merely unused in it.
 *
 * The access cookie expires with the token it carries, so a person coming back
 * from lunch arrives without one and with a refresh cookie that is good for a
 * month. Turning them away there would be the session quietly expiring under
 * someone who did nothing wrong. So the door renews rather than evicts: one
 * exchange, and only for a request that arrives without an access token — never
 * a round trip in front of a document that already carries one.
 *
 * Renewal either succeeds, and the person notices nothing, or it sends them to
 * sign-in with the session emptied.
 */
const admit = createIsomorphicFn()
  .server(async () => {
    const request = getRequest()

    // Never stored, so the back button after a sign-out has to ask the server
    // again — and is turned away — rather than being handed the shell out of
    // the browser's own history. It is also what keeps a Clinic's screens off
    // the disk of a shared machine.
    setResponseHeader('cache-control', 'no-store, must-revalidate')

    if (hasSession(request)) {
      return
    }
    await renewOnce(request)
  })
  .client(() => {})

/**
 * Who is signed in. Read on the server, where the session is, and handed to the
 * shell as plain names — the browser is told what to put on screen and nothing
 * about the session that produced it.
 */
const signedInMember = createServerFn({ method: 'GET' }).handler(() =>
  readSignedInMember(getRequest()),
)

export const Route = createFileRoute('/_app')({
  /**
   * The guard, and the reason it lives here rather than in the shell it
   * guards: `beforeLoad` runs while the route resolves, which on a document
   * request means on the server, before a single element is rendered. A check
   * inside the component — or an effect after paint — would ship the shell and
   * whatever the screens put in it to someone with no session, then redirect
   * them once it had already arrived.
   */
  beforeLoad: async () => {
    await admit()
  },
  loader: () => signedInMember(),
  component: AppLayoutRoute,
})

function AppLayoutRoute() {
  const member = Route.useLoaderData()

  return (
    <AppLayout member={member}>
      <Outlet />
    </AppLayout>
  )
}
