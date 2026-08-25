import { afterAll, beforeAll, beforeEach, expect, test } from 'vitest'
import { readdirSync } from 'node:fs'
import { createServer } from 'vite'
import type { ViteDevServer } from 'vite'
import type { AddressInfo } from 'node:net'
import { ACCESS_COOKIE, REFRESH_COOKIE } from '#/server/session'
import { startApiStub } from '#/server/testing/api-stub'
import type { ApiStub, Recorded } from '#/server/testing/api-stub'

/**
 * The application is stood up for real and answered with over HTTP. Nothing
 * here reaches into the route: the assertions are made on the document the
 * server sends back, which is the only place the distinction this guard exists
 * for can be seen. A guard that ran after paint would still redirect — it
 * would just ship the shell first, and only a response body can tell the two
 * apart.
 *
 * The cookie policy itself — which cookie counts as a session — belongs to
 * `hasSession` and is asserted in `src/server/session.test.ts`.
 */

let server: ViteDevServer
let origin: string
let api: ApiStub
let calls: Array<Recorded>
/** How the stubbed API answers an exchange. Set per test. */
let exchange: { status: number; body: string }

const RENEWED = {
  accessToken: 'renewed-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'rotated-refresh-token',
}

const SIGNED_IN = {
  userId: '00000000-0000-0000-0000-000000000001',
  email: 'ada@avicena.test',
  clinicId: '00000000-0000-0000-0000-000000000002',
  memberName: 'Ada Admin',
  clinicName: 'Avicena Clinic',
  roles: ['ADMIN'],
}

beforeAll(async () => {
  // The API the application renews and signs out against, stood up in this
  // process so the dev server below reaches it through `ZENDENT_API_PORT`.
  api = await startApiStub()
  calls = api.received

  server = await createServer({
    root: process.cwd(),
    server: { port: 0 },
    logLevel: 'error',
  })
  await server.listen()
  const address = server.httpServer?.address() as AddressInfo
  origin = `http://localhost:${address.port}`
}, 120_000)

afterAll(async () => {
  await server.close()
  await api.close()
})

beforeEach(() => {
  api.reset()
  exchange = { status: 200, body: JSON.stringify(RENEWED) }
  api.answerWith((call) =>
    call.path === '/me'
      ? { status: 200, body: JSON.stringify(SIGNED_IN) }
      : exchange,
  )
})

/**
 * Every screen behind the shell, read off the route folder rather than listed
 * by hand: a screen added later is guarded by these tests the day it lands.
 */
const screens = readdirSync(new URL('.', import.meta.url))
  .filter((file) => file.endsWith('.tsx') && file !== 'route.tsx')
  .map((file) => `/${file.slice(0, -'.tsx'.length)}`)

function get(path: string, cookie?: string): Promise<Response> {
  return fetch(`${origin}${path}`, {
    redirect: 'manual',
    headers: cookie === undefined ? undefined : { cookie },
  })
}

test('the route folder holds screens to guard', () => {
  // Without this the sweep below would pass by covering nothing at all.
  expect(screens.length).toBeGreaterThan(0)
  expect(screens).toContain('/reservations')
})

test('no screen behind the shell is served to a request without a session', async () => {
  for (const screen of screens) {
    const response = await get(screen)

    expect({ screen, status: response.status }).toEqual({
      screen,
      status: 307,
    })
    expect(response.headers.get('location')).toBe('/login')
    // The point of the ticket: not merely that they are sent to sign-in, but
    // that the application never reached them on the way.
    expect(await response.text()).toBe('')
  }
}, 60_000)

test('a request carrying a session is answered with the application', async () => {
  const response = await get(
    '/reservations',
    `${ACCESS_COOKIE}=access-token-value`,
  )
  const body = await response.text()

  expect(response.status).toBe(200)
  // The shell, and the screen it wraps: both rendered into the document.
  expect(body).toContain('Customer Support')
  expect(body).toContain('Reservations')
}, 60_000)

test('the sign-in screen stays reachable without a session', async () => {
  const response = await get('/login')

  expect(response.status).toBe(200)
  expect(await response.text()).toContain('Sign in')
}, 60_000)

/**
 * The access cookie expires with the token it carries, so a person coming back
 * after a break arrives holding only the refresh cookie. That is the case the
 * whole renewal path exists for, and it is asserted here on the document the
 * server sends back rather than on the mechanism underneath it.
 */
