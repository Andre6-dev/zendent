import { afterAll, beforeAll, beforeEach, expect, test } from 'vitest'
import { requestHandler } from '@tanstack/react-start/server'
import { ACCESS_COOKIE, REFRESH_COOKIE } from './session'
import { callApiWithSession } from './session-calls'
import { answeringInOrder, startApiStub } from './testing/api-stub'
import type { Answer, ApiStub, Recorded } from './testing/api-stub'

/**
 * What is under test is what the BFF does when a token expires underneath a
 * person: the exchange, the single retry, and what the browser is left holding.
 *
 * The assertions are made on the response the framework would actually send —
 * `requestHandler` is what binds the request and collects the cookies renewal
 * sets from deep inside — never on the internals of the call.
 */

/** Answers the stub gives, per path, in order. The last one repeats. */
type Plan = Record<string, Array<Answer>>

let api: ApiStub
let received: Array<Recorded>

const RENEWED = {
  accessToken: 'renewed-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'rotated-refresh-token',
}

/** Points the stub at a plan and exposes what it goes on to receive. */
function apiAnswers(plan: Plan): void {
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

interface Proxied {
  /** What the caller of the proxied call got back, if it got anything. */
  answer: Response | undefined
  /** The response the framework would send the browser. */
  browser: Response
}

/**
 * Drives one proxied call the way a route would, inside a real request context
 * so the cookies renewal sets are collected onto the outgoing response.
 */
async function proxy(cookie?: string, path = '/me'): Promise<Proxied> {
  let answer: Response | undefined

  const handle = requestHandler(async (request) => {
    try {
      answer = await callApiWithSession({ path, method: 'GET', from: request })
      return new Response(await answer.clone().text(), {
        status: answer.status,
      })
    } catch (thrown) {
      // A session that could not be renewed leaves as a redirect, which is a
      // Response — the same thing the router would send.
      if (thrown instanceof Response) return thrown
      throw thrown
    }
  })

  const browser = await handle(
    new Request(`http://avicena.localhost:3000${path}`, {
      headers: cookie === undefined ? undefined : { cookie },
    }),
    undefined,
  )

  return { answer, browser }
}

/**
 * Drives two proxied calls at once inside a single request, the way two
 * loaders on one server-rendered document would.
 */
async function proxyBoth(
  cookie: string,
  paths: [string, string],
): Promise<{ answers: Array<Response>; browser: Response }> {
  let answers: Array<Response> = []

  const handle = requestHandler(async (request) => {
    answers = await Promise.all(
      paths.map((path) =>
        callApiWithSession({ path, method: 'GET', from: request }),
      ),
    )
    return new Response(null, { status: 204 })
  })

  const browser = await handle(
    new Request(`http://avicena.localhost:3000${paths[0]}`, {
      headers: { cookie },
    }),
    undefined,
  )

  return { answers, browser }
}

const signedIn = `${ACCESS_COOKIE}=live-access-token; ${REFRESH_COOKIE}=live-refresh-token`

test('a live session reaches the API as a bearer token and nothing else happens', async () => {
  apiAnswers({
    '/me': [{ status: 200, body: '{"email":"drg.soap@avicena.test"}' }],
  })

  const { answer } = await proxy(signedIn)

  expect(answer?.status).toBe(200)
  expect(received).toHaveLength(1)
  expect(received[0].authorization).toBe('Bearer live-access-token')
})

test('an expired token is exchanged and the original call retried once', async () => {
  apiAnswers({
    '/me': [
      { status: 401, body: '{"detail":"Token expired"}' },
      { status: 200, body: '{"email":"drg.soap@avicena.test"}' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const { answer } = await proxy(signedIn)

  // What the person's screen sees: the answer it asked for, not the refusal.
  expect(answer?.status).toBe(200)
  expect(await answer?.text()).toBe('{"email":"drg.soap@avicena.test"}')

  expect(received.map((call) => call.path)).toEqual([
    '/me',
    '/auth/refresh',
    '/me',
  ])
  // The refresh token is spent as a body field, and the retry carries the
  // token the exchange just issued rather than the one that expired.
  expect(JSON.parse(received[1].body)).toEqual({
    refreshToken: 'live-refresh-token',
  })
  expect(received[2].authorization).toBe('Bearer renewed-access-token')
})

test('the renewed session replaces the cookies', async () => {
  apiAnswers({
    '/me': [
      { status: 401, body: '{}' },
      { status: 200, body: '{}' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const { browser } = await proxy(signedIn)
  const cookies = browser.headers.getSetCookie()

  expect(cookies).toHaveLength(2)
  expect(cookies[0]).toContain(`${ACCESS_COOKIE}=renewed-access-token`)
  // The API spends the refresh token it is given and answers with its
  // successor, so keeping the old one would leave a spent credential behind.
  expect(cookies[1]).toContain(`${REFRESH_COOKIE}=rotated-refresh-token`)
  for (const cookie of cookies) {
    expect(cookie).toContain('HttpOnly')
    expect(cookie).toContain('SameSite=Lax')
  }
})

test('nothing surfaces to the person when renewal succeeds', async () => {
  apiAnswers({
    '/me': [
      { status: 401, body: '{}' },
      { status: 200, body: '{"email":"drg.soap@avicena.test"}' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const { browser } = await proxy(signedIn)

  expect(browser.status).toBe(200)
  expect(browser.headers.get('location')).toBeNull()
})

test('a failed exchange clears the session and sends the person to sign-in', async () => {
  apiAnswers({
    '/me': [{ status: 401, body: '{}' }],
    '/auth/refresh': [{ status: 401, body: '{"detail":"Token spent"}' }],
  })

  const { browser } = await proxy(signedIn)

  // Named on the response itself, not only in what the router would resolve:
  // a proxied call can be made from a plain server handler, where nothing
  // resolves it.
  expect(browser.status).toBe(307)
  expect(browser.headers.get('location')).toBe('/login')
  const cookies = browser.headers.getSetCookie()
  expect(cookies).toHaveLength(2)
  for (const cookie of cookies) {
    expect(cookie).toContain('Max-Age=0')
  }
})

test('renewal is attempted once and never repeated', async () => {
  // The retried call is refused too. Nothing about that starts renewal again.
  apiAnswers({
    '/me': [{ status: 401, body: '{}' }],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const { browser } = await proxy(signedIn)

  expect(received.map((call) => call.path)).toEqual([
    '/me',
    '/auth/refresh',
    '/me',
  ])
  // A token minted seconds ago and refused is not a session worth keeping. The
  // person is sent to sign-in rather than handed a bare refusal to puzzle over.
  expect(browser.status).toBe(307)
  expect(browser.headers.get('location')).toBe('/login')
  for (const cookie of browser.headers.getSetCookie()) {
    expect(cookie).toContain('Max-Age=0')
  }
})

test('two calls racing on one request spend the refresh token once', async () => {
  // Two loaders on one server-rendered document. The API spends the token it
  // is given, so a second exchange would be refused as already-spent and would
  // throw out a person whose session was perfectly healthy.
  apiAnswers({
    '/me': [
      { status: 401, body: '{}' },
      { status: 200, body: '{"email":"drg.soap@avicena.test"}' },
    ],
    '/members': [
      { status: 401, body: '{}' },
      { status: 200, body: '[]' },
    ],
    // The stub spends the token the way the API does: presenting it twice is
    // refused the second time.
    '/auth/refresh': [
      { status: 200, body: JSON.stringify(RENEWED) },
      { status: 401, body: '{"detail":"Token already spent"}' },
    ],
  })

  const { answers, browser } = await proxyBoth(signedIn, ['/me', '/members'])

  const exchanges = received.filter((call) => call.path === '/auth/refresh')
  expect(exchanges).toHaveLength(1)
  expect(answers.map((answer) => answer.status)).toEqual([200, 200])
  expect(browser.status).toBe(204)
})

test('a racing pair leaves one renewed session in the browser', async () => {
  apiAnswers({
    '/me': [
      { status: 401, body: '{}' },
      { status: 200, body: '{}' },
    ],
    '/members': [
      { status: 401, body: '{}' },
      { status: 200, body: '{}' },
    ],
    '/auth/refresh': [
      { status: 200, body: JSON.stringify(RENEWED) },
      { status: 401, body: '{"detail":"Token already spent"}' },
    ],
  })

  const { browser } = await proxyBoth(signedIn, ['/me', '/members'])
  const cookies = browser.headers.getSetCookie()

  // Not four cookies, and not the spent token: one exchange, one session.
  expect(cookies).toHaveLength(2)
  expect(cookies[1]).toContain(`${REFRESH_COOKIE}=rotated-refresh-token`)
})

test('a request with no refresh cookie is not sent to the exchange at all', async () => {
  apiAnswers({ '/me': [{ status: 401, body: '{}' }] })

  const { browser } = await proxy(`${ACCESS_COOKIE}=live-access-token`)

  expect(received.map((call) => call.path)).toEqual(['/me'])
  expect(browser.status).toBe(307)
  expect(browser.headers.get('location')).toBe('/login')
})

test('no token reaches the browser on either path', async () => {
  apiAnswers({
    '/me': [
      { status: 401, body: '{}' },
      { status: 200, body: '{"email":"drg.soap@avicena.test"}' },
    ],
    '/auth/refresh': [{ status: 200, body: JSON.stringify(RENEWED) }],
  })

  const { browser } = await proxy(signedIn)
  const body = await browser.text()

  expect(body).not.toContain('renewed-access-token')
  expect(body).not.toContain('rotated-refresh-token')
  for (const cookie of browser.headers.getSetCookie()) {
    expect(cookie).toContain('HttpOnly')
  }
})
