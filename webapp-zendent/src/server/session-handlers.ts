import { z } from 'zod'
import { setResponseHeader } from '@tanstack/react-start/server'
import type { SignedInMember } from '#/features/auth/session'
import { callApi } from './api'
import { callApiWithSession, endSession, renewOnce } from './session-calls'
import { logRefusal } from './log'
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  clearedSessionCookies,
  isSecureRequest,
  readCookie,
} from './session'

/**
 * What the application asks the API about the person using it, and how they
 * stop being that person.
 *
 * Both live on the server for the same reason sign-in does: the tokens are
 * here and must stay here.
 */

/**
 * The `/me` answer, narrowed to what a screen can show.
 *
 * The identifiers and roles the endpoint also returns are deliberately not
 * carried across: nothing in the application decides anything from them yet,
 * and putting them in the document would be handing the browser facts about a
 * session it has no use for.
 */
const meSchema = z.object({
  memberName: z.string(),
  clinicName: z.string(),
  roles: z.array(z.string()),
}) satisfies z.ZodType<SignedInMember>

/**
 * Who is signed in, for the shell to put on screen.
 *
 * Goes through `callApiWithSession`, so an access token that expired while the
 * page was open is renewed here rather than surfacing as a nameless navigation
 * bar. A session that cannot be renewed leaves as a redirect to sign-in.
 */
export async function readSignedInMember(
  request: Request,
): Promise<SignedInMember> {
  const answer = await callApiWithSession({
    path: '/me',
    method: 'GET',
    from: request,
  })

  if (answer.status === 403 || answer.status === 404) {
    // The token is well-formed and still names nobody here: a Membership in
    // another Clinic, or one that has been revoked since the token was issued.
    // That is a session to end, not an error to show.
    endSession(request, `the API knows no such session (${answer.status})`)
  }
  if (!answer.ok) {
    // Anything else is the API having a bad day. Signing someone out over one
    // would turn a blip into a lost session.
    throw new Error(`the API would not describe the session (${answer.status})`)
  }

  const described = meSchema.safeParse(await answer.json())
  if (!described.success) {
    throw new Error('the API described the session in a way we did not expect')
  }
  return described.data
}

/**
 * Ends the session deliberately.
 *
 * The refresh token is revoked at the API first. Clearing the cookies alone
 * would only take the session out of this browser and leave a credential alive
 * for a month wherever else it had reached — which is the whole difference
 * between signing out and closing the tab.
 *
 * The cookies are then emptied whatever the API said. A revocation that fails
 * is worth a line in the log, but refusing to sign someone out because the API
 * would not co-operate leaves them signed in on a machine they are walking away
 * from, which is worse than a token that outlives its usefulness.
 */
export async function handleSignOut(request: Request): Promise<Response> {
  await revokeRefreshToken(request)

  // Set on the response rather than built onto this one, so it lands *after*
  // anything renewal put there: a sign-out that left a renewed session in the
  // browser would be the exact opposite of what was asked for.
  setResponseHeader(
    'set-cookie',
    clearedSessionCookies(isSecureRequest(request)),
  )
  return new Response(null, { status: 204 })
}

/**
 * Revokes the refresh token at the API, renewing first if that is what it
 * takes.
 *
 * The API's logout needs a live access token as well as the refresh token, and
 * the access token is the half that expires in minutes. Someone signing out of
 * a tab left open over lunch has exactly the expired one — which is the case
 * this whole step exists for, since it is the shared computer they are walking
 * away from. So a refusal is met by renewing and asking again, and what is
 * revoked the second time is the token renewal itself issued: rotation means
 * the one read from the cookie has already been spent, and the live credential
 * is the successor.
 */
async function revokeRefreshToken(request: Request): Promise<void> {
  const refreshToken = readCookie(request, REFRESH_COOKIE)
  if (refreshToken === undefined || refreshToken.length === 0) {
    return
  }

  const accessToken = readCookie(request, ACCESS_COOKIE)
  if (accessToken !== undefined && accessToken.length > 0) {
    const answer = await revoke(request, accessToken, refreshToken)
    if (answer === undefined || answer.ok) {
      return
    }
    if (answer.status !== 401) {
      logRefusal(
        'sign-out could not revoke the refresh token',
        `the API answered ${answer.status}; the session is cleared here regardless`,
      )
      return
    }
  }

  try {
    const renewed = await renewOnce(request)
    const answer = await revoke(
      request,
      renewed.accessToken,
      renewed.refreshToken,
    )
    if (answer !== undefined && !answer.ok) {
      logRefusal(
        'sign-out could not revoke the renewed refresh token',
        `the API answered ${answer.status}; the session is cleared here regardless`,
      )
    }
  } catch {
    // Renewal gave up, which means the refresh token is already spent, expired
    // or unknown. There is no live credential left to revoke — and the redirect
    // it threw must not escape a sign-out that is going to succeed anyway.
  }
}

/** One attempt at the API's logout. `undefined` when it could not be reached. */
async function revoke(
  request: Request,
  bearer: string,
  refreshToken: string,
): Promise<Response | undefined> {
  try {
    return await callApi({
      path: '/auth/logout',
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
      from: request,
      bearer,
    })
  } catch {
    // Already logged as an unreachable API by `callApi`. Signing out locally is
    // still the right thing to do.
    return undefined
  }
}