const lapsed = `${REFRESH_COOKIE}=live-refresh-token`

test('a lapsed access token is renewed at the door and the application still renders', async () => {
  const response = await get('/reservations', lapsed)
  const body = await response.text()

  expect(response.status).toBe(200)
  expect(body).toContain('Customer Support')
})

test('the renewed session is put back in the browser', async () => {
  const response = await get('/reservations', lapsed)
  const cookies = response.headers.getSetCookie()

  expect(cookies).toHaveLength(2)
  expect(cookies[0]).toContain(`${ACCESS_COOKIE}=renewed-access-token`)
  expect(cookies[1]).toContain(`${REFRESH_COOKIE}=rotated-refresh-token`)
  for (const cookie of cookies) {
    expect(cookie).toContain('HttpOnly')
  }
})

test('no token reaches the browser in the renewed document', async () => {
  const body = await (await get('/reservations', lapsed)).text()

  expect(body).not.toContain('renewed-access-token')
  expect(body).not.toContain('rotated-refresh-token')
})

test('a refusal at the exchange empties the session and sends the person to sign-in', async () => {
  exchange = { status: 401, body: '{"detail":"Token already spent"}' }

  const response = await get('/reservations', lapsed)

  expect(response.status).toBe(307)
  expect(response.headers.get('location')).toBe('/login')
  expect(await response.text()).toBe('')
  for (const cookie of response.headers.getSetCookie()) {
    expect(cookie).toContain('Max-Age=0')
  }
})

test('the document names the signed-in member and their Clinic', async () => {
  const body = await (
    await get('/reservations', `${ACCESS_COOKIE}=live-access-token`)
  ).text()

  expect(body).toContain('Ada Admin')
  expect(body).toContain('Avicena Clinic')
  // The placeholders the shell shipped with are gone from the document, not
  // merely covered over by it.
  expect(body).not.toContain('Darrell Steward')
  expect(body).not.toContain('Northside Dental')
})

test('the document tells the browser not to store it', async () => {
  // What stops the back button handing back a Clinic's screens after a
  // sign-out: the entry cannot be served from the browser's own store, so it
  // is asked of the server again and meets the guard.
  const response = await get(
    '/reservations',
    `${ACCESS_COOKIE}=live-access-token`,
  )

  expect(response.headers.get('cache-control')).toContain('no-store')
})

test('signing out revokes the refresh token and empties the session', async () => {
  exchange = { status: 204, body: '' }

  const response = await fetch(`${origin}/logout`, {
    method: 'POST',
    redirect: 'manual',
    headers: {
      cookie: `${ACCESS_COOKIE}=live-access-token; ${REFRESH_COOKIE}=live-refresh-token`,
    },
  })

  expect(calls.map((call) => call.path)).toEqual(['/auth/logout'])
  expect(JSON.parse(calls[0].body)).toEqual({
    refreshToken: 'live-refresh-token',
  })
  for (const cookie of response.headers.getSetCookie()) {
    expect(cookie).toContain('Max-Age=0')
  }
})

test('what the browser holds after signing out will not open a screen again', async () => {
  // The sequence the back button walks: sign out, then ask for the screen the
  // person was on with whatever the browser is left holding. The document is
  // served `no-store`, so it genuinely has to be asked for rather than
  // restored — and this is the answer it gets.
  exchange = { status: 204, body: '' }

  const signedOut = await fetch(`${origin}/logout`, {
    method: 'POST',
    redirect: 'manual',
    headers: {
      cookie: `${ACCESS_COOKIE}=live-access-token; ${REFRESH_COOKIE}=live-refresh-token`,
    },
  })

  // Exactly what a browser would send afterwards: the emptied cookies.
  const emptied = signedOut.headers
    .getSetCookie()
    .map((cookie) => cookie.slice(0, cookie.indexOf(';')))
    .join('; ')
  const back = await get('/reservations', emptied)

  expect(back.status).toBe(307)
  expect(back.headers.get('location')).toBe('/login')
  expect(await back.text()).toBe('')
})

test('a screen is not served again once the session has been signed out of', async () => {
  // The state a browser would be in after signing out: no cookies at all.
  const response = await get('/reservations')

  expect(response.status).toBe(307)
  expect(response.headers.get('location')).toBe('/login')
  expect(await response.text()).toBe('')
})
