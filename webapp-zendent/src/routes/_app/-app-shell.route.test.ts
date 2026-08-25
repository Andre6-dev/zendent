import { afterAll, beforeAll, expect, test } from 'vitest'
import { readdirSync } from 'node:fs'
import { createServer } from 'vite'
import type { ViteDevServer } from 'vite'
import type { AddressInfo } from 'node:net'
import { ACCESS_COOKIE } from '#/server/session'

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

beforeAll(async () => {
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
