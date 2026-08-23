import { z } from 'zod'
import { credentialsSchema } from '#/features/auth/schemas'
import { callApi, problemMessage } from './api'
import { isSecureRequest, sessionCookies } from './session'
import type { ApiSession } from './session'

/**
 * The sign-in handler. Its whole reason for existing on the server is that the
 * tokens the API issues must never reach the browser: they arrive here, go
 * straight into HTTP-only cookies, and the caller gets back a body that names
 * no credential at all.
 */

const apiSessionSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number().int().positive(),
  refreshToken: z.string().min(1),
})

/** Never narrows to which half was wrong. */
const REFUSED = 'Invalid credentials'

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

export async function handleSignIn(request: Request): Promise<Response> {
  const submitted = await request.json().catch(() => null)
  const credentials = credentialsSchema.safeParse(submitted)
  if (!credentials.success) {
    // Refused here, so a malformed submission never reaches the API.
    return json(400, { message: 'Enter your email address and password.' })
  }

  const answer = await callApi({
    path: '/auth/login',
    method: 'POST',
    body: JSON.stringify(credentials.data),
    from: request,
  })

  if (!answer.ok) {
    return json(answer.status, {
      message: await problemMessage(answer, REFUSED),
    })
  }

  const session = apiSessionSchema.safeParse(await answer.json())
  if (!session.success) {
    return json(502, {
      message: 'The server answered in a way we did not expect.',
    })
  }

  return signedIn(session.data, isSecureRequest(request))
}

function signedIn(session: ApiSession, secure: boolean): Response {
  const headers = new Headers()
  for (const cookie of sessionCookies(session, secure)) {
    headers.append('set-cookie', cookie)
  }
  // No content on purpose: there is nothing to tell the browser that it is
  // allowed to know. The cookies are the whole answer.
  return new Response(null, { status: 204, headers })
}
