import { redirect } from '@tanstack/react-router'
import { setResponseHeader } from '@tanstack/react-start/server'
import { callApi } from './api'
import type { ApiCall } from './api'
import { logRefusal } from './log'
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  apiSessionSchema,
  clearedSessionCookies,
  isSecureRequest,
  readCookie,
  sessionCookies,
} from './session'
import type { ApiSession } from './session'

/**
 * Calls made on behalf of the person using the application, and the renewal
 * that keeps them working.
 *
 * The access token is short-lived by design, so it expires under people who
 * have done nothing wrong — mid-shift, mid-task. What that costs them is
 * decided here: either they never learn it happened, or they are returned to
 * the sign-in screen. What it must never cost them is an unexplained error in
 * the middle of their work.
 *
 * The browser is not involved in any of it and still never receives a token.
 */

/**
 * The renewal in flight for a given browser request, if one is.
 *
 * Keyed by the request itself, so it lives exactly as long as the request does
 * and needs no clearing. Two loaders on one server-rendered document both reach
 * renewal, and the exchange spends the refresh token — so the second exchange
 * would be refused as already-spent and would throw out a person whose session
 * was healthy. They share one exchange instead. A rejected renewal stays here
 * on purpose: if the session could not be renewed for the first caller, it
 * cannot for the second either.
 */
const renewals = new WeakMap<Request, Promise<ApiSession>>()

export function renewOnce(from: Request): Promise<ApiSession> {
  const inFlight = renewals.get(from)
  if (inFlight !== undefined) {
    return inFlight
  }

  const started = renewSession(from)
  renewals.set(from, started)
  return started
}

/**
 * Makes a call carrying the session, renewing it once if the API says the
 * access token has expired.
 *
 * Throws a redirect to sign-in when the session cannot be renewed. That is a
 * `Response`, so it travels out of a route the same way any other refusal
 * does, carrying the cookies that empty the session with it.
 */
export async function callApiWithSession(call: ApiCall): Promise<Response> {
  const answer = await callApi({
    ...call,
    bearer: readCookie(call.from, ACCESS_COOKIE),
  })
  // Any 401, not only an expiry. The API answers 401 for a token that is
  // missing, malformed, expired, or untrusted and does not say which, so
  // expiry cannot be singled out from here. Renewing on the others costs one
  // exchange that fails and sends the person to sign-in — which is where a
  // token the API will not accept was going to land them anyway.
  if (answer.status !== 401) {
    return answer
  }

  const renewed = await renewOnce(call.from)

  // The retry goes back to `callApi`, deliberately not through this function:
  // one retry, and it cannot loop because there is no path here that reaches
  // renewal a second time. A counter would have to be trusted; this cannot be
  // got wrong.
  const retried = await callApi({ ...call, bearer: renewed.accessToken })
  if (retried.status === 401) {
    // A token minted seconds ago and still refused is not a session that more
    // renewing will fix. Handing the caller a bare 401 would surface exactly
    // the unexplained error in the middle of someone's work that this whole
    // path exists to prevent.
    endSession(call.from, 'the API refused a freshly renewed token')
  }
  return retried
}

/**
 * Spends the refresh token for a new session and puts it in the browser.
 *
 * The exchange rotates: the API spends the token presented and answers with
 * its successor, so both cookies are replaced. Keeping the old refresh token
 * would leave a spent credential in the browser and the next renewal would
 * fail on a session that was perfectly healthy.
 */
async function renewSession(from: Request): Promise<ApiSession> {
  const refreshToken = readCookie(from, REFRESH_COOKIE)
  if (refreshToken === undefined || refreshToken.length === 0) {
    // Nothing to exchange. Asking the API anyway would only be a slower way of
    // arriving at the same place.
    endSession(from, 'the request carried no refresh token')
  }

  const answer = await callApi({
    path: '/auth/refresh',
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    from,
  })
  if (!answer.ok) {
    // Unknown, expired, or already spent — the API does not distinguish, and
    // neither does what happens next.
    endSession(from, `the API refused the exchange (${answer.status})`)
  }

  const renewed = apiSessionSchema.safeParse(await answer.json())
  if (!renewed.success) {
    endSession(from, 'the exchange answered in a way we did not expect')
  }

  setResponseHeader(
    'set-cookie',
    sessionCookies(renewed.data, isSecureRequest(from)),
  )
  return renewed.data
}

/**
 * Ends the session: empties the cookies and sends the person to sign-in.
 *
 * Both halves matter. Clearing without redirecting leaves someone inside a
 * shell that can no longer answer them; redirecting without clearing sends
 * them to a sign-in screen while the browser still holds a dead session that
 * the next request would present all over again.
 */
function endSession(from: Request, why: string): never {
  logRefusal('session ended', why)
  setResponseHeader('set-cookie', clearedSessionCookies(isSecureRequest(from)))

  // `href` alongside `to`, so the refusal is a complete response on its own.
  // `to` is the router's to resolve, and it only resolves inside a route — a
  // proxied call made from a plain server handler would otherwise send a 307
  // naming nowhere to go.
  throw redirect({ to: '/login', href: '/login' })
}
