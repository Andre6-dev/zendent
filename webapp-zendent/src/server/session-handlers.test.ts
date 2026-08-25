import { afterAll, beforeAll, beforeEach, expect, test } from 'vitest'
import { requestHandler } from '@tanstack/react-start/server'
import { ACCESS_COOKIE, REFRESH_COOKIE } from './session'
import { handleSignOut, readSignedInMember } from './session-handlers'
import { answeringInOrder, startApiStub } from './testing/api-stub'
import type { Answer, ApiStub, Recorded } from './testing/api-stub'

let api: ApiStub
let received: Array<Recorded>

function apiAnswers(plan: Record<string, Array<Answer>>): void {
  api.answerWith(answeringInOrder(plan))
}

beforeAll(async () => {
  api = await startApiStub()
  received = api.received
})

afterAll(async () => {
  await api.close()
})

beforeEach(() => {
  api.reset()
  apiAnswers({})
})

const signedIn = `${ACCESS_COOKIE}=live-access-token; ${REFRESH_COOKIE}=live-refresh-token`

const RENEWED = {
  accessToken: 'renewed-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'rotated-refresh-token',
}

function request(cookie = signedIn): Request {
  return new Request('http://avicena.localhost:3000/reservations', {
    headers: { cookie },
  })
}

test('the signed-in member is described by name, not by identifier', async () => {
  apiAnswers({
    '/me': [
      {
        status: 200,
        body: JSON.stringify({
          userId: '00000000-0000-0000-0000-000000000001',
          email: 'drg.soap@avicena.test',
          clinicId: '00000000-0000-0000-0000-000000000002',
          memberName: 'Ada Admin',
          clinicName: 'Avicena Clinic',
          roles: ['ADMIN'],
        }),
      },
    ],
  })

  const member = await readSignedInMember(request())

  expect(member).toEqual({
    memberName: 'Ada Admin',
    clinicName: 'Avicena Clinic',
    roles: ['ADMIN'],
  })
  expect(received[0].authorization).toBe('Bearer live-access-token')
})

test('reading the session refuses an answer it does not recognise', async () => {
  apiAnswers({ '/me': [{ status: 200, body: '{"email":"only-this"}' }] })

  await expect(readSignedInMember(request())).rejects.toThrow(/did not expect/)
})

/**
 * Signing out sets its cookies on the response rather than building them onto
 * one, so it is driven inside a request context and asserted on the response
 * the framework would send — which is also the only way to see that a renewal
 * happening underneath cannot leave a live session behind.
 */
async function signOut(cookie = signedIn): Promise<Response> {
  const handle = requestHandler((incoming) => handleSignOut(incoming))
  return handle(
    new Request('http://avicena.localhost:3000/logout', {
      method: 'POST',
      headers: { cookie },
    }),
    undefined,
  )
}

test('signing out revokes the refresh token at the API first', async () => {
  apiAnswers({ '/auth/logout': [{ status: 204, body: '' }] })

  await signOut()

  expect(received.map((call) => call.path)).toEqual(['/auth/logout'])
  // Revoked by presenting it, and authorized by the access token: a cleared
  // cookie alone would leave the refresh token alive wherever it had reached.
  expect(JSON.parse(received[0].body)).toEqual({
    refreshToken: 'live-refresh-token',
  })
  expect(received[0].authorization).toBe('Bearer live-access-token')
})

test('signing out empties the session cookies', async () => {
  apiAnswers({ '/auth/logout': [{ status: 204, body: '' }] })

  const response = await signOut()
  const cookies = response.headers.getSetCookie()

  expect(response.status).toBe(204)
  expect(cookies).toHaveLength(2)
  for (const cookie of cookies) {
    expect(cookie).toContain('Max-Age=0')
    expect(cookie).toContain('HttpOnly')
  }
})

test('an expired access token is renewed so the refresh token is still revoked', async () => {
  // The case this step exists for: someone signing out of a tab left open over
  // lunch. The API's logout needs a live access token, and theirs is the half
  // that expires in minutes.
  apiAnswers({
    '/auth/logout': [
      { status: 401, body: '{}' },
      { status: 204, body: '' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  await signOut()

  expect(received.map((call) => call.path)).toEqual([
    '/auth/logout',
    '/auth/refresh',
    '/auth/logout',
  ])
  // Renewal spends the token it was given, so what is left live — and what
  // therefore has to be revoked — is the successor it issued.
  expect(JSON.parse(received[2].body)).toEqual({
    refreshToken: 'rotated-refresh-token',
  })
  expect(received[2].authorization).toBe('Bearer renewed-access-token')
})

test('a renewal during sign-out does not leave a live session in the browser', async () => {
  apiAnswers({
    '/auth/logout': [
      { status: 401, body: '{}' },
      { status: 204, body: '' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const cookies = (await signOut()).headers.getSetCookie()

  expect(cookies).toHaveLength(2)
  for (const cookie of cookies) {
    expect(cookie).toContain('Max-Age=0')
    expect(cookie).not.toContain('renewed-access-token')
    expect(cookie).not.toContain('rotated-refresh-token')
  }
})

test('a session too far gone to renew is still signed out of here', async () => {
  // Nothing left to revoke: the refresh token is already spent or expired.
  // Refusing to sign someone out over that would leave them signed in on a
  // machine they are walking away from.
  apiAnswers({
    '/auth/logout': [{ status: 401, body: '{}' }],
    '/auth/refresh': [{ status: 401, body: '{}' }],
  })

  const response = await signOut()

  expect(response.status).toBe(204)
  expect(response.headers.getSetCookie()).toHaveLength(2)
})

test('a request with no refresh token is not sent to the API at all', async () => {
  const response = await signOut(`${ACCESS_COOKIE}=live-access-token`)

  expect(received).toHaveLength(0)
  expect(response.headers.getSetCookie()).toHaveLength(2)
})

test('no token reaches the browser when signing out', async () => {
  apiAnswers({ '/auth/logout': [{ status: 204, body: '' }] })

  const response = await signOut()

  expect(await response.text()).toBe('')
  for (const cookie of response.headers.getSetCookie()) {
    expect(cookie).not.toContain('live-refresh-token')
    expect(cookie).not.toContain('live-access-token')
  }
})

test('a session that cannot be renewed leaves reading it as a redirect', async () => {
  apiAnswers({
    '/me': [{ status: 401, body: '{}' }],
    '/auth/refresh': [{ status: 401, body: '{}' }],
  })

  let thrown: unknown
  const handle = requestHandler(async (incoming) => {
    try {
      await readSignedInMember(incoming)
    } catch (caught) {
      thrown = caught
    }
    return new Response(null, { status: 204 })
  })
  await handle(request(), undefined)

  expect(thrown).toBeInstanceOf(Response)
  expect((thrown as Response).headers.get('location')).toBe('/login')
})
