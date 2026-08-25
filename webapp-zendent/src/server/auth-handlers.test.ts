import { afterAll, beforeAll, beforeEach, expect, test } from 'vitest'
import { createServer } from 'node:http'
import type { IncomingMessage, Server, ServerResponse } from 'node:http'
import { handleSignIn } from './auth-handlers'

/**
 * The API is stood up as a local stub. Its shapes come from the backend's
 * published document, not from imagination: sign-in answers
 * `{accessToken, tokenType, expiresIn, refreshToken}` and refuses with an
 * RFC 7807 problem detail.
 *
 * The stub cannot prove that the frontend and the backend agree — that is this
 * seam's known blind spot, recorded in the spec. It proves what the BFF does.
 */
interface Recorded {
  method: string
  url: string
  host: string | undefined
  body: string
}

let server: Server
let apiPort: number
let received: Array<Recorded>
let respondWith: (body: string) => { status: number; body: string }

function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve) => {
    let raw = ''
    request.on('data', (chunk) => (raw += chunk))
    request.on('end', () => resolve(raw))
  })
}

beforeAll(async () => {
  server = createServer(
    (request: IncomingMessage, response: ServerResponse) => {
      void readBody(request).then((body) => {
        received.push({
          method: request.method ?? '',
          url: request.url ?? '',
          host: request.headers.host,
          body,
        })
        const answer = respondWith(body)
        response.writeHead(answer.status, {
          'content-type': 'application/json',
        })
        response.end(answer.body)
      })
    },
  )

  // Bound on every interface: the BFF reaches the stub through the Clinic
  // hostname it was called on, and `avicena.localhost` resolves to ::1.
  await new Promise<void>((resolve) => server.listen(0, resolve))
  const address = server.address()
  if (address === null || typeof address === 'string') {
    throw new Error('the stub API did not bind to a port')
  }
  apiPort = address.port
  process.env.ZENDENT_API_PORT = String(apiPort)
})

afterAll(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()))
})

beforeEach(() => {
  received = []
  respondWith = () => ({
    status: 200,
    body: JSON.stringify({
      accessToken: 'access-token-value',
      tokenType: 'Bearer',
      expiresIn: 900,
      refreshToken: 'refresh-token-value',
    }),
  })
})

function signInRequest(
  credentials: unknown,
  host = 'avicena.localhost:3000',
): Request {
  return new Request(`http://${host}/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', host },
    body: JSON.stringify(credentials),
  })
}

const validCredentials = {
  email: 'drg.soap@avicena.test',
  password: 'correct-horse-battery-staple',
}

test('valid credentials leave the caller signed in', async () => {
  const response = await handleSignIn(signInRequest(validCredentials))

  expect(response.status).toBe(204)
})

test('the session is stored in cookies the browser cannot read', async () => {
  const response = await handleSignIn(signInRequest(validCredentials))
  const cookies = response.headers.getSetCookie()

  expect(cookies).toHaveLength(2)
  for (const cookie of cookies) {
    expect(cookie).toContain('HttpOnly')
    expect(cookie).toContain('SameSite=Lax')
    expect(cookie).toContain('Path=/')
  }
})

test('no token reaches the browser in a response body', async () => {
  const response = await handleSignIn(signInRequest(validCredentials))
  const body = await response.text()

  expect(body).not.toContain('access-token-value')
  expect(body).not.toContain('refresh-token-value')
})

test('the Clinic travels to the API as the host, never as a body field', async () => {
  await handleSignIn(signInRequest(validCredentials, 'avicena.localhost:3000'))

  expect(received).toHaveLength(1)
  expect(received[0].url).toBe('/auth/login')
  expect(received[0].host).toBe(`avicena.localhost:${apiPort}`)
  expect(JSON.parse(received[0].body)).toEqual(validCredentials)
})

test('a different Clinic subdomain reaches the API as that Clinic', async () => {
  await handleSignIn(signInRequest(validCredentials, 'other.localhost:3000'))

  expect(received[0].host).toBe(`other.localhost:${apiPort}`)
})

test('wrong credentials are refused without saying which half was wrong', async () => {
  respondWith = () => ({
    status: 401,
    body: JSON.stringify({
      type: 'about:blank',
      title: 'Unauthorized',
      status: 401,
      detail: 'Invalid credentials',
    }),
  })

  const response = await handleSignIn(signInRequest(validCredentials))
  const body = (await response.json()) as { message: string }

  expect(response.status).toBe(401)
  expect(response.headers.getSetCookie()).toHaveLength(0)
  expect(body.message).toBe('Invalid credentials')
  expect(body.message).not.toMatch(/password|email|user/i)
})

test('a failure that is not about the credentials does not blame them', async () => {
  respondWith = () => ({
    status: 500,
    body: JSON.stringify({ status: 500, title: 'Internal Server Error' }),
  })

  const response = await handleSignIn(signInRequest(validCredentials))
  const body = (await response.json()) as { message: string }

  expect(response.status).toBe(500)
  // Telling someone their credentials are invalid when the server fell over
  // sends them off to rewrite a password that was never the problem.
  expect(body.message).not.toMatch(/credential/i)
  expect(body.message).toContain('could not sign you in right now')
})

test('an unresolvable Clinic address is passed through as not found', async () => {
  respondWith = () => ({
    status: 404,
    body: JSON.stringify({ status: 404, detail: 'Unknown clinic address' }),
  })

  const response = await handleSignIn(signInRequest(validCredentials))

  expect(response.status).toBe(404)
  expect(response.headers.getSetCookie()).toHaveLength(0)
})

test('a host that names no Clinic says so instead of blaming access', async () => {
  respondWith = () => ({
    status: 403,
    body: JSON.stringify({ status: 403, detail: 'Access denied' }),
  })

  const response = await handleSignIn(signInRequest(validCredentials))
  const body = (await response.json()) as { message: string }

  expect(response.status).toBe(403)
  // "Access denied" is what the API says and it explains nothing: the real
  // problem is the address, and the person needs to be told which one to use.
  expect(body.message).not.toMatch(/access denied/i)
  expect(body.message).toMatch(/address/i)
  expect(response.headers.getSetCookie()).toHaveLength(0)
})

test('a malformed submission never reaches the API', async () => {
  const response = await handleSignIn(signInRequest({ email: 'not-an-email' }))

  expect(response.status).toBe(400)
  expect(received).toHaveLength(0)
})

test('session cookies are not marked secure over plain http', async () => {
  const response = await handleSignIn(signInRequest(validCredentials))

  // Marking them Secure on a developer's http machine would stop the browser
  // storing them at all, which looks exactly like a broken sign-in.
  for (const cookie of response.headers.getSetCookie()) {
    expect(cookie).not.toContain('Secure')
  }
})
